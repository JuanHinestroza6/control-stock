package co.livorno.controlstock.comun.exportacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class GeneradorExcelTest {

    private final ExportadorDocumentos exportador = new ExportadorDocumentos(new GeneradorExcel(), new GeneradorPdf());

    private static final List<ColumnaReporte> COLUMNAS = List.of(
            ColumnaReporte.texto("Producto", 24),
            ColumnaReporte.entero("Unidades", 10),
            ColumnaReporte.dinero("Ingreso", 14),
            ColumnaReporte.porcentaje("Margen %", 10),
            ColumnaReporte.fecha("Fecha", 12),
            ColumnaReporte.fechaHora("Hora", 18));

    private static List<Object> fila(Object... valores) {
        return new ArrayList<>(Arrays.asList(valores));
    }

    private static DocumentoTabular documento(SeccionTabular... secciones) {
        return new DocumentoTabular("Ventas del periodo",
                List.of(new FiltroReporte("Desde", "2026-01-01"), new FiltroReporte("Hasta", "2026-01-31")),
                "Ana Admin", LocalDateTime.of(2026, 2, 1, 9, 30), List.of(secciones),
                List.of("Solo ventas registradas."));
    }

    private Workbook leer(ArchivoExportado archivo) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(archivo.contenido()));
    }

    @Test
    void exportar_xlsx_devuelveTipoYNombreCorrectos() {
        // dado
        DocumentoTabular doc = documento(SeccionTabular.de("Ventas", COLUMNAS, List.of()));
        // cuando
        ArchivoExportado archivo = exportador.exportar(doc, FormatoExportacion.XLSX, "ventas-2026-01");
        // entonces
        assertThat(archivo.nombre()).isEqualTo("ventas-2026-01.xlsx");
        assertThat(archivo.tipoContenido())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(archivo.contenido()).isNotEmpty();
    }

    @Test
    void generar_conDatos_escribeCeldasNumericasConFormatoYTotales() throws IOException {
        // dado
        SeccionTabular seccion = SeccionTabular.de("Ventas", COLUMNAS, List.of(
                fila("Camiseta niño MARRÓN", 3, new BigDecimal("1234567.50"), new BigDecimal("12.34"),
                        LocalDate.of(2026, 1, 15), LocalDateTime.of(2026, 1, 15, 14, 5)),
                fila("Jean", 2, new BigDecimal("80000.00"), null, LocalDate.of(2026, 1, 16), null)))
                .conTotales(fila("Total", 5, new BigDecimal("1314567.50"), null, null, null));
        // cuando
        try (Workbook libro = leer(exportador.exportar(documento(seccion), FormatoExportacion.XLSX, "x"))) {
            Sheet hoja = libro.getSheet("Ventas");
            // entonces: encabezado
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Producto");
            // texto con tildes
            assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Camiseta niño MARRÓN");
            // entero
            Cell entero = hoja.getRow(1).getCell(1);
            assertThat(entero.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(entero.getNumericCellValue()).isEqualTo(3.0);
            assertThat(entero.getCellStyle().getDataFormatString()).isEqualTo("#,##0");
            // dinero
            Cell dinero = hoja.getRow(1).getCell(2);
            assertThat(dinero.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(dinero.getNumericCellValue()).isEqualTo(1234567.50);
            assertThat(dinero.getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
            // porcentaje
            Cell pct = hoja.getRow(1).getCell(3);
            assertThat(pct.getNumericCellValue()).isEqualTo(12.34);
            assertThat(pct.getCellStyle().getDataFormatString()).isEqualTo("0.0");
            // fecha y fecha-hora son fechas reales
            Cell fecha = hoja.getRow(1).getCell(4);
            assertThat(DateUtil.isCellDateFormatted(fecha)).isTrue();
            assertThat(fecha.getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 1, 15, 0, 0));
            assertThat(fecha.getCellStyle().getDataFormatString()).isEqualTo("yyyy-mm-dd");
            Cell hora = hoja.getRow(1).getCell(5);
            assertThat(hora.getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 1, 15, 14, 5));
            assertThat(hora.getCellStyle().getDataFormatString()).isEqualTo("yyyy-mm-dd hh:mm");
            // nulos quedan en blanco
            assertThat(hoja.getRow(2).getCell(3).getCellType()).isEqualTo(CellType.BLANK);
            // totales en negrita, numéricos, tras los datos
            Cell total = hoja.getRow(3).getCell(2);
            assertThat(total.getNumericCellValue()).isEqualTo(1314567.50);
            assertThat(libro.getFontAt(total.getCellStyle().getFontIndex()).getBold()).isTrue();
            assertThat(hoja.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Total");
        }
    }

    @Test
    void generar_cualquierSeccion_fijaPrimeraFilaYPoneAutofiltro() throws IOException {
        // dado
        SeccionTabular seccion = SeccionTabular.de("Ventas", COLUMNAS, List.of(
                fila("A", 1, BigDecimal.ONE, null, null, null)));
        // cuando
        try (Workbook libro = leer(exportador.exportar(documento(seccion), FormatoExportacion.XLSX, "x"))) {
            XSSFSheet hoja = (XSSFSheet) libro.getSheet("Ventas");
            // entonces
            assertThat(hoja.getPaneInformation()).isNotNull();
            assertThat(hoja.getPaneInformation().isFreezePane()).isTrue();
            assertThat(hoja.getPaneInformation().getHorizontalSplitTopRow()).isEqualTo((short) 1);
            assertThat(hoja.getCTWorksheet().isSetAutoFilter()).isTrue();
            assertThat(hoja.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:F2");
            assertThat(hoja.getColumnWidth(0)).isEqualTo(26 * 256);
        }
    }

    @Test
    void generar_hojaNotas_incluyeFiltrosGeneradoPorYNotas() throws IOException {
        // dado
        DocumentoTabular doc = documento(SeccionTabular.de("Ventas", COLUMNAS, List.of()));
        // cuando
        try (Workbook libro = leer(exportador.exportar(doc, FormatoExportacion.XLSX, "x"))) {
            Sheet notas = libro.getSheet("Notas");
            // entonces
            List<String> textos = new ArrayList<>();
            notas.forEach(r -> r.forEach(c -> textos.add(c.getStringCellValue())));
            assertThat(textos).contains("Ventas del periodo", "Desde", "2026-01-01", "Hasta", "2026-01-31",
                    "Generado por", "Ana Admin", "Generado el", "2026-02-01 09:30", "Solo ventas registradas.");
            assertThat(libro.getSheetName(libro.getNumberOfSheets() - 1)).isEqualTo("Notas");
        }
    }

    @Test
    void generar_seccionVaciaConMensaje_escribeElMensajeDebajoDelEncabezado() throws IOException {
        // dado
        SeccionTabular seccion = SeccionTabular.de("Ventas", COLUMNAS, List.of())
                .conMensaje("No hay ventas en el periodo");
        // cuando
        try (Workbook libro = leer(exportador.exportar(documento(seccion), FormatoExportacion.XLSX, "x"))) {
            Sheet hoja = libro.getSheet("Ventas");
            // entonces
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Producto");
            assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).isEqualTo("No hay ventas en el periodo");
        }
    }

    @Test
    void generar_variasSeccionesConNombresRepetidosOLargos_producenHojasValidasYUnicas() throws IOException {
        // dado
        String largo = "Detalle de ventas por producto y variante del periodo";
        DocumentoTabular doc = documento(
                SeccionTabular.de(largo, COLUMNAS, List.of()),
                SeccionTabular.de(largo, COLUMNAS, List.of()).soloEnExcel(),
                SeccionTabular.de("Notas", COLUMNAS, List.of()));
        // cuando
        try (Workbook libro = leer(exportador.exportar(doc, FormatoExportacion.XLSX, "x"))) {
            // entonces
            List<String> nombres = new ArrayList<>();
            for (int i = 0; i < libro.getNumberOfSheets(); i++) {
                nombres.add(libro.getSheetName(i));
            }
            assertThat(nombres).hasSize(4).doesNotHaveDuplicates()
                    .allSatisfy(n -> assertThat(n.length()).isLessThanOrEqualTo(31));
            assertThat(nombres.get(3)).isEqualTo("Notas");
        }
    }

    @Test
    void generar_masDeUmbralDeFilas_usaStreamingYConservaLosDatos() throws IOException {
        // dado
        List<List<Object>> filas = new ArrayList<>();
        for (int i = 0; i < GeneradorExcel.UMBRAL_STREAMING + 10; i++) {
            filas.add(fila("P" + i, i, new BigDecimal("10.50"), null, null, null));
        }
        // cuando
        try (Workbook libro = leer(exportador.exportar(
                documento(SeccionTabular.de("Ventas", COLUMNAS, filas)), FormatoExportacion.XLSX, "x"))) {
            Sheet hoja = libro.getSheet("Ventas");
            // entonces
            assertThat(hoja.getLastRowNum()).isEqualTo(GeneradorExcel.UMBRAL_STREAMING + 10);
            assertThat(hoja.getRow(5000).getCell(1).getNumericCellValue()).isEqualTo(4999.0);
        }
    }
}
