package co.livorno.controlstock.comun.exportacion;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Convierte un {@link DocumentoTabular} en un PDF tamaño carta horizontal (ADR-011).
 *
 * <p>Formato numérico igual al de la interfaz (es-CO): miles con punto y decimales con coma
 * ({@code 1.234.567,50}). Las secciones marcadas "solo Excel" se omiten con una nota. Una sección con
 * {@link SeccionTabular#imagenPdf()} inserta esa imagen antes de la tabla (punto de extensión para gráficos).
 */
@Component
public class GeneradorPdf {

    static final String ENCABEZADO = "Livorno Clothing — Control Stock";
    static final String NOTA_SOLO_EXCEL = "El detalle está disponible en Excel.";
    static final String MENSAJE_SIN_DATOS = "No hay datos para los filtros seleccionados.";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DecimalFormatSymbols SIMBOLOS = simbolos();
    private static final Color GRIS_ENCABEZADO = new Color(0xE5, 0xE7, 0xEB);
    private static final Color GRIS_TOTAL = new Color(0xF3, 0xF4, 0xF6);

    public byte[] generar(DocumentoTabular doc) {
        Document pdf = new Document(PageSize.LETTER.rotate(), 36, 36, 36, 48);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            PdfWriter escritor = PdfWriter.getInstance(pdf, salida);
            escritor.setPageEvent(new NumeroDePagina());
            pdf.addTitle(doc.titulo());
            pdf.addAuthor("Livorno Clothing — Control Stock");
            pdf.open();
            Fuentes f = new Fuentes();
            escribirEncabezado(pdf, doc, f);
            for (SeccionTabular seccion : doc.secciones()) {
                escribirSeccion(pdf, seccion, f);
            }
            escribirNotas(pdf, doc.notas(), f);
            pdf.close();
            return salida.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el archivo PDF", e);
        }
    }

    private void escribirEncabezado(Document pdf, DocumentoTabular doc, Fuentes f) throws DocumentException {
        pdf.add(new Paragraph(ENCABEZADO, f.marca));
        Paragraph titulo = new Paragraph(doc.titulo(), f.titulo);
        titulo.setSpacingBefore(4);
        pdf.add(titulo);
        for (FiltroReporte filtro : doc.filtros()) {
            Phrase linea = new Phrase();
            linea.add(new com.lowagie.text.Chunk(filtro.etiqueta() + ": ", f.negrita));
            linea.add(new com.lowagie.text.Chunk(filtro.valor(), f.normal));
            pdf.add(new Paragraph(linea));
        }
        Paragraph generado = new Paragraph(
                "Generado por " + doc.generadoPor() + " el " + doc.generadoEn().format(FECHA_HORA), f.menor);
        generado.setSpacingBefore(4);
        generado.setSpacingAfter(10);
        pdf.add(generado);
    }

    private void escribirSeccion(Document pdf, SeccionTabular seccion, Fuentes f) throws DocumentException {
        Paragraph nombre = new Paragraph(seccion.nombre(), f.subtitulo);
        nombre.setSpacingBefore(6);
        nombre.setSpacingAfter(4);
        nombre.setKeepTogether(true);
        pdf.add(nombre);

        if (seccion.soloExcel()) {
            pdf.add(new Paragraph(NOTA_SOLO_EXCEL, f.cursiva));
            return;
        }
        if (seccion.imagenPdf() != null) {
            pdf.add(imagen(pdf, seccion.imagenPdf()));
        }
        if (seccion.filas().isEmpty()) {
            String mensaje = seccion.mensaje() != null ? seccion.mensaje() : MENSAJE_SIN_DATOS;
            pdf.add(new Paragraph(mensaje, f.cursiva));
            return;
        }
        pdf.add(tabla(seccion, f));
    }

    private Image imagen(Document pdf, byte[] contenido) throws DocumentException {
        try {
            Image imagen = Image.getInstance(contenido);
            imagen.scaleToFit(pdf.right() - pdf.left(), 240);
            imagen.setAlignment(Element.ALIGN_CENTER);
            imagen.setSpacingAfter(8);
            return imagen;
        } catch (IOException e) {
            throw new UncheckedIOException("Imagen inválida para el PDF", e);
        }
    }

    private PdfPTable tabla(SeccionTabular seccion, Fuentes f) throws DocumentException {
        List<ColumnaReporte> columnas = seccion.columnas();
        PdfPTable tabla = new PdfPTable(columnas.size());
        tabla.setWidthPercentage(100);
        float[] anchos = new float[columnas.size()];
        for (int c = 0; c < anchos.length; c++) {
            anchos[c] = columnas.get(c).ancho();
        }
        tabla.setWidths(anchos);
        tabla.setHeaderRows(1);
        tabla.setSplitRows(false);

        for (ColumnaReporte columna : columnas) {
            PdfPCell celda = celda(columna.encabezado(), f.negrita, alineacion(columna.tipo()));
            celda.setBackgroundColor(GRIS_ENCABEZADO);
            tabla.addCell(celda);
        }
        for (List<Object> fila : seccion.filas()) {
            for (int c = 0; c < columnas.size(); c++) {
                Object valor = c < fila.size() ? fila.get(c) : null;
                tabla.addCell(celda(formatear(valor, columnas.get(c).tipo()), f.normal,
                        alineacion(columnas.get(c).tipo())));
            }
        }
        if (seccion.totales() != null) {
            for (int c = 0; c < columnas.size(); c++) {
                Object valor = seccion.totales().get(c);
                PdfPCell celda = celda(formatear(valor, columnas.get(c).tipo()), f.negrita,
                        alineacion(columnas.get(c).tipo()));
                celda.setBackgroundColor(GRIS_TOTAL);
                tabla.addCell(celda);
            }
        }
        return tabla;
    }

    private static PdfPCell celda(String texto, Font fuente, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(3);
        celda.setBorderColor(new Color(0xD1, 0xD5, 0xDB));
        return celda;
    }

    private static int alineacion(TipoColumna tipo) {
        return switch (tipo) {
            case ENTERO, DINERO, PORCENTAJE -> Element.ALIGN_RIGHT;
            default -> Element.ALIGN_LEFT;
        };
    }

    private void escribirNotas(Document pdf, List<String> notas, Fuentes f) throws DocumentException {
        if (notas.isEmpty()) {
            return;
        }
        Paragraph titulo = new Paragraph("Notas", f.negrita);
        titulo.setSpacingBefore(10);
        pdf.add(titulo);
        for (String nota : notas) {
            pdf.add(new Paragraph("• " + nota, f.menor));
        }
    }

    /** Formato es-CO: {@code 1.234.567,50}; porcentaje con un decimal; fechas ISO. */
    static String formatear(Object valor, TipoColumna tipo) {
        if (valor == null) {
            return "";
        }
        return switch (tipo) {
            case ENTERO -> new DecimalFormat("#,##0", SIMBOLOS).format(numero(valor).setScale(0, RoundingMode.HALF_UP));
            case DINERO -> new DecimalFormat("#,##0.00", SIMBOLOS).format(numero(valor).setScale(2, RoundingMode.HALF_UP));
            case PORCENTAJE ->
                new DecimalFormat("#,##0.0", SIMBOLOS).format(numero(valor).setScale(1, RoundingMode.HALF_UP)) + " %";
            case FECHA -> valor instanceof LocalDate d ? d.format(FECHA) : valor.toString();
            case FECHA_HORA -> valor instanceof LocalDateTime d ? d.format(FECHA_HORA) : valor.toString();
            case TEXTO -> valor.toString();
        };
    }

    private static BigDecimal numero(Object valor) {
        if (valor instanceof BigDecimal b) {
            return b;
        }
        if (valor instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        throw new IllegalArgumentException("Se esperaba un número y llegó " + valor.getClass().getSimpleName());
    }

    private static DecimalFormatSymbols simbolos() {
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.ROOT);
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        return s;
    }

    /** Fuentes Helvetica con codificación Cp1252 (tildes y eñes). */
    private static final class Fuentes {
        final Font marca = fuente(Font.BOLD, 9, new Color(0x6B, 0x72, 0x80));
        final Font titulo = fuente(Font.BOLD, 16, Color.BLACK);
        final Font subtitulo = fuente(Font.BOLD, 12, Color.BLACK);
        final Font normal = fuente(Font.NORMAL, 8.5f, Color.BLACK);
        final Font negrita = fuente(Font.BOLD, 8.5f, Color.BLACK);
        final Font cursiva = fuente(Font.ITALIC, 9.5f, new Color(0x4B, 0x55, 0x63));
        final Font menor = fuente(Font.NORMAL, 8.5f, new Color(0x4B, 0x55, 0x63));

        private static Font fuente(int estilo, float tamano, Color color) {
            String nombre = switch (estilo) {
                case Font.BOLD -> BaseFont.HELVETICA_BOLD;
                case Font.ITALIC -> BaseFont.HELVETICA_OBLIQUE;
                default -> BaseFont.HELVETICA;
            };
            try {
                return new Font(BaseFont.createFont(nombre, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), tamano,
                        Font.NORMAL, color);
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException("No se pudo cargar la fuente del PDF", e);
            }
        }
    }

    /** Pie "Página n de m": el total se escribe al cerrar el documento en una plantilla reservada. */
    private static final class NumeroDePagina extends PdfPageEventHelper {
        private static final float TAMANO = 8;
        private PdfTemplate total;
        private BaseFont base;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException(e);
            }
            total = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            String texto = "Página " + writer.getPageNumber() + " de ";
            float ancho = base.getWidthPoint(texto, TAMANO);
            float x = (document.right() + document.left()) / 2 - (ancho + 12) / 2;
            float y = document.bottom() - 22;
            cb.beginText();
            cb.setFontAndSize(base, TAMANO);
            cb.setTextMatrix(x, y);
            cb.showText(texto);
            cb.endText();
            cb.addTemplate(total, x + ancho, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(base, TAMANO);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}
