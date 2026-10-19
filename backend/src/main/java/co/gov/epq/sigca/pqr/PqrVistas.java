package co.gov.epq.sigca.pqr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import co.gov.epq.sigca.contratos.EstadoContrato;
import co.gov.epq.sigca.contratos.TipoServicio;
import co.gov.epq.sigca.facturacion.EstadoLinea;

/** Respuestas de la API de PQR. Nunca exponen la identificación completa del solicitante. */
public final class PqrVistas {

    private PqrVistas() {
    }

    /** Constancia de radicación (RF-08): radicado y fecha límite que calculó la BD. */
    public record Radicada(Long id, String numeroRadicado, TipoPqr tipo, EstadoPqr estado, LocalDateTime fechaRegistro,
            LocalDate fechaLimiteRespuesta) {
    }

    /** Consulta sin sesión (RF-10, RN-24): solo datos del trámite, nada de terceros. */
    public record ConsultaPublica(String numeroRadicado, TipoPqr tipo, EstadoPqr estado, LocalDateTime fechaRegistro,
            LocalDate fechaLimiteRespuesta, LocalDateTime fechaRespuesta, String respuesta, DecisionRecurso decisionRecurso,
            boolean silencioPositivo) {
    }

    /** Fila de la bandeja de trámite. */
    public record Resumen(Long id, String numeroRadicado, TipoPqr tipo, CanalPqr canal, EstadoPqr estado, Long municipioId,
            String municipio, String numeroContrato, LocalDateTime fechaRegistro, LocalDate fechaLimiteRespuesta,
            Long gestorId, String gestor, boolean silencioPositivo, boolean alertaEnviada, Integer diasHabilesRestantes) {
    }

    public record Historial(LocalDateTime fecha, EstadoPqr estadoAnterior, EstadoPqr estadoNuevo, String gestorAnterior,
            String gestorNuevo, String realizadoPor) {
    }

    public record ActuacionVista(LocalDateTime fecha, String funcionario, String descripcion) {
    }

    public record Adjunto(Long id, TipoAdjunto tipo, String nombreArchivo, String tipoMime, Long tamanoBytes,
            LocalDateTime cargadoEn) {
    }

    /** Línea de factura reclamada (RN-33). */
    public record Linea(Long id, String numeroFactura, Integer periodo, TipoServicio tipoServicio, BigDecimal totalLinea,
            BigDecimal valorAjuste, EstadoLinea estado, boolean enReclamo) {
    }

    public record Referencia(Long id, String numeroRadicado, EstadoPqr estado) {
    }

    /** Detalle para el funcionario que tramita. */
    public record Detalle(Long id, String numeroRadicado, TipoPqr tipo, CanalPqr canal, EstadoPqr estado,
            Long municipioId, String municipio, String numeroContrato, String titular, String solicitanteIdentificacion,
            LocalDateTime fechaRegistro, LocalDate fechaLimiteRespuesta, Long gestorId, String gestor,
            Integer diasHabilesRestantes, String descripcion, String nombreContacto, String correoContacto,
            String telefonoContacto, String registradaPor, String respuesta, LocalDateTime fechaRespuesta,
            String respondidaPor, boolean respuestaExtemporanea, DecisionRecurso decisionRecurso, boolean solicitaApelacion,
            Referencia pqrOrigen, Referencia recurso, Linea linea, LocalDateTime fechaTrasladoSspd,
            DecisionRecurso decisionSspd, LocalDateTime fechaDecisionSspd, boolean silencioPositivo,
            LocalDateTime fechaLimiteReconocimiento, LocalDateTime fechaReconocimientoSilencio, boolean alertaEnviada,
            List<Historial> historial, List<ActuacionVista> actuaciones, List<Adjunto> adjuntos) {
    }

    /** PQR propia en el portal, con lo necesario para presentar un recurso (P-15). */
    public record Portal(Long id, String numeroRadicado, TipoPqr tipo, EstadoPqr estado, String numeroContrato,
            String numeroFactura, LocalDateTime fechaRegistro, LocalDate fechaLimiteRespuesta, LocalDateTime fechaRespuesta,
            String respuesta, DecisionRecurso decisionRecurso, DecisionRecurso decisionSspd, boolean silencioPositivo,
            String radicadoOrigen, String radicadoRecurso, boolean puedeRecurrir, LocalDate fechaLimiteRecurso,
            List<Adjunto> adjuntos) {
    }

    /** Factura del contrato que se puede reclamar (línea pendiente, factura sin acuerdo ni anulación). */
    public record FacturaReclamable(String numeroFactura, Integer periodo, BigDecimal totalLinea, boolean enReclamo) {
    }

    public record PqrRecurrible(String numeroRadicado, TipoPqr tipo, LocalDateTime fechaRespuesta, LocalDate fechaLimiteRecurso) {
    }

    /** Contrato con lo que se puede reclamar o recurrir en él. */
    public record ContratoPqr(Long id, String numeroContrato, TipoServicio tipoServicio, EstadoContrato estado,
            String titular, String direccion, String municipio, List<FacturaReclamable> facturas,
            List<PqrRecurrible> recurribles) {
    }

    /** Datos que precargan el formulario del portal. */
    public record ContextoPortal(String nombre, String correo, String telefono, List<ContratoPqr> contratos) {
    }

    public record Gestor(Long id, String nombre, String rol, String municipio, long pqrAbiertas) {
    }
}
