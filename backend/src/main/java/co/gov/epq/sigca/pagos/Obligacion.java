package co.gov.epq.sigca.pagos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import co.gov.epq.sigca.common.error.ConflictoException;
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

    /**
     * Exige que hoy se le pueda registrar un pago; si no, el rechazo lleva el motivo que ve el cajero o el ciudadano.
     * Es la única copia de la regla en Java: la usan el pago en caja y banco ({@link PagoService}) y el pago en línea
     * ({@link PagoEnLineaService}). Oracle la vuelve a validar en {@code TRG_PAGO_BI}.
     */
    public void exigirAdmitePago() {
        if (!admitePago) {
            throw new ConflictoException("NO_ADMITE_PAGOS", motivoNoAdmite);
        }
    }

    public static String referenciaCuota(String numeroAcuerdo, Integer numeroCuota) {
        return numeroAcuerdo == null || numeroCuota == null ? null
                : numeroAcuerdo + "-" + String.format("%02d", numeroCuota);
    }
}
