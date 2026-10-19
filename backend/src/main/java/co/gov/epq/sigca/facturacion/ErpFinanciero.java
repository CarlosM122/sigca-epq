package co.gov.epq.sigca.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Puerto hacia el ERP financiero (IS-01, SWR-17, P-19). Devuelve la referencia que asigna el ERP; lanza
 * {@link ErpNoDisponibleException} si no responde, para reintentar en la siguiente corrida.
 */
public interface ErpFinanciero {

    String registrarFactura(FacturaErp factura);

    String registrarPago(PagoErp pago);

    record FacturaErp(String numeroFactura, String codigoUsuario, Integer periodo, LocalDate fechaEmision,
            LocalDate fechaVencimiento, BigDecimal total, String estado) {
    }

    record PagoErp(String codigoPago, String codigoUsuario, String referencia, LocalDateTime fechaPago, BigDecimal valor,
            String canal) {
    }

    class ErpNoDisponibleException extends RuntimeException {
        public ErpNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }
}
