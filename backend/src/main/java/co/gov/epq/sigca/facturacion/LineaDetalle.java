package co.gov.epq.sigca.facturacion;

import java.math.BigDecimal;

import co.gov.epq.sigca.contratos.TipoServicio;

/** Línea de factura por servicio, con la lectura del periodo y su liquidación (RF-07, P-08). */
public record LineaDetalle(Long id, Long contratoId, String numeroContrato, TipoServicio tipoServicio, Integer periodoLectura,
        BigDecimal lecturaAnterior, BigDecimal lecturaActual, BigDecimal consumo, BigDecimal consumoBasico,
        BigDecimal consumoComplementario, BigDecimal consumoSuntuario, Boolean consumoEstimado, BigDecimal cargoFijo,
        BigDecimal valorConsumo, BigDecimal subsidio, BigDecimal contribucion, BigDecimal otrosCargos,
        String detalleOtrosCargos, BigDecimal interesesMora, BigDecimal totalLinea, BigDecimal valorAjuste,
        EstadoLinea estado, Boolean enReclamo) {
}
