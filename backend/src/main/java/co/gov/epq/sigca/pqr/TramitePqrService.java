package co.gov.epq.sigca.pqr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.tiempo.Periodos;
import co.gov.epq.sigca.facturacion.LineaFactura;
import co.gov.epq.sigca.pqr.PqrConsultas.LineaInfo;
import co.gov.epq.sigca.seguridad.UsuarioActual;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * Trámite de PQR por el Gestor y el Jefe de PQR (RF-09, RF-24, P-15, RN-06, RN-22, RN-31, RN-33): bandeja,
 * detalle, toma, actuaciones, evidencias, ajuste de la línea reclamada, respuesta, traslado a la SSPD y su decisión,
 * reasignación y reconocimiento del silencio positivo. Un gestor sin PQR_CONSULTAR_TODAS solo ve su municipio
 * (y lo que tenga asignado).
 */
@Service
public class TramitePqrService {

    static final String TODAS = "PQR_CONSULTAR_TODAS";
    private static final Set<String> ORDENABLES = Set.of("fechaLimiteRespuesta", "fechaRegistro", "numeroRadicado", "estado", "tipo");
    private static final int MAX_PAGINA = 100;

    @PersistenceContext
    private EntityManager em;

    private final PqrRepository repo;
    private final PqrAdjuntoRepository adjuntos;
    private final PqrActuacionRepository actuaciones;
    private final PqrHistorialRepository historial;
    private final PqrConsultas consultas;
    private final RadicacionPqrService radicacion;
    private final CalendarioPqr calendario;
    private final Parametros parametros;
    private final Auditoria auditoria;
    private final AvisosPqr avisos;

    public TramitePqrService(PqrRepository repo, PqrAdjuntoRepository adjuntos, PqrActuacionRepository actuaciones,
            PqrHistorialRepository historial, PqrConsultas consultas, RadicacionPqrService radicacion,
            CalendarioPqr calendario, Parametros parametros, Auditoria auditoria, AvisosPqr avisos) {
        this.repo = repo;
        this.adjuntos = adjuntos;
        this.actuaciones = actuaciones;
        this.historial = historial;
        this.consultas = consultas;
        this.radicacion = radicacion;
        this.calendario = calendario;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.avisos = avisos;
    }

    /** Filtros de la bandeja. {@code vencimiento}: POR_VENCER, VENCIDAS o SILENCIO. */
    public record Filtro(EstadoPqr estado, TipoPqr tipo, Long municipioId, String radicado, Long usuarioId, Long gestorId,
            String vencimiento) {
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public Page<PqrVistas.Resumen> bandeja(Filtro f, Pageable pageable) {
        UsuarioActual u = UsuarioActual.funcionario();
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Map<String, Object> params = new HashMap<>();
        if (!u.tienePermiso(TODAS)) {
            where.append(u.municipioId() == null ? " and p.gestorId = :yo" : " and (p.municipioId = :mio or p.gestorId = :yo)");
            params.put("yo", u.funcionarioId());
            if (u.municipioId() != null) {
                params.put("mio", u.municipioId());
            }
        }
        condicion(where, params, f.estado(), "p.estado = :estado", "estado");
        condicion(where, params, f.tipo(), "p.tipo = :tipo", "tipo");
        condicion(where, params, f.municipioId(), "p.municipioId = :municipio", "municipio");
        condicion(where, params, f.usuarioId(), "p.usuarioId = :usuario", "usuario");
        condicion(where, params, f.gestorId(), "p.gestorId = :gestor", "gestor");
        if (f.radicado() != null && !f.radicado().isBlank()) {
            where.append(" and p.numeroRadicado like :radicado");
            params.put("radicado", f.radicado().strip() + "%");
        }
        LocalDate hoy = Periodos.hoy();
        if ("POR_VENCER".equals(f.vencimiento())) {
            where.append(" and p.estado in :enPlazo and p.fechaLimiteRespuesta <= :hasta and p.fechaLimiteRespuesta >= :hoy");
            params.put("enPlazo", List.of(EstadoPqr.RECIBIDA, EstadoPqr.EN_TRAMITE));
            params.put("hasta", hoy.plusDays(Math.max(1, (parametros.entero(Parametros.PQR_HORAS_ALERTA) + 23) / 24)));
            params.put("hoy", hoy);
        } else if ("VENCIDAS".equals(f.vencimiento())) {
            where.append(" and (p.estado = co.gov.epq.sigca.pqr.EstadoPqr.VENCIDA"
                    + " or (p.estado in :enPlazo and p.fechaLimiteRespuesta < :hoy))");
            params.put("enPlazo", List.of(EstadoPqr.RECIBIDA, EstadoPqr.EN_TRAMITE));
            params.put("hoy", hoy);
        } else if ("SILENCIO".equals(f.vencimiento())) {
            where.append(" and p.silencioPositivo = true and p.fechaReconocimientoSilencio is null");
        }
        String desde = " from Pqr p join Municipio m on m.id = p.municipioId join Funcionario g on g.id = p.gestorId"
                + " left join ContratoServicio c on c.id = p.contratoId";
        TypedQuery<Object[]> q = em.createQuery("""
                select p.id, p.numeroRadicado, p.tipo, p.canal, p.estado, p.municipioId, m.nombre, c.numeroContrato,
                       p.fechaRegistro, p.fechaLimiteRespuesta, p.gestorId, g.nombreCompleto, p.silencioPositivo,
                       p.alerta48hEnviada""" + desde + where + orden(pageable), Object[].class);
        TypedQuery<Long> total = em.createQuery("select count(p)" + desde + where, Long.class);
        params.forEach((k, v) -> {
            q.setParameter(k, v);
            total.setParameter(k, v);
        });
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGINA);
        q.setFirstResult(pageable.getPageNumber() * size).setMaxResults(size);
        Set<LocalDate> festivos = calendario.festivosEntre(hoy.minusDays(90), hoy.plusDays(60));
        List<PqrVistas.Resumen> filas = q.getResultList().stream().map(r -> new PqrVistas.Resumen((Long) r[0],
                (String) r[1], (TipoPqr) r[2], (CanalPqr) r[3], (EstadoPqr) r[4], (Long) r[5], (String) r[6],
                (String) r[7], (LocalDateTime) r[8], (LocalDate) r[9], (Long) r[10], (String) r[11],
                Boolean.TRUE.equals(r[12]), Boolean.TRUE.equals(r[13]),
                abierta((EstadoPqr) r[4]) ? CalendarioPqr.diasHabiles(hoy, (LocalDate) r[9], festivos) : null)).toList();
        return new PageImpl<>(filas, PageRequest.of(pageable.getPageNumber(), size), total.getSingleResult());
    }

    private static void condicion(StringBuilder where, Map<String, Object> params, Object valor, String sql, String nombre) {
        if (valor != null) {
            where.append(" and ").append(sql);
            params.put(nombre, valor);
        }
    }

    private static String orden(Pageable pageable) {
        Sort.Order o = pageable.getSort().stream().filter(s -> ORDENABLES.contains(s.getProperty())).findFirst()
                .orElse(Sort.Order.asc("fechaLimiteRespuesta"));
        return " order by p." + o.getProperty() + (o.isAscending() ? " asc" : " desc") + ", p.id";
    }

    @Transactional(readOnly = true)
    public PqrVistas.Detalle detalle(Long id) {
        Pqr p = cargar(id);
        verificarAcceso(p);
        return detalle(p);
    }

    @Transactional(readOnly = true)
    public PqrAdjunto adjunto(Long pqrId, Long adjuntoId) {
        verificarAcceso(cargar(pqrId));
        PqrAdjunto a = adjuntos.findByIdAndPqrId(adjuntoId, pqrId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Adjunto", adjuntoId));
        a.getContenido();
        return a;
    }

    /** Responsables a los que el Jefe puede reasignar, con su carga actual. */
    @Transactional(readOnly = true)
    public List<PqrVistas.Gestor> gestores(Long municipioId) {
        List<PqrVistas.Gestor> lista = new ArrayList<>();
        for (String rol : List.of("GESTOR_PQR", "JEFE_PQR")) {
            for (PqrConsultas.Carga c : consultas.cargas(rol, "GESTOR_PQR".equals(rol) ? municipioId : null)) {
                lista.add(new PqrVistas.Gestor(c.id(), c.nombre(), rol,
                        c.municipioId() == null ? null : consultas.nombreMunicipio(c.municipioId()), c.abiertas()));
            }
        }
        return lista;
    }

    // ------------------------------------------------------------------ trámite

    /** RECIBIDA → EN_TRAMITE (SWR-18). */
    @Transactional
    public PqrVistas.Detalle tomar(Long id) {
        Pqr p = cargar(id);
        verificarAcceso(p);
        if (p.getEstado() != EstadoPqr.RECIBIDA) {
            throw new ConflictoException("TRANSICION_NO_PERMITIDA", "Solo se inicia el trámite de una PQR RECIBIDA");
        }
        p.setEstado(EstadoPqr.EN_TRAMITE);
        repo.saveAndFlush(p);
        auditoria.exito(OperacionAuditoria.TOMAR_PQR, RadicacionPqrService.ENTIDAD, p.getId(), Map.of("radicado", p.getNumeroRadicado()));
        return detalle(p);
    }

    @Transactional
    public PqrVistas.Detalle registrarActuacion(Long id, PqrSolicitudes.Actuacion a) {
        Pqr p = cargar(id);
        verificarAcceso(p);
        exigirEstado(p, "documentar actuaciones", EstadoPqr.EN_TRAMITE, EstadoPqr.VENCIDA, EstadoPqr.TRASLADADA_SSPD);
        nuevaActuacion(p.getId(), a.descripcion());
        return detalle(p);
    }

    /** Evidencias de la respuesta (RN-06, SWR-20): PDF, JPG o PNG verificados por su contenido. */
    @Transactional
    public PqrVistas.Detalle adjuntarEvidencias(Long id, List<ValidadorAdjuntos.Entrada> entradas) {
        Pqr p = cargar(id);
        verificarAcceso(p);
        exigirEstado(p, "adjuntar evidencias", EstadoPqr.EN_TRAMITE, EstadoPqr.VENCIDA, EstadoPqr.TRASLADADA_SSPD);
        if (entradas.isEmpty()) {
            throw new NegocioException("ADJUNTO_INVALIDO", "Seleccione al menos un archivo");
        }
        List<ValidadorAdjuntos.Archivo> archivos = ValidadorAdjuntos.validar(entradas, radicacion.maxBytes());
        radicacion.guardarAdjuntos(p.getId(), archivos, TipoAdjunto.EVIDENCIA, UsuarioActual.funcionario().funcionarioId());
        return detalle(p);
    }

    /**
     * RN-33: valor reconocido al reclamante sobre la línea mientras sigue en reclamo (antes de responder). Si cubre
     * todo el valor de la línea, la BD la deja EXONERADA.
     */
    @Transactional
    public PqrVistas.Detalle ajustarLinea(Long id, PqrSolicitudes.AjusteLinea a) {
        Pqr p = cargar(id);
        verificarAcceso(p);
        if (p.getLineaFacturaId() == null) {
            throw new NegocioException("SIN_LINEA", "La PQR no reclama una línea de factura");
        }
        exigirEstado(p, "ajustar la línea reclamada", EstadoPqr.EN_TRAMITE, EstadoPqr.VENCIDA, EstadoPqr.TRASLADADA_SSPD);
        LineaFactura l = em.find(LineaFactura.class, p.getLineaFacturaId());
        if (!Boolean.TRUE.equals(l.getEnReclamo())) {
            throw new ConflictoException("VALOR_EN_RECLAMO", "La línea ya no está en reclamo y no admite ajustes");
        }
        if (a.valorAjuste().negate().compareTo(l.getTotalLinea()) > 0) {
            throw new NegocioException("AJUSTE_INVALIDO", "El ajuste no puede superar el valor de la línea ("
                    + l.getTotalLinea().toPlainString() + ")");
        }
        BigDecimal anterior = l.getValorAjuste();
        l.setValorAjuste(a.valorAjuste().setScale(2));
        em.flush();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("radicado", p.getNumeroRadicado());
        d.put("ajusteAnterior", anterior);
        d.put("ajusteNuevo", l.getValorAjuste());
        d.put("estadoLinea", l.getEstado().name());
        auditoria.exito(OperacionAuditoria.AJUSTAR_LINEA, "LINEA_FACTURA", l.getId(), d);
        return detalle(p);
    }

    /** EN_TRAMITE o VENCIDA → RESPONDIDA, con evidencia (RN-06) y, en un recurso, la decisión (P-15). */
    @Transactional
    public PqrVistas.Detalle responder(Long id, PqrSolicitudes.Respuesta r) {
        Pqr p = cargar(id);
        verificarAcceso(p);
        exigirEstado(p, "responder", EstadoPqr.EN_TRAMITE, EstadoPqr.VENCIDA);
        if (p.getTipo() == TipoPqr.RECURSO) {
            if (r.decisionRecurso() == null) {
                throw new NegocioException("DECISION_REQUERIDA", "Indique si la reposición confirma, revoca o modifica la decisión");
            }
            if (Boolean.TRUE.equals(p.getSolicitaApelacion()) && r.decisionRecurso() == DecisionRecurso.CONFIRMA) {
                throw new ConflictoException("REQUIERE_TRASLADO",
                        "Se pidió apelación y la decisión se confirma: el recurso se traslada a la Superservicios");
            }
        } else if (r.decisionRecurso() != null) {
            throw new NegocioException("DECISION_NO_APLICA", "Solo un recurso lleva decisión de reposición");
        }
        exigirEvidencia(p);
        boolean vencida = p.getEstado() == EstadoPqr.VENCIDA;
        p.setRespuesta(r.respuesta().strip());
        p.setDecisionRecurso(r.decisionRecurso());
        p.setRespondidaPor(UsuarioActual.funcionario().funcionarioId());
        p.setEstado(EstadoPqr.RESPONDIDA);
        repo.saveAndFlush(p);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("radicado", p.getNumeroRadicado());
        d.put("extemporanea", vencida);
        if (p.getDecisionRecurso() != null) {
            d.put("decision", p.getDecisionRecurso().name());
        }
        auditoria.exito(OperacionAuditoria.RESPONDER_PQR, RadicacionPqrService.ENTIDAD, p.getId(), d);
        avisos.respondida(p);
        return detalle(p);
    }

    /** Recurso con apelación cuya reposición se confirma: respuesta y traslado a la SSPD (TRASLADADA_SSPD). */
    @Transactional
    public PqrVistas.Detalle trasladarSspd(Long id, PqrSolicitudes.Traslado t) {
        Pqr p = cargar(id);
        verificarAcceso(p);
        if (p.getTipo() != TipoPqr.RECURSO || !Boolean.TRUE.equals(p.getSolicitaApelacion())) {
            throw new ConflictoException("TRANSICION_NO_PERMITIDA", "Solo un recurso con apelación se traslada a la SSPD");
        }
        exigirEstado(p, "trasladar a la SSPD", EstadoPqr.EN_TRAMITE, EstadoPqr.VENCIDA);
        exigirEvidencia(p);
        p.setRespuesta(t.respuesta().strip());
        p.setDecisionRecurso(DecisionRecurso.CONFIRMA);
        p.setRespondidaPor(UsuarioActual.funcionario().funcionarioId());
        p.setEstado(EstadoPqr.TRASLADADA_SSPD);
        repo.saveAndFlush(p);
        auditoria.exito(OperacionAuditoria.TRASLADAR_SSPD, RadicacionPqrService.ENTIDAD, p.getId(),
                Map.of("radicado", p.getNumeroRadicado()));
        avisos.trasladada(p);
        return detalle(p);
    }

    /** TRASLADADA_SSPD → RESPONDIDA con la decisión de la Superintendencia. */
    @Transactional
    public PqrVistas.Detalle registrarDecisionSspd(Long id, PqrSolicitudes.DecisionSspd d) {
        Pqr p = cargar(id);
        verificarAcceso(p);
        exigirEstado(p, "registrar la decisión de la SSPD", EstadoPqr.TRASLADADA_SSPD);
        if (d.observacion() != null && !d.observacion().isBlank()) {
            nuevaActuacion(p.getId(), "Decisión de la SSPD (" + d.decisionSspd() + "): " + d.observacion().strip());
        }
        p.setDecisionSspd(d.decisionSspd());
        p.setEstado(EstadoPqr.RESPONDIDA);
        repo.saveAndFlush(p);
        auditoria.exito(OperacionAuditoria.REGISTRAR_DECISION_SSPD, RadicacionPqrService.ENTIDAD, p.getId(),
                Map.of("radicado", p.getNumeroRadicado(), "decision", d.decisionSspd().name()));
        avisos.decisionSspd(p);
        return detalle(p);
    }

    /** P-15: el Jefe reasigna en RECIBIDA o EN_TRAMITE a un gestor del municipio o a un Jefe de PQR. */
    @Transactional
    public PqrVistas.Detalle reasignar(Long id, PqrSolicitudes.Reasignacion r) {
        Pqr p = cargar(id);
        verificarAcceso(p);
        exigirEstado(p, "reasignar", EstadoPqr.RECIBIDA, EstadoPqr.EN_TRAMITE);
        if (r.gestorId().equals(p.getGestorId())) {
            throw new NegocioException("MISMO_RESPONSABLE", "La PQR ya está asignada a ese funcionario");
        }
        PqrConsultas.FuncionarioInfo nuevo = consultas.funcionario(r.gestorId()).filter(PqrConsultas.FuncionarioInfo::activo)
                .orElseThrow(() -> new NegocioException("FUNCIONARIO_SIN_ROL", "El funcionario no existe o está inactivo"));
        boolean gestorDelMunicipio = consultas.tieneRol(nuevo.id(), "GESTOR_PQR")
                && Objects.equals(nuevo.municipioId(), p.getMunicipioId());
        if (!gestorDelMunicipio && !consultas.tieneRol(nuevo.id(), "JEFE_PQR")) {
            throw new NegocioException("FUNCIONARIO_SIN_ROL",
                    "El responsable debe ser Gestor de PQR del municipio de la solicitud o Jefe de PQR");
        }
        Long anterior = p.getGestorId();
        p.setGestorId(nuevo.id());
        repo.saveAndFlush(p);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("radicado", p.getNumeroRadicado());
        d.put("gestorAnterior", anterior);
        d.put("gestorNuevo", nuevo.id());
        d.put("motivo", r.motivo().strip());
        auditoria.exito(OperacionAuditoria.REASIGNAR_PQR, RadicacionPqrService.ENTIDAD, p.getId(), d);
        avisos.asignada(p);
        return detalle(p);
    }

    /** RN-31: registro único del reconocimiento de los efectos del silencio administrativo positivo. */
    @Transactional
    public PqrVistas.Detalle reconocerSilencio(Long id, PqrSolicitudes.Reconocimiento r) {
        Pqr p = cargar(id);
        verificarAcceso(p);
        if (!Boolean.TRUE.equals(p.getSilencioPositivo())) {
            throw new ConflictoException("SIN_SILENCIO", "La PQR no tiene silencio administrativo positivo");
        }
        if (p.getFechaReconocimientoSilencio() != null) {
            throw new ConflictoException("REGISTRO_INMUTABLE", "El reconocimiento del silencio positivo ya está registrado");
        }
        boolean conObservacion = r != null && r.observacion() != null && !r.observacion().isBlank();
        if (conObservacion && p.getEstado() != EstadoPqr.RESPONDIDA && p.getEstado() != EstadoPqr.RECIBIDA) {
            nuevaActuacion(p.getId(), "Reconocimiento del silencio positivo: " + r.observacion().strip());
        }
        p.setFechaReconocimientoSilencio(LocalDateTime.now());
        repo.saveAndFlush(p);
        em.refresh(p);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("radicado", p.getNumeroRadicado());
        d.put("dentroDelPlazo", !p.getFechaReconocimientoSilencio().isAfter(p.getFechaLimiteReconocimiento()));
        auditoria.exito(OperacionAuditoria.RECONOCER_SILENCIO, RadicacionPqrService.ENTIDAD, p.getId(), d);
        return detalle(p);
    }

    // ------------------------------------------------------------------ apoyo

    private Pqr cargar(Long id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("PQR", id));
    }

    /** El gestor sin PQR_CONSULTAR_TODAS solo accede a su municipio o a lo que tiene asignado. */
    private static void verificarAcceso(Pqr p) {
        UsuarioActual u = UsuarioActual.funcionario();
        if (u.tienePermiso(TODAS) || Objects.equals(p.getGestorId(), u.funcionarioId())
                || (u.municipioId() != null && u.municipioId().equals(p.getMunicipioId()))) {
            return;
        }
        throw new NegocioException("ACCESO_DENEGADO", "La PQR pertenece a otro municipio", HttpStatus.FORBIDDEN);
    }

    private static void exigirEstado(Pqr p, String accion, EstadoPqr... estados) {
        for (EstadoPqr e : estados) {
            if (p.getEstado() == e) {
                return;
            }
        }
        throw new ConflictoException("TRANSICION_NO_PERMITIDA", "No se puede " + accion + " de una PQR " + p.getEstado());
    }

    private void exigirEvidencia(Pqr p) {
        if (adjuntos.countByPqrIdAndTipo(p.getId(), TipoAdjunto.EVIDENCIA) == 0) {
            throw new NegocioException("SIN_EVIDENCIA", "La respuesta requiere al menos una evidencia adjunta (RN-06)");
        }
    }

    private void nuevaActuacion(Long pqrId, String descripcion) {
        PqrActuacion a = new PqrActuacion();
        a.setPqrId(pqrId);
        a.setFuncionarioId(UsuarioActual.funcionario().funcionarioId());
        a.setDescripcion(descripcion.strip().length() > 2000 ? descripcion.strip().substring(0, 2000) : descripcion.strip());
        actuaciones.saveAndFlush(a);
    }

    private static boolean abierta(EstadoPqr e) {
        return e == EstadoPqr.RECIBIDA || e == EstadoPqr.EN_TRAMITE || e == EstadoPqr.VENCIDA;
    }

    static PqrVistas.Adjunto adjunto(PqrAdjuntoMetadatos a) {
        return new PqrVistas.Adjunto(a.id(), a.tipo(), a.nombreArchivo(), a.tipoMime(), a.tamanoBytes(), a.cargadoEn());
    }

    private PqrVistas.Detalle detalle(Pqr p) {
        List<PqrHistorial> hist = historial.findByPqrIdOrderByFechaAscIdAsc(p.getId());
        List<PqrActuacion> acts = actuaciones.findByPqrIdOrderByFechaAscIdAsc(p.getId());
        Set<Long> ids = new HashSet<>();
        ids.add(p.getGestorId());
        if (p.getRespondidaPor() != null) {
            ids.add(p.getRespondidaPor());
        }
        if (p.getRegistradaPor() != null) {
            ids.add(p.getRegistradaPor());
        }
        hist.forEach(h -> {
            if (h.getGestorAnterior() != null) {
                ids.add(h.getGestorAnterior());
            }
            if (h.getGestorNuevo() != null) {
                ids.add(h.getGestorNuevo());
            }
        });
        acts.forEach(a -> ids.add(a.getFuncionarioId()));
        Map<Long, String> nombres = consultas.nombresFuncionarios(ids);

        PqrConsultas.ContratoInfo c = p.getContratoId() == null ? null : consultas.contrato(p.getContratoId()).orElse(null);
        LineaInfo l = p.getLineaFacturaId() == null ? null : consultas.linea(p.getLineaFacturaId()).orElse(null);
        Pqr origen = p.getPqrOrigenId() == null ? null : repo.findById(p.getPqrOrigenId()).orElse(null);
        Pqr recurso = repo.findByPqrOrigenId(p.getId()).orElse(null);
        LocalDate hoy = Periodos.hoy();
        Integer dias = abierta(p.getEstado())
                ? CalendarioPqr.diasHabiles(hoy, p.getFechaLimiteRespuesta(),
                        calendario.festivosEntre(hoy.minusDays(90), hoy.plusDays(60)))
                : null;
        return new PqrVistas.Detalle(p.getId(), p.getNumeroRadicado(), p.getTipo(), p.getCanal(), p.getEstado(),
                p.getMunicipioId(), consultas.nombreMunicipio(p.getMunicipioId()), c == null ? null : c.numero(),
                c == null ? null : c.titular(),
                p.getSolicitanteTipoIdentificacion() == null ? null
                        : p.getSolicitanteTipoIdentificacion() + " " + p.getSolicitanteIdentificacionMascara(),
                p.getFechaRegistro(), p.getFechaLimiteRespuesta(), p.getGestorId(), nombres.get(p.getGestorId()), dias,
                p.getDescripcion(), p.getNombreContacto(), p.getCorreoContacto(), p.getTelefonoContacto(),
                p.getRegistradaPor() == null ? null : nombres.get(p.getRegistradaPor()), p.getRespuesta(),
                p.getFechaRespuesta(), p.getRespondidaPor() == null ? null : nombres.get(p.getRespondidaPor()),
                Boolean.TRUE.equals(p.getRespuestaExtemporanea()), p.getDecisionRecurso(),
                Boolean.TRUE.equals(p.getSolicitaApelacion()), referencia(origen), referencia(recurso),
                l == null ? null : new PqrVistas.Linea(l.id(), l.numeroFactura(), l.periodo(), l.tipoServicio(),
                        l.totalLinea(), l.valorAjuste(), l.estado(), l.enReclamo()),
                p.getFechaTrasladoSspd(), p.getDecisionSspd(), p.getFechaDecisionSspd(),
                Boolean.TRUE.equals(p.getSilencioPositivo()), p.getFechaLimiteReconocimiento(),
                p.getFechaReconocimientoSilencio(), Boolean.TRUE.equals(p.getAlerta48hEnviada()),
                hist.stream().map(h -> new PqrVistas.Historial(h.getFecha(), h.getEstadoAnterior(), h.getEstadoNuevo(),
                        h.getGestorAnterior() == null ? null : nombres.get(h.getGestorAnterior()),
                        h.getGestorNuevo() == null ? null : nombres.get(h.getGestorNuevo()), h.getRealizadoPor())).toList(),
                acts.stream().map(a -> new PqrVistas.ActuacionVista(a.getFecha(), nombres.get(a.getFuncionarioId()),
                        a.getDescripcion())).toList(),
                adjuntos.metadatos(p.getId()).stream().map(TramitePqrService::adjunto).toList());
    }

    private static PqrVistas.Referencia referencia(Pqr p) {
        return p == null ? null : new PqrVistas.Referencia(p.getId(), p.getNumeroRadicado(), p.getEstado());
    }
}
