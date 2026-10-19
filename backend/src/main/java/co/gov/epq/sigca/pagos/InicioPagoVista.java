package co.gov.epq.sigca.pagos;

import java.math.BigDecimal;

/** Intento de pago en línea creado: el portal redirige al ciudadano a {@code urlCheckout}. */
public record InicioPagoVista(String referencia, String referenciaPago, BigDecimal monto, String urlCheckout) {
}
