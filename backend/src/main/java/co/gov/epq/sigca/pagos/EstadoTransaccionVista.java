package co.gov.epq.sigca.pagos;

import java.math.BigDecimal;

/** Estado de un intento de pago en línea, tal como lo confirmó la pasarela (nunca lo que dice la URL de retorno). */
public record EstadoTransaccionVista(String referencia, String referenciaPago, EstadoTransaccion estado, BigDecimal monto,
        String mensaje, String codigoPago) {
}
