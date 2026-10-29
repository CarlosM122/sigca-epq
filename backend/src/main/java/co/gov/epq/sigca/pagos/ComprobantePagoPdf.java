package co.gov.epq.sigca.pagos;

import org.springframework.stereotype.Component;

import co.gov.epq.sigca.facturacion.DocumentoPdf;

/**
 * PDF del comprobante electrónico de pago (SWR-08), como {@code FacturaPdf} para la factura. Solo maqueta: los datos
 * llegan ya resueltos en la {@link PagoVista} (incluido el estado real de la obligación después del pago).
 */
@Component
public class ComprobantePagoPdf {

    public byte[] generar(PagoVista v) {
        return DocumentoPdf.generar("Comprobante de pago " + v.codigoPago(), doc -> doc.add(DocumentoPdf.datos(
                "Comprobante", v.codigoPago(),
                "Fecha y hora", DocumentoPdf.fechaHora(v.fechaPago()),
                "Titular", v.titular() + " (" + v.codigoUsuario() + ")",
                "Referencia pagada", v.referencia(),
                "Tipo", v.tipoObligacion() == TipoObligacion.FACTURA ? "Factura" : "Cuota de acuerdo de pago",
                "Canal", v.canal().name(),
                "Valor pagado", DocumentoPdf.pesos(v.valor()),
                "Código de transacción", v.codigoTransaccion(),
                "Referencia bancaria", v.referenciaBanco(),
                "Estado de la obligación", v.estadoObligacion())));
    }
}
