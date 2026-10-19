package co.gov.epq.sigca.pagos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import co.gov.epq.sigca.facturacion.LineaDetalle;

/**
 * Lo que se paga: una factura o una cuota de acuerdo. {@code valorExigible} es exactamente lo que debe pagarse
 * (P-05, CO-02): saldo menos lo que está en reclamo (RN-33), o el valor de la cuota.
 *
 * @param referencia    número de factura o {@code AP-AAAA-NNNNNN-NN} (P-18)
 * @param admitePago    si hoy se le puede registrar un pago
 * @param motivoNoAdmite por qué no, en lenguaje del cajero o del ciudadano
 */
public record Obligacion(TipoObligacion tipo, Long id, String referencia, Long usuarioId, String codigoUsuario,
        String titular, String estado, BigDecimal totalAPagar, BigDecimal valorEnReclamo, BigDecimal valorExigible,
        LocalDate fechaVencimiento, boolean admitePago, String motivoNoAdmite, List<LineaDetalle> lineas) {

    public static String referenciaCuota(String numeroAcuerdo, Integer numeroCuota) {
        return numeroAcuerdo == null || numeroCuota == null ? null
                : numeroAcuerdo + "-" + String.format("%02d", numeroCuota);
    }
}
