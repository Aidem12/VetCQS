package mx.buap.veterinaria.reportes;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

/** Pequeño ayudante sobre OpenPDF para documentos con encabezado, secciones y tablas. */
public final class Pdf implements AutoCloseable {
    private static final Color MARCA = new Color(23, 50, 77);
    private static final Color GRIS = new Color(107, 124, 143);
    private static final Color FONDO_ENCABEZADO = new Color(232, 240, 247);
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, MARCA);
    private static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, MARCA);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, Color.BLACK);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Color.BLACK);
    private static final Font PEQUENA = FontFactory.getFont(FontFactory.HELVETICA, 8, GRIS);
    private static final Font ENCABEZADO_TABLA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, MARCA);

    private final ByteArrayOutputStream salida = new ByteArrayOutputStream();
    private final Document documento;

    public Pdf(boolean horizontal) {
        documento = new Document(horizontal ? PageSize.LETTER.rotate() : PageSize.LETTER, 40, 40, 40, 40);
        PdfWriter.getInstance(documento, salida);
        documento.open();
    }

    public Pdf encabezado(String negocio, String titulo, String folio, String fecha, String responsable) {
        agregar(new Paragraph(negocio, PEQUENA));
        agregar(new Paragraph(titulo, TITULO));
        StringBuilder datos = new StringBuilder();
        if (folio != null) datos.append("Folio: ").append(folio).append("   ");
        if (fecha != null) datos.append("Generado: ").append(fecha).append("   ");
        if (responsable != null) datos.append("Responsable: ").append(responsable);
        agregar(new Paragraph(datos.toString(), PEQUENA));
        linea();
        return this;
    }
    public Pdf seccion(String texto) {
        Paragraph p = new Paragraph(texto, SUBTITULO);
        p.setSpacingBefore(10); p.setSpacingAfter(4);
        return agregar(p);
    }
    public Pdf texto(String texto) { return agregar(new Paragraph(texto == null ? "" : texto, NORMAL)); }
    public Pdf nota(String texto) { return agregar(new Paragraph(texto, PEQUENA)); }
    public Pdf campo(String etiqueta, String valor) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(etiqueta + ": ", NEGRITA));
        p.add(new Chunk(valor == null || valor.isBlank() ? "—" : valor, NORMAL));
        return agregar(p);
    }
    public Pdf tabla(List<String> encabezados, List<List<String>> filas, float[] anchos) {
        PdfPTable t = new PdfPTable(encabezados.size());
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        t.setHeaderRows(1);
        if (anchos != null) t.setWidths(anchos);
        for (String e : encabezados) {
            PdfPCell c = new PdfPCell(new Phrase(e, ENCABEZADO_TABLA));
            c.setBackgroundColor(FONDO_ENCABEZADO); c.setPadding(4); c.setBorderColor(new Color(215, 224, 232));
            t.addCell(c);
        }
        if (filas.isEmpty()) {
            PdfPCell c = new PdfPCell(new Phrase("Sin registros", PEQUENA));
            c.setColspan(encabezados.size()); c.setPadding(4); c.setBorderColor(new Color(215, 224, 232));
            t.addCell(c);
        }
        for (List<String> fila : filas) {
            for (String valor : fila) {
                PdfPCell c = new PdfPCell(new Phrase(valor == null ? "" : valor, NORMAL));
                c.setPadding(4); c.setBorderColor(new Color(215, 224, 232));
                t.addCell(c);
            }
        }
        return agregar(t);
    }
    public Pdf firma(String nombre) {
        Paragraph espacio = new Paragraph(" ");
        espacio.setSpacingBefore(30);
        agregar(espacio);
        agregar(new Paragraph("______________________________", NORMAL));
        return agregar(new Paragraph(nombre, NORMAL));
    }
    private void linea() {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase(" "));
        c.setBorder(Rectangle.BOTTOM); c.setBorderColor(MARCA); c.setBorderWidth(1.5f); c.setFixedHeight(4);
        t.addCell(c);
        agregar(t);
    }
    private Pdf agregar(Element e) {
        try { documento.add(e); } catch (DocumentException ex) { throw new IllegalStateException(ex); }
        return this;
    }
    public byte[] bytes() {
        documento.close();
        return salida.toByteArray();
    }
    @Override public void close() { if (documento.isOpen()) documento.close(); }
}
