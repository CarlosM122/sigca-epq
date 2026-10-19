package co.gov.epq.sigca.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Factura con sus líneas. La referencia de pago es el número de factura (P-18); el valor exigible es lo que se
 * paga hoy: saldo pendiente menos lo que está en reclamo (RN-33).
 */
public record FacturaDetalle(Long id, String numeroFactura, String referenciaPago, Integer periodo, Long usuarioId,
        String codigoUsuario, String titular, Long predioId, String codigoPredio, String direccion, String municipio,
        String uso, Integer estrato, LocalDate fechaEmision, LocalDate fechaVencimiento, Integer numeroLineas,
        BigDecimal totalAPagar, BigDecimal valorAjustes, BigDecimal valorPagado, BigDecimal valorEnReclamo,
        BigDecimal saldoPendiente, BigDecimal valorExigible, EstadoFactura estado, String motivoAnulacion,
        String reexpedicionDe, String reexpedidaComo, Long loteId, EstadoSyncErp estadoSyncErp, boolean consumoEstimado,
        List<LineaDetalle> lineas, Long version) {
}
