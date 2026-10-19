package co.gov.epq.sigca.pagos;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.common.error.ConflictoException;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.RecursoNoEncontradoException;

/**
 * Pago en línea (CU-04, RN-05, RN-14, RN-15, P-18): crea el intento ante la pasarela, procesa la confirmación
 * del webhook (idempotente por referencia y código de transacción) y expira los intentos sin respuesta.
 * La factura o la cuota solo cambian cuando llega una aprobación con firma válida.
 */
@Service
public class PagoEnLineaService {

    private static final Logger log = LoggerFactory.getLogger(PagoEnLineaService.class);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final TransaccionPasarelaRepository transacciones;
    private final PagoRepository pagos;
    private final Obligaciones obligaciones;
    private final PagoService pagoService;
    private final PasarelaPagos pasarela;
    private final PasarelaProperties props;
    private final Auditoria auditoria;

    public PagoEnLineaService(TransaccionPasarelaRepository transacciones, PagoRepository pagos, Obligaciones obligaciones,
            PagoService pagoService, PasarelaPagos pasarela, PasarelaProperties props, Auditoria auditoria) {
        this.transacciones = transacciones;
        this.pagos = pagos;
        this.obligaciones = obligaciones;
        this.pagoService = pagoService;
        this.pasarela = pasarela;
        this.props = props;
        this.auditoria = auditoria;
    }

    /**
     * Inicia el pago de una obligación propia del ciudadano por su valor exigible. Si ya hay un intento abierto por el
     * mismo valor se reutiliza (doble clic, volver atrás); si el valor cambió, el anterior se expira.
     */
    @Transactional
    public InicioPagoVista iniciar(Long usuarioId, TipoObligacion tipo, Long obligacionId, String ip) {
        Obligacion o = obligaciones.porId(tipo, obligacionId);
        if (!Objects.equals(o.usuarioId(), usuarioId)) {
            // No se revela si la obligación existe
            throw new RecursoNoEncontradoException(tipo == TipoObligacion.FACTURA ? "Factura" : "Cuota", obligacionId);
        }
        if (!o.admitePago()) {
            throw new ConflictoException("NO_ADMITE_PAGOS", o.motivoNoAdmite());
        }
        Optional<TransaccionPasarela> abierta = tipo == TipoObligacion.FACTURA
                ? transacciones.findFirstByFacturaIdAndEstado(o.id(), EstadoTransaccion.INICIADA)
                : transacciones.findFirstByCuotaIdAndEstado(o.id(), EstadoTransaccion.INICIADA);
        if (abierta.isPresent()) {
            TransaccionPasarela t = abierta.get();
            if (t.getMonto().compareTo(o.valorExigible()) == 0 && Objects.equals(t.getUsuarioId(), usuarioId)) {
                return inicio(t);
            }
            t.setEstado(EstadoTransaccion.EXPIRADA);
            t.setMensajeRespuesta("Reemplazada por un nuevo intento con otro valor");
            transacciones.saveAndFlush(t);
        }
        TransaccionPasarela t = new TransaccionPasarela();
        t.setReferencia("TX-" + HexFormat.of().withUpperCase().formatHex(bytes(16)));
        t.setReferenciaPago(o.referencia());
        if (tipo == TipoObligacion.FACTURA) {
            t.setFacturaId(o.id());
        } else {
            t.setCuotaId(o.id());
        }
        t.setUsuarioId(usuarioId);
        t.setMonto(o.valorExigible());
        t.setEstado(EstadoTransaccion.INICIADA);
        t.setFirmaValidada(false);
        t.setIpOrigen(ip);
        transacciones.saveAndFlush(t);
        return inicio(t);
    }

    private InicioPagoVista inicio(TransaccionPasarela t) {
        return new InicioPagoVista(t.getReferencia(), t.getReferenciaPago(), t.getMonto(), pasarela.urlCheckout(t));
    }

    /** Resultado de procesar una notificación de la pasarela. */
    public record ResultadoWebhook(String referencia, EstadoTransaccion estado, String codigoPago, boolean repetida) {
    }

    /**
     * Aplica una notificación con firma ya verificada. Bloquea la transacción para serializar notificaciones
     * repetidas; una repetida con el mismo resultado no cambia nada (idempotencia, RN-15).
     */
    @Transactional
    public ResultadoWebhook procesar(NotificacionPasarela n) {
        TransaccionPasarela t = transacciones.bloquearPorReferencia(n.referencia())
                .orElseThrow(() -> new RecursoNoEncontradoException("Transacción", n.referencia()));
        EstadoTransaccion nuevo = EstadoTransaccion.valueOf(n.estado());
        if (t.getEstado() != EstadoTransaccion.INICIADA) {
            boolean igual = t.getEstado() == nuevo
                    && (nuevo != EstadoTransaccion.APROBADA || Objects.equals(t.getCodigoTransaccion(), n.codigoTransaccion()));
            if (igual) {
                return new ResultadoWebhook(t.getReferencia(), t.getEstado(), codigoPago(t), true);
            }
            throw new ConflictoException("TRANSACCION_FINALIZADA",
                    "La transacción " + t.getReferencia() + " ya está " + t.getEstado());
        }
        if (n.monto().compareTo(t.getMonto()) != 0) {
            throw new NegocioException("VALOR_NO_COINCIDE", "El monto notificado no coincide con el de la transacción");
        }
        t.setCodigoRespuesta(recortar(n.codigoRespuesta(), 20));
        t.setMensajeRespuesta(recortar(n.mensaje(), 300));
        t.setFirmaValidada(true);
        switch (nuevo) {
            case APROBADA -> {
                if (n.codigoTransaccion() == null) {
                    throw new NegocioException("CODIGO_TRANSACCION_REQUERIDO", "Una aprobación exige el código de transacción (RN-15)");
                }
                if (pagos.existsByCodigoTransaccion(n.codigoTransaccion())) {
                    throw new ConflictoException("DUPLICADO", "El código de transacción ya fue aplicado a otro pago (RN-15)");
                }
                t.setCodigoTransaccion(n.codigoTransaccion());
                t.setEstado(EstadoTransaccion.APROBADA);
                transacciones.saveAndFlush(t);
                Obligacion o = t.getFacturaId() != null ? obligaciones.porId(TipoObligacion.FACTURA, t.getFacturaId())
                        : obligaciones.porId(TipoObligacion.CUOTA, t.getCuotaId());
                Pago p = pagoService.aplicar(o, CanalPago.EN_LINEA, t.getMonto(), null, null, t.getId(),
                        t.getCodigoTransaccion());
                return new ResultadoWebhook(t.getReferencia(), t.getEstado(), p.getCodigoPago(), false);
            }
            case RECHAZADA -> {
                t.setCodigoTransaccion(n.codigoTransaccion());
                if (t.getCodigoRespuesta() == null) {
                    t.setCodigoRespuesta("RECHAZADA");
                }
                t.setEstado(EstadoTransaccion.RECHAZADA);
                transacciones.saveAndFlush(t);
                auditoria.exito(OperacionAuditoria.PAGO_RECHAZADO, "TRANSACCION_PASARELA", t.getId(), detalle(t));
                return new ResultadoWebhook(t.getReferencia(), t.getEstado(), null, false);
            }
            default -> {
                t.setFirmaValidada(true);
                t.setEstado(EstadoTransaccion.ERROR);
                transacciones.saveAndFlush(t);
                auditoria.exito(OperacionAuditoria.PAGO_RECHAZADO, "TRANSACCION_PASARELA", t.getId(), detalle(t));
                return new ResultadoWebhook(t.getReferencia(), t.getEstado(), null, false);
            }
        }
    }

    /**
     * La pasarela aprobó pero la BD no aceptó el pago (por ejemplo, la factura se pagó en caja mientras tanto): el
     * intento queda en ERROR para conciliación y reverso, y se audita como pago rechazado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarNoAplicable(String referencia, String motivo) {
        transacciones.bloquearPorReferencia(referencia).ifPresent(t -> {
            if (t.getEstado() != EstadoTransaccion.INICIADA) {
                return;
            }
            t.setEstado(EstadoTransaccion.ERROR);
            t.setMensajeRespuesta(recortar("Aprobación no aplicada, requiere reverso: " + motivo, 300));
            transacciones.saveAndFlush(t);
            auditoria.exito(OperacionAuditoria.PAGO_RECHAZADO, "TRANSACCION_PASARELA", t.getId(), detalle(t));
        });
        log.warn("Pago en línea {} aprobado por la pasarela pero no aplicado: {}", referencia, motivo);
    }

    @Transactional(readOnly = true)
    public EstadoTransaccionVista estado(String referencia) {
        TransaccionPasarela t = transacciones.findByReferencia(referencia)
                .orElseThrow(() -> new RecursoNoEncontradoException("Transacción", referencia));
        return new EstadoTransaccionVista(t.getReferencia(), t.getReferenciaPago(), t.getEstado(), t.getMonto(),
                t.getMensajeRespuesta(), codigoPago(t));
    }

    @Transactional(readOnly = true)
    public TransaccionPasarela transaccion(String referencia) {
        return transacciones.findByReferencia(referencia)
                .orElseThrow(() -> new NegocioException("TRANSACCION_NO_ENCONTRADA", "Transacción no encontrada",
                        HttpStatus.NOT_FOUND));
    }

    /** CO-02: un intento sin respuesta de la pasarela se expira para poder reintentar. */
    @Scheduled(fixedDelayString = "${sigca.integraciones.pasarela.intervalo-expiracion-ms:300000}", initialDelay = 60000)
    @Transactional
    public void expirar() {
        int n = transacciones.expirarAnteriores(props.minutosExpiracion());
        if (n > 0) {
            log.info("{} intentos de pago en línea expirados", n);
        }
    }

    private String codigoPago(TransaccionPasarela t) {
        return pagos.findByTransaccionPasarelaId(t.getId()).map(Pago::getCodigoPago).orElse(null);
    }

    private static Map<String, Object> detalle(TransaccionPasarela t) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("referencia", t.getReferencia());
        d.put("referenciaPago", t.getReferenciaPago());
        d.put("monto", t.getMonto());
        d.put("estado", t.getEstado());
        if (t.getCodigoRespuesta() != null) {
            d.put("codigoRespuesta", t.getCodigoRespuesta());
        }
        return d;
    }

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        ALEATORIO.nextBytes(b);
        return b;
    }

    private static String recortar(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }

}
