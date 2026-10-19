package co.gov.epq.sigca.pqr;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.auditoria.ResultadoAuditoria;
import co.gov.epq.sigca.catalogo.Parametros;
import co.gov.epq.sigca.common.cifrado.CifradorIdentificacion;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;
import co.gov.epq.sigca.common.tiempo.Periodos;
import co.gov.epq.sigca.common.validation.Normalizador;
import co.gov.epq.sigca.contratos.EstadoContrato;
import co.gov.epq.sigca.facturacion.EstadoFactura;
import co.gov.epq.sigca.facturacion.EstadoLinea;
import co.gov.epq.sigca.pqr.PqrConsultas.ContratoInfo;
import co.gov.epq.sigca.pqr.PqrConsultas.LineaInfo;
import co.gov.epq.sigca.seguridad.UsuarioActual;

/**
 * Radicación de PQR por el portal, la ventanilla y el formulario público (RF-08, P-14, P-15), consulta por radicado
 * (RF-10) y las PQR propias del ciudadano. La BD pone radicado, fechas y plazo y es la última barrera de cada regla;
 * aquí se validan antes para dar mensajes claros, se asigna el gestor de menor carga (RN-20) y se notifica.
 */
@Service
public class RadicacionPqrService {

    static final String ENTIDAD = "PQR";

    private final PqrRepository repo;
    private final PqrAdjuntoRepository adjuntos;
    private final PqrConsultas consultas;
    private final CifradorIdentificacion cifrador;
    private final Parametros parametros;
    private final Auditoria auditoria;
    private final AvisosPqr avisos;

    public RadicacionPqrService(PqrRepository repo, PqrAdjuntoRepository adjuntos, PqrConsultas consultas,
            CifradorIdentificacion cifrador, Parametros parametros, Auditoria auditoria, AvisosPqr avisos) {
        this.repo = repo;
        this.adjuntos = adjuntos;
        this.consultas = consultas;
        this.cifrador = cifrador;
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.avisos = avisos;
    }

    /** Ciudadano autenticado: canal PORTAL sobre uno de sus contratos. */
    @Transactional
    public PqrVistas.Radicada radicarPortal(PqrSolicitudes.Radicar r, List<ValidadorAdjuntos.Entrada> archivos) {
        UsuarioActual u = UsuarioActual.ciudadano();
        if (vacio(r.numeroContrato())) {
            throw new NegocioException("CONTRATO_REQUERIDO", "Seleccione el contrato sobre el que presenta la solicitud");
        }
        ContratoInfo c = contratoVigente(r.numeroContrato());
        if (!c.usuarioId().equals(u.usuarioId())) {
            throw new NegocioException("CONTRATO_NO_VALIDO", "El contrato " + r.numeroContrato() + " no está a su nombre");
        }
        Pqr p = radicar(r, c, CanalPqr.PORTAL, null, archivos);
        auditoria.exito(operacion(p), ENTIDAD, p.getId(), detalle(p));
        return vista(p);
    }

    /** Ventanilla: la registra un Gestor o el Jefe de PQR (CANAL PRESENCIAL, REGISTRADA_POR = funcionario). */
    @Transactional
    public PqrVistas.Radicada radicarPresencial(PqrSolicitudes.Radicar r, List<ValidadorAdjuntos.Entrada> archivos) {
        UsuarioActual u = UsuarioActual.funcionario();
        ContratoInfo c = vacio(r.numeroContrato()) ? null : contratoVigente(r.numeroContrato());
        Pqr p = radicar(r, c, CanalPqr.PRESENCIAL, u.funcionarioId(), archivos);
        auditoria.exito(operacion(p), ENTIDAD, p.getId(), detalle(p));
        return vista(p);
    }

    /**
     * Formulario público sin sesión (P-14). Sin contrato solo se radica una PETICION con la identificación del
     * solicitante; con contrato, la identificación debe ser la del titular.
     */
    @Transactional
    public PqrVistas.Radicada radicarPublica(PqrSolicitudes.Radicar r, List<ValidadorAdjuntos.Entrada> archivos) {
        if (!Boolean.TRUE.equals(r.autorizaTratamientoDatos())) {
            throw new NegocioException("AUTORIZACION_REQUERIDA",
                    "Debe autorizar el tratamiento de sus datos personales para radicar (Ley 1581 de 2012)");
        }
        if (r.tipoIdentificacion() == null || vacio(r.numeroIdentificacion())) {
            throw new NegocioException("IDENTIFICACION_REQUERIDA", "Indique su tipo y número de identificación");
        }
        ContratoInfo c = null;
        if (!vacio(r.numeroContrato())) {
            c = contratoVigente(r.numeroContrato());
            String hash = cifrador.hash(r.tipoIdentificacion(), Normalizador.documento(r.numeroIdentificacion()));
            if (c.tipoIdentificacion() != r.tipoIdentificacion() || !hash.equals(c.hashIdentificacion())) {
                throw new NegocioException("IDENTIFICACION_NO_COINCIDE",
                        "La identificación no corresponde al titular del contrato " + r.numeroContrato());
            }
        }
        Pqr p = radicar(r, c, CanalPqr.PORTAL, null, archivos);
        auditoria.como(Auditoria.Actor.ANONIMO, operacion(p), ResultadoAuditoria.EXITO, ENTIDAD, p.getId(), detalle(p));
        return vista(p);
    }

    private Pqr radicar(PqrSolicitudes.Radicar r, ContratoInfo c, CanalPqr canal, Long registradaPor,
            List<ValidadorAdjuntos.Entrada> entradas) {
        List<ValidadorAdjuntos.Archivo> archivos = ValidadorAdjuntos.validar(entradas, maxBytes());
        Pqr p = new Pqr();
        p.setTipo(r.tipo());
        p.setCanal(canal);
        p.setRegistradaPor(registradaPor);
        p.setSolicitaApelacion(false);
        if (c != null) {
            p.setContratoId(c.id());
            p.setUsuarioId(c.usuarioId());
            p.setMunicipioId(c.municipioId());
        } else {
            if (r.tipo() != TipoPqr.PETICION) {
                throw new NegocioException("CONTRATO_REQUERIDO", "Una " + r.tipo().name().toLowerCase() + " exige contrato");
            }
            if (r.municipioId() == null || !consultas.municipioActivo(r.municipioId())) {
                throw new NegocioException("MUNICIPIO_NO_ATENDIDO", "El municipio no es atendido por EPQ");
            }
            String numero = Normalizador.documento(r.numeroIdentificacion());
            p.setMunicipioId(r.municipioId());
            p.setSolicitanteTipoIdentificacion(r.tipoIdentificacion());
            p.setSolicitanteIdentificacionCifrado(cifrador.cifrar(numero));
            p.setSolicitanteIdentificacionHash(cifrador.hash(r.tipoIdentificacion(), numero));
            p.setSolicitanteIdentificacionMascara(CifradorIdentificacion.mascara(numero));
        }
        if (r.tipo() == TipoPqr.RECURSO) {
            Pqr origen = origenRecurrible(r.radicadoOriginal(), c);
            p.setPqrOrigenId(origen.getId());
            p.setSolicitaApelacion(Boolean.TRUE.equals(r.solicitaApelacion()));
            p.setLineaFacturaId(origen.getLineaFacturaId());
        }
        if (r.tipo() == TipoPqr.RECLAMO && !vacio(r.numeroFactura())) {
            p.setLineaFacturaId(lineaReclamable(r.numeroFactura(), c).id());
        }
        p.setDescripcion(r.descripcion().strip());
        p.setNombreContacto(Normalizador.texto(r.nombreContacto()));
        p.setCorreoContacto(Normalizador.correo(r.correoContacto()));
        p.setTelefonoContacto(Normalizador.telefono(r.telefonoContacto()));
        p.setEstado(EstadoPqr.RECIBIDA);
        p.setAlerta48hEnviada(false);
        p.setReportadaSspd(false);
        p.setGestorId(asignarGestor(p.getMunicipioId()));
        p = repo.saveAndFlush(p);
        guardarAdjuntos(p.getId(), archivos, TipoAdjunto.SOPORTE, registradaPor);
        avisos.radicada(p);
        avisos.asignada(p);
        return p;
    }

    /** RN-20 / P-14: gestor activo del municipio con menos PQR abiertas; si no hay, el Jefe de PQR. */
    Long asignarGestor(Long municipioId) {
        List<PqrConsultas.Carga> gestores = consultas.cargas("GESTOR_PQR", municipioId);
        if (!gestores.isEmpty()) {
            return gestores.getFirst().id();
        }
        List<PqrConsultas.Carga> jefes = consultas.cargas("JEFE_PQR", null);
        if (!jefes.isEmpty()) {
            return jefes.getFirst().id();
        }
        throw new NegocioException("SIN_RESPONSABLE",
                "No hay gestores de PQR disponibles para asignar la solicitud. Intente más tarde", HttpStatus.SERVICE_UNAVAILABLE);
    }

    void guardarAdjuntos(Long pqrId, List<ValidadorAdjuntos.Archivo> archivos, TipoAdjunto tipo, Long cargadoPor) {
        for (ValidadorAdjuntos.Archivo a : archivos) {
            if (adjuntos.existsByPqrIdAndHashSha256(pqrId, a.sha256())) {
                throw new ConflictoException("ADJUNTO_DUPLICADO", "El archivo \"" + a.nombre() + "\" ya está adjunto a la PQR");
            }
            PqrAdjunto adj = new PqrAdjunto();
            adj.setPqrId(pqrId);
            adj.setTipo(tipo);
            adj.setNombreArchivo(a.nombre());
            adj.setTipoMime(a.tipoMime());
            adj.setTamanoBytes((long) a.contenido().length);
            adj.setHashSha256(a.sha256());
            adj.setContenido(a.contenido());
            adj.setCargadoPor(cargadoPor);
            adjuntos.save(adj);
        }
        adjuntos.flush();
    }

    long maxBytes() {
        return parametros.entero(Parametros.ADJUNTO_MAX_MB) * 1_048_576L;
    }

    /** RN-17 (P-29): el contrato existe y está activo o suspendido. */
    private ContratoInfo contratoVigente(String numero) {
        ContratoInfo c = consultas.contrato(numero)
                .orElseThrow(() -> new NegocioException("CONTRATO_NO_VALIDO",
                        "No existe el contrato " + numero + ". Verifique el número o acérquese a una oficina de EPQ"));
        if (c.estado() == EstadoContrato.CANCELADO) {
            throw new NegocioException("CONTRATO_NO_VALIDO", "El contrato " + numero + " está cancelado");
        }
        return c;
    }

    /** P-15: se recurre la respuesta de una petición, queja o reclamo del mismo contrato, una sola vez y en plazo. */
    private Pqr origenRecurrible(String radicado, ContratoInfo c) {
        Pqr origen = repo.findByNumeroRadicado(radicado)
                .filter(o -> c != null && Objects.equals(o.getContratoId(), c.id()))
                .orElseThrow(() -> new NegocioException("PQR_ORIGEN_NO_VALIDA",
                        "No existe una PQR con el radicado " + radicado + " en el contrato indicado"));
        if (origen.getTipo() == TipoPqr.RECURSO) {
            throw new NegocioException("PQR_ORIGEN_NO_VALIDA", "La decisión de un recurso no admite otro recurso");
        }
        if (origen.getEstado() != EstadoPqr.RESPONDIDA) {
            throw new NegocioException("PQR_ORIGEN_NO_VALIDA", "Solo se recurre una PQR que ya fue respondida");
        }
        if (repo.findByPqrOrigenId(origen.getId()).isPresent()) {
            throw new ConflictoException("DUPLICADO", "Esa respuesta ya fue recurrida");
        }
        if (Periodos.hoy().isAfter(limiteRecurso(origen))) {
            throw new NegocioException("PLAZO_LEGAL", "Venció el plazo para recurrir la respuesta: "
                    + parametros.entero(AvisosPqr.RECURSO_DIAS_PLAZO) + " días hábiles desde la respuesta (RN-32)");
        }
        return origen;
    }

    LocalDate limiteRecurso(Pqr origen) {
        return consultas.sumarDiasHabiles(origen.getFechaRespuesta().toLocalDate(),
                parametros.entero(AvisosPqr.RECURSO_DIAS_PLAZO));
    }

    /** RN-33: la línea de esa factura y contrato debe estar pendiente de pago en una factura con cobro. */
    private LineaInfo lineaReclamable(String numeroFactura, ContratoInfo c) {
        LineaInfo l = consultas.lineaDeFactura(numeroFactura, c.id())
                .orElseThrow(() -> new NegocioException("FACTURA_NO_CORRESPONDE",
                        "La factura " + numeroFactura + " no tiene cobros del contrato " + c.numero()));
        if (l.estado() != EstadoLinea.PENDIENTE
                || (l.estadoFactura() != EstadoFactura.PENDIENTE && l.estadoFactura() != EstadoFactura.VENCIDA)) {
            throw new NegocioException("LINEA_NO_RECLAMABLE", "El cobro del contrato en la factura " + numeroFactura
                    + " ya está pagado, anulado o en un acuerdo de pago");
        }
        return l;
    }

    // ------------------------------------------------------------------ consultas

    /** RF-10 / RN-24: radicado + identificación del solicitante o del titular; sin coincidencia, 404 genérico. */
    @Transactional(readOnly = true)
    public PqrVistas.ConsultaPublica consultar(PqrSolicitudes.ConsultaPublica c) {
        String hash = cifrador.hash(c.tipoIdentificacion(), Normalizador.documento(c.numeroIdentificacion()));
        Pqr p = repo.findByNumeroRadicado(c.radicado())
                .filter(x -> pertenece(x, c, hash))
                .orElseThrow(() -> new NegocioException("PQR_NO_ENCONTRADA",
                        "No encontramos una PQR con ese radicado para esa identificación", HttpStatus.NOT_FOUND));
        boolean conRespuesta = p.getEstado() == EstadoPqr.RESPONDIDA || p.getEstado() == EstadoPqr.TRASLADADA_SSPD;
        return new PqrVistas.ConsultaPublica(p.getNumeroRadicado(), p.getTipo(), p.getEstado(), p.getFechaRegistro(),
                p.getFechaLimiteRespuesta(), conRespuesta ? p.getFechaRespuesta() : null,
                conRespuesta ? p.getRespuesta() : null, p.getDecisionRecurso(), Boolean.TRUE.equals(p.getSilencioPositivo()));
    }

    private boolean pertenece(Pqr p, PqrSolicitudes.ConsultaPublica c, String hash) {
        if (p.getUsuarioId() == null) {
            return p.getSolicitanteTipoIdentificacion() == c.tipoIdentificacion()
                    && hash.equals(p.getSolicitanteIdentificacionHash());
        }
        return consultas.usuario(p.getUsuarioId())
                .map(u -> u.tipoIdentificacion() == c.tipoIdentificacion() && hash.equals(u.hashIdentificacion()))
                .orElse(false);
    }

    /** RF-24 en el portal: las PQR de los contratos del ciudadano autenticado. */
    @Transactional(readOnly = true)
    public List<PqrVistas.Portal> misPqr() {
        Long usuarioId = UsuarioActual.ciudadano().usuarioId();
        List<Pqr> lista = repo.findByUsuarioIdOrderByFechaRegistroDesc(usuarioId);
        Map<Long, Pqr> porId = lista.stream().collect(Collectors.toMap(Pqr::getId, Function.identity()));
        Map<Long, Pqr> recursos = repo.findByPqrOrigenIdIn(porId.keySet().isEmpty() ? List.of(-1L) : porId.keySet())
                .stream().collect(Collectors.toMap(Pqr::getPqrOrigenId, Function.identity()));
        Map<Long, String> contratos = new LinkedHashMap<>();
        consultas.contratosVigentes(usuarioId).forEach(c -> contratos.put(c.id(), c.numero()));
        LocalDate hoy = Periodos.hoy();
        return lista.stream().map(p -> {
            String contrato = contratos.computeIfAbsent(p.getContratoId(),
                    id -> id == null ? null : consultas.contrato(id).map(ContratoInfo::numero).orElse(null));
            String factura = p.getLineaFacturaId() == null ? null
                    : consultas.linea(p.getLineaFacturaId()).map(LineaInfo::numeroFactura).orElse(null);
            Pqr origen = p.getPqrOrigenId() == null ? null
                    : Optional.ofNullable(porId.get(p.getPqrOrigenId())).orElseGet(() -> repo.findById(p.getPqrOrigenId()).orElse(null));
            Pqr recurso = recursos.get(p.getId());
            LocalDate limite = recurrible(p) ? limiteRecurso(p) : null;
            boolean puede = limite != null && recurso == null && !hoy.isAfter(limite);
            boolean conRespuesta = p.getEstado() == EstadoPqr.RESPONDIDA || p.getEstado() == EstadoPqr.TRASLADADA_SSPD;
            return new PqrVistas.Portal(p.getId(), p.getNumeroRadicado(), p.getTipo(), p.getEstado(), contrato, factura,
                    p.getFechaRegistro(), p.getFechaLimiteRespuesta(), conRespuesta ? p.getFechaRespuesta() : null,
                    conRespuesta ? p.getRespuesta() : null, p.getDecisionRecurso(), p.getDecisionSspd(),
                    Boolean.TRUE.equals(p.getSilencioPositivo()), origen == null ? null : origen.getNumeroRadicado(),
                    recurso == null ? null : recurso.getNumeroRadicado(), puede, limite,
                    adjuntos.metadatos(p.getId()).stream().map(TramitePqrService::adjunto).toList());
        }).toList();
    }

    /** Descarga de un adjunto de una PQR propia del ciudadano autenticado. */
    @Transactional(readOnly = true)
    public PqrAdjunto adjuntoPortal(Long pqrId, Long adjuntoId) {
        Long usuarioId = UsuarioActual.ciudadano().usuarioId();
        repo.findById(pqrId).filter(p -> usuarioId.equals(p.getUsuarioId()))
                .orElseThrow(() -> new RecursoNoEncontradoException("PQR", pqrId));
        PqrAdjunto a = adjuntos.findByIdAndPqrId(adjuntoId, pqrId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Adjunto", adjuntoId));
        a.getContenido();
        return a;
    }

    /** Datos para el formulario del portal: contacto del titular y sus contratos con lo reclamable y recurrible. */
    @Transactional(readOnly = true)
    public PqrVistas.ContextoPortal contextoPortal() {
        Long usuarioId = UsuarioActual.ciudadano().usuarioId();
        PqrConsultas.UsuarioInfo u = consultas.usuario(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", usuarioId));
        List<PqrVistas.ContratoPqr> contratos = consultas.contratosVigentes(usuarioId).stream().map(this::contratoPqr).toList();
        return new PqrVistas.ContextoPortal(u.nombre(), u.correo(), u.telefono(), contratos);
    }

    /** Contrato para la ventanilla: titular, facturas reclamables y PQR recurribles. */
    @Transactional(readOnly = true)
    public PqrVistas.ContratoPqr contratoParaRadicar(String numero) {
        return contratoPqr(contratoVigente(numero));
    }

    private PqrVistas.ContratoPqr contratoPqr(ContratoInfo c) {
        List<PqrVistas.FacturaReclamable> facturas = consultas.lineasReclamables(c.id()).stream()
                .map(l -> new PqrVistas.FacturaReclamable(l.numeroFactura(), l.periodo(), l.totalLinea().add(l.valorAjuste()),
                        l.enReclamo()))
                .toList();
        List<Pqr> respondidas = repo.findByContratoIdAndEstadoOrderByFechaRespuestaDesc(c.id(), EstadoPqr.RESPONDIDA)
                .stream().filter(this::recurrible).toList();
        Map<Long, Pqr> recursos = respondidas.isEmpty() ? Map.of()
                : repo.findByPqrOrigenIdIn(respondidas.stream().map(Pqr::getId).toList()).stream()
                        .collect(Collectors.toMap(Pqr::getPqrOrigenId, Function.identity()));
        LocalDate hoy = Periodos.hoy();
        List<PqrVistas.PqrRecurrible> recurribles = respondidas.stream()
                .filter(p -> !recursos.containsKey(p.getId()))
                .map(p -> new PqrVistas.PqrRecurrible(p.getNumeroRadicado(), p.getTipo(), p.getFechaRespuesta(), limiteRecurso(p)))
                .filter(p -> !hoy.isAfter(p.fechaLimiteRecurso()))
                .toList();
        return new PqrVistas.ContratoPqr(c.id(), c.numero(), c.tipoServicio(), c.estado(), c.titular(), c.direccion(),
                c.municipio(), facturas, recurribles);
    }

    private boolean recurrible(Pqr p) {
        return p.getEstado() == EstadoPqr.RESPONDIDA && p.getTipo() != TipoPqr.RECURSO && p.getContratoId() != null
                && p.getFechaRespuesta() != null;
    }

    private static OperacionAuditoria operacion(Pqr p) {
        return p.getTipo() == TipoPqr.RECURSO ? OperacionAuditoria.REGISTRAR_RECURSO : OperacionAuditoria.REGISTRAR_PQR;
    }

    private static Map<String, Object> detalle(Pqr p) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("radicado", p.getNumeroRadicado());
        d.put("tipo", p.getTipo().name());
        d.put("canal", p.getCanal().name());
        d.put("gestorId", p.getGestorId());
        if (p.getPqrOrigenId() != null) {
            d.put("pqrOrigenId", p.getPqrOrigenId());
            d.put("apelacion", p.getSolicitaApelacion());
        }
        if (p.getLineaFacturaId() != null) {
            d.put("lineaFacturaId", p.getLineaFacturaId());
        }
        return d;
    }

    private static PqrVistas.Radicada vista(Pqr p) {
        return new PqrVistas.Radicada(p.getId(), p.getNumeroRadicado(), p.getTipo(), p.getEstado(), p.getFechaRegistro(),
                p.getFechaLimiteRespuesta());
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
