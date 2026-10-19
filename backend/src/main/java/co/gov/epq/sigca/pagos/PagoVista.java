package co.gov.epq.sigca.pagos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pago registrado y el estado real de la obligación después de aplicarlo (lo deriva la BD: PAGADA cuando el saldo
 * llega a cero, CUMPLIDO el acuerdo con la última cuota).
 */
public record PagoVista(Long id, String codigoPago, TipoObligacion tipoObligacion, Long obligacionId, String referencia,
        String codigoUsuario, String titular, BigDecimal valor, CanalPago canal, LocalDateTime fechaPago,
        String codigoTransaccion, String referenciaBanco, String estadoObligacion, BigDecimal saldoPendiente,
        BigDecimal valorEnReclamo, boolean comprobanteEnviado) {
}
