package co.gov.epq.sigca.facturacion;

import static co.gov.epq.sigca.facturacion.DocumentoPdf.celda;
import static co.gov.epq.sigca.facturacion.DocumentoPdf.datos;
import static co.gov.epq.sigca.facturacion.DocumentoPdf.encabezado;
import static co.gov.epq.sigca.facturacion.DocumentoPdf.numero;
import static co.gov.epq.sigca.facturacion.DocumentoPdf.pesos;

import java.math.BigDecimal;

import org.openpdf.text.Element;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPTable;
import org.springframework.stereotype.Component;

/**
 * PDF de la factura (RF-07): referencia de pago = número de factura (P-18), líneas por servicio con lecturas y
 * liquidación, totales, valor en reclamo y valor exigible (RN-33).
 */
@Component
public class FacturaPdf {

    public byte[] generar(FacturaDetalle f) {
        return DocumentoPdf.generar("Factura de servicios públicos " + f.numeroFactura(), doc -> {
            if (f.estado() == EstadoFactura.ANULADA) {
                doc.add(new Paragraph("FACTURA ANULADA: " + f.motivoAnulacion(), DocumentoPdf.ALERTA));
            }
            doc.add(datos(
                    "Referencia de pago", f.referenciaPago(),
                    "Periodo", String.valueOf(f.periodo()),
                    "Fecha de emisión", DocumentoPdf.fecha(f.fechaEmision()),
                    "Fecha de vencimiento", DocumentoPdf.fecha(f.fechaVencimiento()),
                    "Titular", f.titular() + " (" + f.codigoUsuario() + ")",
                    "Estado", f.estado().name(),
                    "Predio", f.codigoPredio() + " · " + f.direccion(),
                    "Municipio", f.municipio(),
                    "Uso", f.uso() + (f.estrato() != null ? " · estrato " + f.estrato() : ""),
                    "Reexpide a", f.reexpedicionDe()));
            doc.add(new Paragraph(" ", DocumentoPdf.PEQUENA));

            PdfPTable t = new PdfPTable(new float[] { 2.2f, 1.4f, 1.3f, 1.6f, 1.4f, 1.5f, 1.5f, 1.2f, 1.6f });
            t.setWidthPercentage(100);
            for (String h : new String[] { "Servicio / contrato", "Lect. ant. / act.", "Consumo m³", "Básico / compl. / sunt.",
                    "Cargo fijo", "Consumo", "Subsidio (−) / contrib. (+)", "Otros", "Total línea" }) {
                t.addCell(encabezado(h));
            }
            for (LineaDetalle l : f.lineas()) {
                String servicio = l.tipoServicio() + "\n" + l.numeroContrato()
                        + (Boolean.TRUE.equals(l.enReclamo()) ? "\nEN RECLAMO" : "")
                        + (l.estado() != EstadoLinea.PENDIENTE ? "\n" + l.estado() : "");
                t.addCell(celda(servicio, DocumentoPdf.NORMAL, false));
                t.addCell(celda(numero(l.lecturaAnterior()) + " / "
                        + (l.lecturaActual() == null ? "sin lectura" : numero(l.lecturaActual())), DocumentoPdf.NORMAL, true));
                t.addCell(celda(numero(l.consumo()) + (Boolean.TRUE.equals(l.consumoEstimado()) ? " *" : ""),
                        DocumentoPdf.NORMAL, true));
                t.addCell(celda(numero(l.consumoBasico()) + " / " + numero(l.consumoComplementario()) + " / "
                        + numero(l.consumoSuntuario()), DocumentoPdf.NORMAL, true));
                t.addCell(celda(pesos(l.cargoFijo()), DocumentoPdf.NORMAL, true));
                t.addCell(celda(pesos(l.valorConsumo()), DocumentoPdf.NORMAL, true));
                t.addCell(celda(pesos(l.subsidio().add(l.contribucion())), DocumentoPdf.NORMAL, true));
                t.addCell(celda(pesos(l.otrosCargos().add(l.interesesMora())), DocumentoPdf.NORMAL, true));
                t.addCell(celda(pesos(l.totalLinea()), DocumentoPdf.NEGRITA, true));
            }
            doc.add(t);
            if (f.consumoEstimado()) {
                doc.add(new Paragraph("* Consumo estimado con el promedio de los últimos periodos por no haberse podido "
                        + "tomar la lectura (Ley 142 art. 146, P-07).", DocumentoPdf.PEQUENA));
            }
            doc.add(new Paragraph(" ", DocumentoPdf.PEQUENA));

            PdfPTable totales = new PdfPTable(new float[] { 3f, 1.5f });
            totales.setWidthPercentage(50);
            totales.setHorizontalAlignment(Element.ALIGN_RIGHT);
            fila(totales, "Total facturado", f.totalAPagar());
            fila(totales, "Ajustes por reclamos", f.valorAjustes());
            fila(totales, "Pagado", f.valorPagado());
            fila(totales, "Saldo pendiente", f.saldoPendiente());
            fila(totales, "Valor en reclamo (no se cobra)", f.valorEnReclamo());
            totales.addCell(celda("VALOR A PAGAR", DocumentoPdf.SUBTITULO, false));
            totales.addCell(celda(pesos(f.valorExigible()), DocumentoPdf.SUBTITULO, true));
            doc.add(totales);
            doc.add(new Paragraph(" ", DocumentoPdf.PEQUENA));
            doc.add(new Paragraph("Pague con la referencia " + f.referenciaPago()
                    + " en el portal de EPQ (PSE o tarjeta), en nuestras oficinas o en los bancos autorizados. "
                    + "Acueducto y alcantarillado se pagan juntos; si una línea tiene un reclamo en trámite, se paga solo "
                    + "el valor no reclamado (Ley 142 art. 155).", DocumentoPdf.NORMAL));
        });
    }

    private static void fila(PdfPTable t, String etiqueta, BigDecimal valor) {
        t.addCell(celda(etiqueta, DocumentoPdf.NORMAL, false));
        t.addCell(celda(pesos(valor), DocumentoPdf.NORMAL, true));
    }
}
