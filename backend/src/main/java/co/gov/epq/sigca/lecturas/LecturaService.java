package co.gov.epq.sigca.lecturas;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.tiempo.Periodos;
import co.gov.epq.sigca.contratos.AvisosInternos;
import co.gov.epq.sigca.contratos.ContratoServicio;
import co.gov.epq.sigca.contratos.ContratoServicioRepository;
import co.gov.epq.sigca.contratos.EstadoContrato;
import co.gov.epq.sigca.contratos.EstadoMedidor;
import co.gov.epq.sigca.contratos.Medidor;
import co.gov.epq.sigca.contratos.MedidorRepository;
import co.gov.epq.sigca.lecturas.LecturaDtos.Decision;
import co.gov.epq.sigca.lecturas.LecturaDtos.LecturaRegistrar;
import co.gov.epq.sigca.lecturas.LecturaDtos.LecturaVista;
import co.gov.epq.sigca.lecturas.LecturaDtos.Revision;
import co.gov.epq.sigca.notificaciones.EventoNotificacion;
import co.gov.epq.sigca.seguridad.UsuarioActual;
import co.gov.epq.sigca.usuarios.CatalogoTerritorial;
import co.gov.epq.sigca.usuarios.Predio;
import co.gov.epq.sigca.usuarios.PredioRepository;
import co.gov.epq.sigca.usuarios.Textos;
import co.gov.epq.sigca.usuarios.Usuario;
import co.gov.epq.sigca.usuarios.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Subquery;

/**
 * Lecturas y crítica (P-07, RN-35). El backend valida y registra; la BD calcula la lectura anterior, el consumo,
 * el promedio de los últimos periodos y decide el estado (CONFIRMADA, EN_REVISION o ESTIMADA). Cada lectura se
 * relee después de insertarla para devolver lo que decidió la BD.
 */
@Service
public class LecturaService {

    static final String ENTIDAD = "LECTURA";
    private static final List<EstadoContrato> VIGENTES = List.of(EstadoContrato.ACTIVO, EstadoContrato.SUSPENDIDO);

    private final LecturaRepository lecturas;
    private final MedidorRepository medidores;
    private final ContratoServicioRepository contratos;
    private final PredioRepository predios;
    private final UsuarioRepository usuarios;
    private final CatalogoTerritorial catalogo;
    private final AvisosInternos avisos;
    private final Auditoria auditoria;
    private final EntityManager em;

    public LecturaService(LecturaRepository lecturas, MedidorRepository medidores, ContratoServicioRepository contratos,
            PredioRepository predios, UsuarioRepository usuarios, CatalogoTerritorial catalogo, AvisosInternos avisos,
            Auditoria auditoria, EntityManager em) {
        this.lecturas = lecturas;
        this.medidores = medidores;
        this.contratos = contratos;
        this.predios = predios;
        this.usuarios = usuarios;
        this.catalogo = catalogo;
        this.avisos = avisos;
        this.auditoria = auditoria;
        this.em = em;
    }

    /** Registro manual (LECTURA_REGISTRAR). */
    @Transactional
    public LecturaVista registrar(LecturaRegistrar req) {
        Lectura l = insertar(req, OrigenLectura.MANUAL);
        auditoria.exito(OperacionAuditoria.REGISTRAR_LECTURA, ENTIDAD, l.getId(), Map.of("medidorId", l.getMedidorId(),
                "periodo", l.getPeriodo(), "estado", l.getEstado(), "origen", l.getOrigen()));
        return vistas(List.of(l)).getFirst();
    }

    /** Una fila del archivo CSV: cada una en su propia transacción (ver {@link CargaLecturasService}). */
    @Transactional
    public Lectura registrarFilaArchivo(LecturaRegistrar req) {
        return insertar(req, OrigenLectura.ARCHIVO);
    }

    /** P-07: el Asesor confirma la lectura o la rechaza para facturar por promedio. */
    @Transactional
    public LecturaVista revisar(Long id, Revision req) {
        UsuarioActual actual = UsuarioActual.funcionario();
        if (!actual.tieneRol("ASESOR")) {
            throw new NegocioException("FUNCIONARIO_SIN_ROL", "La revisión de lecturas la hace un Asesor", HttpStatus.FORBIDDEN);
        }
        String observacion = Textos.opcional(req.observacion());
        if (req.decision() == Decision.ESTIMAR && observacion == null) {
            throw new NegocioException("VALIDACION", "Explique en la observación por qué se rechaza la lectura", HttpStatus.BAD_REQUEST);
        }
        Lectura l = lecturas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Lectura", id));
        if (l.getEstado() != EstadoLectura.EN_REVISION) {
            throw new ConflictoException("TRANSICION_NO_PERMITIDA", "La lectura no está en revisión (estado " + l.getEstado() + ")");
        }
        l.setEstado(req.decision() == Decision.CONFIRMAR ? EstadoLectura.CONFIRMADA : EstadoLectura.ESTIMADA);
        l.setRevisadaPor(actual.funcionarioId());
        if (observacion != null) {
            l.setObservacion(observacion);
        }
        lecturas.saveAndFlush(l);
        em.refresh(l);
        auditoria.exito(req.decision() == Decision.CONFIRMAR ? OperacionAuditoria.REVISAR_LECTURA : OperacionAuditoria.ESTIMAR_LECTURA,
                ENTIDAD, id, Map.of("medidorId", l.getMedidorId(), "periodo", l.getPeriodo(), "estado", l.getEstado(),
                        "consumo", l.getConsumo()));
        return vistas(List.of(l)).getFirst();
    }

    /** Bandeja de crítica: lecturas EN_REVISION, las más antiguas primero. */
    @Transactional(readOnly = true)
    public Page<LecturaVista> enRevision(Long municipioId, Pageable pageable) {
        Specification<Lectura> spec = (r, q, cb) -> cb.equal(r.get("estado"), EstadoLectura.EN_REVISION);
        if (municipioId != null) {
            spec = spec.and((r, q, cb) -> {
                Subquery<Long> sm = q.subquery(Long.class);
                var m = sm.from(Medidor.class);
                Subquery<Long> sp = q.subquery(Long.class);
                var p = sp.from(Predio.class);
                sp.select(p.get("id")).where(cb.equal(p.get("municipioId"), municipioId));
                sm.select(m.get("id")).where(m.get("predioId").in(sp));
                return r.get("medidorId").in(sm);
            });
        }
        Pageable orden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("creadoEn", "id"));
        Page<Lectura> pagina = lecturas.findAll(spec, orden);
        return new PageImpl<>(vistas(pagina.getContent()), orden, pagina.getTotalElements());
    }

    /** Historial de un medidor, del periodo más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public Page<LecturaVista> historial(Long medidorId, String serial, Pageable pageable) {
        Long id = medidorId;
        if (id == null) {
            if (Textos.vacio(serial)) {
                throw new NegocioException("VALIDACION", "Indique el medidor o su serial", HttpStatus.BAD_REQUEST);
            }
            id = medidores.findBySerial(serial.trim().toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> new RecursoNoEncontradoException("Medidor", serial)).getId();
        }
        Page<Lectura> pagina = lecturas.findByMedidorIdOrderByPeriodoDesc(id, pageable);
        return new PageImpl<>(vistas(pagina.getContent()), pageable, pagina.getTotalElements());
    }

    @Transactional(readOnly = true)
    public LecturaVista consultar(Long id) {
        return vistas(List.of(lecturas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Lectura", id)))).getFirst();
    }

    private Lectura insertar(LecturaRegistrar req, OrigenLectura origen) {
        String serial = req.serial().trim().toUpperCase(Locale.ROOT);
        Medidor m = medidores.findBySerial(serial)
                .orElseThrow(() -> new NegocioException("MEDIDOR_INEXISTENTE", "No existe un medidor con serial " + serial,
                        HttpStatus.UNPROCESSABLE_CONTENT));
        validarContraMedidor(m, req);

        Lectura l = new Lectura();
        l.setMedidorId(m.getId());
        l.setPeriodo(req.periodo());
        l.setFechaLectura(req.fechaLectura());
        l.setLecturaActual(req.lecturaActual());
        l.setCausaNoLectura(req.lecturaActual() == null ? req.causaNoLectura() : null);
        l.setObservacion(Textos.opcional(req.observacion()));
        l.setOrigen(origen);
        l.setRegistradaPor(UsuarioActual.funcionario().funcionarioId());
        // El trigger compuesto aplica la crítica al terminar la sentencia y cambia ESTADO y CONSUMO: se relee
        lecturas.saveAndFlush(l);
        em.refresh(l);
        if (l.getEstado() == EstadoLectura.EN_REVISION) {
            avisarRevision(l, m);
        }
        return l;
    }

    /** Las mismas reglas de TRG_LECTURA_CIUD, con mensajes claros; la BD sigue siendo la última barrera. */
    private static void validarContraMedidor(Medidor m, LecturaRegistrar req) {
        if (m.getEstado() != EstadoMedidor.ACTIVO) {
            throw new NegocioException("LECTURA_INVALIDA", "No se registran lecturas a un medidor retirado");
        }
        if (Boolean.TRUE.equals(m.getLecturaEnRevision())) {
            throw new NegocioException("LECTURA_INVALIDA", "El medidor tiene una lectura en revisión; confírmela o recházela primero");
        }
        if (m.getUltimoPeriodo() != null) {
            int siguiente = Periodos.siguiente(m.getUltimoPeriodo());
            if (req.periodo() != siguiente) {
                throw new NegocioException("LECTURA_INVALIDA", "Lectura fuera de secuencia: el siguiente periodo del medidor es " + siguiente);
            }
        } else if (req.periodo() < Periodos.dePeriodo(java.time.YearMonth.from(m.getFechaInstalacion()))) {
            throw new NegocioException("LECTURA_INVALIDA", "El periodo es anterior a la instalación del medidor");
        }
        if (req.lecturaActual() != null && req.lecturaActual().compareTo(m.getLecturaActual()) < 0) {
            throw new NegocioException("LECTURA_INVALIDA", "La lectura actual (" + req.lecturaActual().stripTrailingZeros().toPlainString()
                    + ") es menor que la anterior (" + m.getLecturaActual().stripTrailingZeros().toPlainString() + ")");
        }
    }

    private void avisarRevision(Lectura l, Medidor m) {
        Predio p = predios.findById(m.getPredioId()).orElseThrow();
        String contrato = contratos.findFirstByMedidorIdAndEstadoIn(m.getId(), VIGENTES)
                .map(ContratoServicio::getNumeroContrato).orElse("sin contrato vigente");
        avisos.aAsesores(p.getMunicipioId(), EventoNotificacion.LECTURA_EN_REVISION,
                "Lectura en revisión: medidor " + m.getSerial() + ", periodo " + l.getPeriodo(),
                "La lectura del medidor " + m.getSerial() + " (contrato " + contrato + ", " + p.getDireccion() + ") del periodo "
                        + l.getPeriodo() + " registra un consumo de " + texto(l.getConsumo()) + " m³ frente a un promedio de "
                        + texto(l.getPromedioReferencia()) + " m³" + (l.getDesviacionPct() == null ? "" : " (desviación "
                        + texto(l.getDesviacionPct()) + " %)") + ". Confírmela o facture por promedio antes del lote (P-07).",
                ENTIDAD, l.getId());
    }

    private static String texto(BigDecimal v) {
        return v == null ? "—" : v.stripTrailingZeros().toPlainString();
    }

    List<LecturaVista> vistas(Collection<Lectura> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, Medidor> ms = medidores.findAllById(lista.stream().map(Lectura::getMedidorId).distinct().toList()).stream()
                .collect(Collectors.toMap(Medidor::getId, Function.identity()));
        Map<Long, Predio> ps = predios.findAllById(ms.values().stream().map(Medidor::getPredioId).distinct().toList()).stream()
                .collect(Collectors.toMap(Predio::getId, Function.identity()));
        Map<Long, ContratoServicio> cs = new HashMap<>();
        contratos.findByMedidorIdInAndEstadoIn(ms.keySet(), VIGENTES).forEach(c -> cs.put(c.getMedidorId(), c));
        Map<Long, Usuario> us = usuarios.findAllById(cs.values().stream().map(ContratoServicio::getUsuarioId).distinct().toList())
                .stream().collect(Collectors.toMap(Usuario::getId, Function.identity()));
        Map<Long, String> municipios = catalogo.nombresMunicipio();
        return lista.stream().map(l -> {
            Medidor m = ms.get(l.getMedidorId());
            Predio p = ps.get(m.getPredioId());
            ContratoServicio c = cs.get(m.getId());
            Usuario u = c == null ? null : us.get(c.getUsuarioId());
            return new LecturaVista(l.getId(), m.getId(), m.getSerial(), m.getTipoServicio(), c == null ? null : c.getNumeroContrato(),
                    u == null ? null : u.getNombreCompleto(), p.getId(), p.getCodigoPredio(), p.getDireccion(), p.getMunicipioId(),
                    municipios.get(p.getMunicipioId()), l.getPeriodo(), l.getFechaLectura(), l.getLecturaAnterior(),
                    l.getLecturaActual(), l.getConsumo(), l.getEstado(), l.getOrigen(), l.getCausaNoLectura(),
                    l.getPromedioReferencia(), l.getDesviacionPct(), l.getObservacion(), l.getRegistradaPor(), l.getRevisadaPor(),
                    l.getFechaRevision(), l.getCreadoEn());
        }).toList();
    }
}
