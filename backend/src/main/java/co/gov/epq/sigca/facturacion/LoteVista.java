package co.gov.epq.sigca.facturacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Resultado de un lote de facturación (RF-05) con los predios que no se pudieron facturar. */
public record LoteVista(Long id, Integer periodo, EstadoLote estado, LocalDateTime iniciadoEn, LocalDateTime finalizadoEn,
        Long totalPredios, Long totalFacturas, Long totalErrores, BigDecimal valorTotal, String ejecutadoPor,
        List<ErrorPredio> errores) {

    /** Predio que no se facturó y por qué (lectura en revisión, sin tarifa, etc.). */
    public record ErrorPredio(Long usuarioId, Long predioId, String codigoPredio, String mensaje) {
    }
}
