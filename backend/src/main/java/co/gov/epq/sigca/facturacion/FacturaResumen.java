package co.gov.epq.sigca.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fila de la búsqueda de facturas. Valor exigible = saldo − en reclamo (RN-33). */
public record FacturaResumen(Long id, String numeroFactura, Integer periodo, String codigoUsuario, String titular,
        String codigoPredio, String direccion, String municipio, LocalDate fechaEmision, LocalDate fechaVencimiento,
        BigDecimal totalAPagar, BigDecimal saldoPendiente, BigDecimal valorEnReclamo, BigDecimal valorExigible,
        EstadoFactura estado, Long reexpedicionDeId) {

    /** Constructor de la proyección JPQL: el valor exigible se deriva. */
    public FacturaResumen(Long id, String numeroFactura, Integer periodo, String codigoUsuario, String titular,
            String codigoPredio, String direccion, String municipio, LocalDate fechaEmision, LocalDate fechaVencimiento,
            BigDecimal totalAPagar, BigDecimal saldoPendiente, BigDecimal valorEnReclamo, EstadoFactura estado,
            Long reexpedicionDeId) {
        this(id, numeroFactura, periodo, codigoUsuario, titular, codigoPredio, direccion, municipio, fechaEmision,
                fechaVencimiento, totalAPagar, saldoPendiente, valorEnReclamo, saldoPendiente.subtract(valorEnReclamo),
                estado, reexpedicionDeId);
    }
}
