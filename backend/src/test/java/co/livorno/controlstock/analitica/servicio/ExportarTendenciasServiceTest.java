package co.livorno.controlstock.analitica.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.analitica.dto.PuntoTendencia;
import co.livorno.controlstock.analitica.repositorio.ConsultasTendencias;
import co.livorno.controlstock.analitica.repositorio.FilaTendencia;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.exportacion.ArchivoExportado;
import co.livorno.controlstock.comun.exportacion.ExportadorDocumentos;
import co.livorno.controlstock.comun.exportacion.FormatoExportacion;
import co.livorno.controlstock.comun.exportacion.GeneradorExcel;
import co.livorno.controlstock.comun.exportacion.GeneradorPdf;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exportación de tendencias (B7, fase 7) sin base de datos: la consulta se simula y los generadores son los reales. */
class ExportarTendenciasServiceTest {

    // 2026-10-14 en Bogotá: el último mes completo es 2026-09
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T02:00:00Z"), ZoneId.of("America/Bogota"));
    private final ConsultasTendencias consultas = mock(ConsultasTendencias.class);
    private final ExportarTendenciasService servicio = new ExportarTendenciasService(
            new TendenciasService(consultas, clock), consultas,
            new ExportadorDocumentos(new GeneradorExcel(), new GeneradorPdf()), clock);

    private static FilaTendencia fila(String mes, long unidades, String ingreso) {
        return new FilaTendencia(YearMonth.parse(mes), unidades, new BigDecimal(ingreso));
    }

    @BeforeEach
    void nombres() {
        when(consultas.nombreCategoria(any())).thenReturn("Camisetas");
    }

    private void conDatos() {
        when(consultas.porMes(any(), any(), any(), any())).thenReturn(List.of(
                fila("2025-07", 10, "1000.00"), fila("2025-08", 20, "2000.00"),
                fila("2026-07", 30, "3000.00"), fila("2026-08", 5, "500.00"), fila("2026-09", 15, "1500.50")));
    }

    private static String textoPdf(byte[] pdf) throws IOException {
        PdfReader lector = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            StringBuilder sb = new StringBuilder();
            for (int p = 1; p <= lector.getNumberOfPages(); p++) {
                sb.append(extractor.getTextFromPage(p)).append(' ');
            }
            return sb.toString().replaceAll("\\s+", " ");
        } finally {
            lector.close();
        }
    }

    private static boolean tieneImagen(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1).matches("(?s).*/Subtype\\s*/Image.*");
    }

    @Test
    void exportar_pdfConDatos_incluyeResumenCalculadoConTildesYGrafico() throws IOException {
        // dado: julio–septiembre de 2026 contra julio–agosto de 2025
        conDatos();

        // cuando
        ArchivoExportado a = servicio.exportar("2026-07", "2026-09", 2L, null, FormatoExportacion.PDF, "María Núñez");

        // entonces: total 5.000,50 contra 3.000,00 = +66,7 %; mayor julio, menor agosto
        String texto = textoPdf(a.contenido());
        assertThat(a.tipoContenido()).isEqualTo(ExportadorDocumentos.TIPO_PDF);
        assertThat(a.nombre()).isEqualTo("tendencias_2026-07_2026-09.pdf");
        assertThat(texto).contains("Tendencias históricas de ventas").contains("María Núñez")
                .contains("Categoría: Camisetas")
                .contains("se vendieron 50 unidades con un ingreso neto de $5.000,50")
                .contains("el ingreso creció 66,7 %")
                .contains("El mes de mayor venta fue julio de 2026 ($3.000,00)")
                .contains("el de menor venta, agosto de 2026 ($500,00)");
        assertThat(tieneImagen(a.contenido())).isTrue();
    }

    @Test
    void exportar_excelConDatos_traeLaTablaMensualConTotales() throws IOException {
        conDatos();

        ArchivoExportado a = servicio.exportar("2026-07", "2026-09", null, null, FormatoExportacion.XLSX, "Ana");

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(a.contenido()))) {
            Sheet hoja = libro.getSheet("Tendencia mensual");
            assertThat(hoja).isNotNull();
            DataFormatter f = new DataFormatter();
            StringBuilder todo = new StringBuilder();
            hoja.forEach(fila -> fila.forEach(c -> todo.append(f.formatCellValue(c)).append('|')));
            assertThat(todo.toString()).contains("2026-07").contains("2026-08").contains("2026-09").contains("Total")
                    .contains("Ingreso neto");
            assertThat(libro.getSheet("Notas")).isNotNull();
        }
    }

    @Test
    void exportar_sinDatos_muestraMensajeSinResumenNiGrafico() throws IOException {
        when(consultas.porMes(any(), any(), any(), any())).thenReturn(List.of());

        ArchivoExportado a = servicio.exportar("2026-07", "2026-09", null, null, FormatoExportacion.PDF, "Ana");

        assertThat(textoPdf(a.contenido())).contains("No hay datos disponibles para el rango")
                .doesNotContain("Resumen:");
        assertThat(tieneImagen(a.contenido())).isFalse();
    }

    @Test
    void exportar_sinVentasElAnioAnterior_explicaQueNoHayVariacion() throws IOException {
        when(consultas.porMes(any(), any(), any(), any())).thenReturn(List.of(fila("2026-09", 15, "1500.00")));

        ArchivoExportado a = servicio.exportar("2026-09", "2026-09", null, null, FormatoExportacion.PDF, "Ana");

        assertThat(textoPdf(a.contenido())).contains("no tuvo ventas, por lo que no hay variación para comparar");
    }

    @Test
    void exportar_filtrosInvalidos_lanzaSolicitudInvalidaSinGenerarArchivo() {
        assertThatThrownBy(() -> servicio.exportar("2026-09", "2026-01", null, null, FormatoExportacion.PDF, "Ana"))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void escalaMaxima_redondeaHaciaArribaAUnaEscalaLegible() {
        var p = new PuntoTendencia("2026-01", 1, new BigDecimal("1234567.00"), 0, BigDecimal.ZERO, null, null);

        assertThat(GraficoTendencias.escalaMaxima(List.of(p))).isEqualTo(2_000_000d);
    }

    @Test
    void png_conUnSoloPunto_generaImagenValida() {
        var p = new PuntoTendencia("2026-01", 1, new BigDecimal("100.00"), 0, BigDecimal.ZERO, null, null);

        byte[] png = GraficoTendencias.png(List.of(p));

        assertThat(png).startsWith(0x89, 'P', 'N', 'G');
    }
}
