package co.gov.epq.sigca.pagos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import co.gov.epq.sigca.facturacion.EstadoSyncErp;

/** Pago registrado, con la obligación que saldó (factura o cuota de acuerdo). */
public record PagoResumen(Long id, String codigoPago, LocalDateTime fechaPago, BigDecimal valor, CanalPago canal,
        TipoObligacion tipoObligacion, String referencia, String codigoUsuario, String titular, String codigoTransaccion,
        String referenciaBanco, EstadoSyncErp estadoSyncErp) {

    /** Constructor de la proyección JPQL. */
    public PagoResumen(Long id, String codigoPago, LocalDateTime fechaPago, BigDecimal valor, CanalPago canal,
            String numeroFactura, String numeroAcuerdo, Integer numeroCuota, String codigoUsuario, String titular,
            String codigoTransaccion, String referenciaBanco, EstadoSyncErp estadoSyncErp) {
        this(id, codigoPago, fechaPago, valor, canal, numeroFactura != null ? TipoObligacion.FACTURA : TipoObligacion.CUOTA,
                numeroFactura != null ? numeroFactura : Obligacion.referenciaCuota(numeroAcuerdo, numeroCuota),
                codigoUsuario, titular, codigoTransaccion, referenciaBanco, estadoSyncErp);
    }
}
