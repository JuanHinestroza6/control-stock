package co.livorno.controlstock.comun.exportacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

class GeneradorPdfTest {

    private final ExportadorDocumentos exportador = new ExportadorDocumentos(new GeneradorExcel(), new GeneradorPdf());

    private static final List<ColumnaReporte> COLUMNAS = List.of(
            ColumnaReporte.texto("Producto", 30),
            ColumnaReporte.entero("Unidades", 10),
            ColumnaReporte.dinero("Ingreso", 16),
            ColumnaReporte.porcentaje("Margen", 10),
            ColumnaReporte.fecha("Fecha", 12));

    private static List<Object> fila(Object... valores) {
        return new ArrayList<>(Arrays.asList(valores));
    }

    private static DocumentoTabular documento(List<String> notas, SeccionTabular... secciones) {
        return new DocumentoTabular("Ventas del periodo",
                List.of(new FiltroReporte("Categoría", "Camisetas niño")),
                "Ana Admin", LocalDateTime.of(2026, 2, 1, 9, 30), List.of(secciones), notas);
    }

    private ArchivoExportado pdf(DocumentoTabular doc) {
        return exportador.exportar(doc, FormatoExportacion.PDF, "reporte");
    }

    private static String texto(ArchivoExportado archivo) throws IOException {
        PdfReader lector = new PdfReader(archivo.contenido());
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            StringBuilder sb = new StringBuilder();
            for (int p = 1; p <= lector.getNumberOfPages(); p++) {
                sb.append(extractor.getTextFromPage(p)).append('\n');
            }
            return sb.toString();
        } finally {
            lector.close();
        }
    }

    @Test
    void exportar_pdf_devuelveTipoNombreYCabeceraPdf() {
        // dado / cuando
        ArchivoExportado archivo = pdf(documento(List.of(), SeccionTabular.de("Ventas", COLUMNAS, List.of())));
        // entonces
        assertThat(archivo.tipoContenido()).isEqualTo("application/pdf");
        assertThat(archivo.nombre()).isEqualTo("reporte.pdf");
        assertThat(new String(archivo.contenido(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF-");
    }

    @Test
    void generar_conDatos_muestraEncabezadoTituloFiltrosTildesYTotales() throws IOException {
        // dado
        SeccionTabular seccion = SeccionTabular.de("Ventas por producto", COLUMNAS, List.of(
                fila("MARRÓN oversize", 3, new BigDecimal("1234567.5"), new BigDecimal("12.34"),
                        LocalDate.of(2026, 1, 15)),
                fila("Camisetas niño", 12, new BigDecimal("80000"), null, null)))
                .conTotales(fila("Total", 15, new BigDecimal("1314567.50"), null, null));
        // cuando
        ArchivoExportado archivo = pdf(documento(List.of("Solo ventas registradas."), seccion));
        String t = texto(archivo);
        // entonces
        assertThat(t).contains("Livorno Clothing — Control Stock")
                .contains("Ventas del periodo")
                .contains("Categoría: Camisetas niño")
                .contains("Generado por Ana Admin el 2026-02-01 09:30")
                .contains("Ventas por producto")
                .contains("MARRÓN oversize")
                .contains("1.234.567,50")
                .contains("80.000,00")
                .contains("12,3 %")
                .contains("2026-01-15")
                .contains("Total")
                .contains("1.314.567,50")
                .contains("Notas")
                .contains("Solo ventas registradas.");
    }

    @Test
    void generar_cualquierDocumento_usaCartaHorizontal() throws IOException {
        // dado / cuando
        ArchivoExportado archivo = pdf(documento(List.of(), SeccionTabular.de("Ventas", COLUMNAS, List.of())));
        // entonces
        PdfReader lector = new PdfReader(archivo.contenido());
        try {
            Rectangle pagina = lector.getPageSizeWithRotation(1);
            assertThat(pagina.getWidth()).isEqualTo(792f);
            assertThat(pagina.getHeight()).isEqualTo(612f);
        } finally {
            lector.close();
        }
    }

    @Test
    void generar_variasPaginas_numeraPaginasYRepiteEncabezadosDeTabla() throws IOException {
        // dado
        List<List<Object>> filas = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            filas.add(fila("Producto " + i, i, new BigDecimal("1000.00"), null, null));
        }
        // cuando
        ArchivoExportado archivo = pdf(documento(List.of(), SeccionTabular.de("Ventas", COLUMNAS, filas)));
        // entonces
        PdfReader lector = new PdfReader(archivo.contenido());
        int paginas;
        try {
            paginas = lector.getNumberOfPages();
        } finally {
            lector.close();
        }
        assertThat(paginas).isGreaterThan(2);
        String t = texto(archivo);
        // el extractor separa la plantilla del total con un salto de línea
        assertThat(t).containsPattern("Página 1 de\\s+" + paginas)
                .containsPattern("Página " + paginas + " de\\s+" + paginas);
        Matcher m = Pattern.compile("Producto Unidades Ingreso").matcher(t);
        int encabezados = 0;
        while (m.find()) {
            encabezados++;
        }
        assertThat(encabezados).isEqualTo(paginas);
    }

    @Test
    void generar_seccionSinFilas_muestraMensajeOElPredeterminado() throws IOException {
        // dado
        SeccionTabular conMensaje = SeccionTabular.de("Ventas", COLUMNAS, List.of())
                .conMensaje("No hay ventas en el periodo");
        SeccionTabular sinMensaje = SeccionTabular.de("Devoluciones", COLUMNAS, List.of());
        // cuando
        String t = texto(pdf(documento(List.of(), conMensaje, sinMensaje)));
        // entonces
        assertThat(t).contains("No hay ventas en el periodo")
                .contains("No hay datos para los filtros seleccionados.");
    }

    @Test
    void generar_seccionSoloExcel_laOmiteConNota() throws IOException {
        // dado
        SeccionTabular detalle = SeccionTabular.de("Detalle de ventas", COLUMNAS, List.of(
                fila("SECRETO-DETALLE", 1, BigDecimal.ONE, null, null))).soloEnExcel();
        // cuando
        String t = texto(pdf(documento(List.of(), detalle)));
        // entonces
        assertThat(t).contains("Detalle de ventas")
                .contains("El detalle está disponible en Excel.")
                .doesNotContain("SECRETO-DETALLE");
    }

    @Test
    void generar_seccionConImagen_laInsertaAntesDeLaTabla() throws IOException {
        // dado
        BufferedImage img = new BufferedImage(400, 150, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 400, 150);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        SeccionTabular seccion = SeccionTabular.de("Tendencia", COLUMNAS, List.of(
                fila("A", 1, BigDecimal.ONE, null, null))).conImagenPdf(png.toByteArray());
        // cuando
        ArchivoExportado conImagen = pdf(documento(List.of(), seccion));
        ArchivoExportado sinImagen = pdf(documento(List.of(), SeccionTabular.de("Tendencia", COLUMNAS,
                List.of(fila("A", 1, BigDecimal.ONE, null, null)))));
        // entonces
        assertThat(conImagen.contenido().length).isGreaterThan(sinImagen.contenido().length);
        assertThat(texto(conImagen)).contains("Tendencia");
    }

    @Test
    void formatear_valores_usaFormatoColombiano() {
        assertThat(GeneradorPdf.formatear(new BigDecimal("0.005"), TipoColumna.DINERO)).isEqualTo("0,01");
        assertThat(GeneradorPdf.formatear(1234567, TipoColumna.ENTERO)).isEqualTo("1.234.567");
        assertThat(GeneradorPdf.formatear(null, TipoColumna.DINERO)).isEmpty();
        assertThat(GeneradorPdf.formatear(new BigDecimal("-1500.5"), TipoColumna.DINERO)).isEqualTo("-1.500,50");
    }
}
