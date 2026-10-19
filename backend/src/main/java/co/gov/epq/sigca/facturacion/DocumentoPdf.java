package co.gov.epq.sigca.facturacion;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

/** Utilidades comunes para los PDF de facturas y comprobantes (OpenPDF). */
public final class DocumentoPdf {

    private static final Locale CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static final Font TITULO = new Font(Font.HELVETICA, 14, Font.BOLD);
    public static final Font SUBTITULO = new Font(Font.HELVETICA, 11, Font.BOLD);
    public static final Font NORMAL = new Font(Font.HELVETICA, 9);
    public static final Font NEGRITA = new Font(Font.HELVETICA, 9, Font.BOLD);
    public static final Font PEQUENA = new Font(Font.HELVETICA, 7);
    public static final Font ALERTA = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(170, 30, 30));
    private static final Color GRIS = new Color(230, 233, 237);

    private DocumentoPdf() {
    }

    /** Crea un documento carta, lo llena y devuelve los bytes. */
    public static byte[] generar(String titulo, Consumer<Document> contenido) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, salida);
        doc.addTitle(titulo);
        doc.addAuthor("Empresas Públicas del Quindío S.A. E.S.P.");
        doc.open();
        Paragraph enc = new Paragraph("Empresas Públicas del Quindío S.A. E.S.P.", SUBTITULO);
        doc.add(enc);
        doc.add(new Paragraph("SIGCA-EPQ · Documento generado por el sistema comercial", PEQUENA));
        doc.add(new Paragraph(titulo, TITULO));
        doc.add(new Paragraph(" ", PEQUENA));
        contenido.accept(doc);
        doc.close();
        return salida.toByteArray();
    }

    /** Tabla de dos columnas etiqueta/valor. */
    public static PdfPTable datos(String... pares) {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        for (int i = 0; i + 1 < pares.length; i += 2) {
            t.addCell(celda(pares[i], NEGRITA, false));
            t.addCell(celda(pares[i + 1] == null ? "—" : pares[i + 1], NORMAL, false));
        }
        if ((pares.length / 2) % 2 != 0) {
            t.addCell(celda("", NORMAL, false));
            t.addCell(celda("", NORMAL, false));
        }
        return t;
    }

    public static PdfPCell encabezado(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, NEGRITA));
        c.setBackgroundColor(GRIS);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        return c;
    }

    public static PdfPCell celda(String texto, Font fuente, boolean numero) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fuente));
        c.setBorderColor(GRIS);
        c.setPadding(3);
        if (numero) {
            c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        }
        return c;
    }

    public static String pesos(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(CO).format(v == null ? BigDecimal.ZERO : v);
    }

    public static String numero(BigDecimal v) {
        if (v == null) {
            return "—";
        }
        NumberFormat f = NumberFormat.getNumberInstance(CO);
        f.setMaximumFractionDigits(3);
        return f.format(v);
    }

    public static String fecha(LocalDate d) {
        return d == null ? "—" : FECHA.format(d);
    }

    public static String fechaHora(LocalDateTime d) {
        return d == null ? "—" : FECHA_HORA.format(d);
    }
}
