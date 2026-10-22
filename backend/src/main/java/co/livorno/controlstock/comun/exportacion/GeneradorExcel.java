package co.livorno.controlstock.comun.exportacion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Convierte un {@link DocumentoTabular} en un libro .xlsx con celdas numéricas reales. */
@Component
public class GeneradorExcel {

    /** Desde este número de filas en alguna sección se usa SXSSF (streaming). */
    static final int UMBRAL_STREAMING = 5_000;
    static final String HOJA_NOTAS = "Notas";

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public byte[] generar(DocumentoTabular doc) {
        boolean grande = doc.secciones().stream().anyMatch(s -> s.filas().size() > UMBRAL_STREAMING);
        Workbook libro = grande ? new SXSSFWorkbook(200) : new XSSFWorkbook();
        try (libro; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            Set<String> nombres = new HashSet<>();
            nombres.add(HOJA_NOTAS.toLowerCase(Locale.ROOT));
            for (SeccionTabular seccion : doc.secciones()) {
                escribirSeccion(libro, estilos, seccion, nombreHoja(seccion.nombre(), nombres));
            }
            escribirNotas(libro, estilos, doc);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el archivo Excel", e);
        }
    }

    private void escribirSeccion(Workbook libro, Estilos estilos, SeccionTabular seccion, String nombreHoja) {
        Sheet hoja = libro.createSheet(nombreHoja);
        List<ColumnaReporte> columnas = seccion.columnas();
        Row encabezado = hoja.createRow(0);
        for (int c = 0; c < columnas.size(); c++) {
            Cell celda = encabezado.createCell(c);
            celda.setCellValue(columnas.get(c).encabezado());
            celda.setCellStyle(estilos.encabezado);
            hoja.setColumnWidth(c, Math.min(254, columnas.get(c).ancho() + 2) * 256);
        }
        hoja.createFreezePane(0, 1);

        int fila = 1;
        for (List<Object> valores : seccion.filas()) {
            escribirFila(hoja.createRow(fila++), columnas, valores, estilos, false);
        }
        if (!columnas.isEmpty()) {
            hoja.setAutoFilter(new CellRangeAddress(0, Math.max(0, fila - 1), 0, columnas.size() - 1));
        }
        if (seccion.filas().isEmpty() && seccion.mensaje() != null) {
            hoja.createRow(fila++).createCell(0).setCellValue(seccion.mensaje());
        }
        if (seccion.totales() != null) {
            escribirFila(hoja.createRow(fila), columnas, seccion.totales(), estilos, true);
        }
    }

    private void escribirFila(Row fila, List<ColumnaReporte> columnas, List<Object> valores, Estilos estilos,
            boolean total) {
        for (int c = 0; c < columnas.size(); c++) {
            Object valor = c < valores.size() ? valores.get(c) : null;
            Cell celda = fila.createCell(c);
            TipoColumna tipo = columnas.get(c).tipo();
            celda.setCellStyle(total ? estilos.total(tipo) : estilos.dato(tipo));
            if (valor == null) {
                continue;
            }
            switch (tipo) {
                case ENTERO, DINERO, PORCENTAJE -> celda.setCellValue(aDouble(valor));
                case FECHA -> {
                    if (valor instanceof LocalDate f) {
                        celda.setCellValue(f);
                    } else {
                        celda.setCellValue(valor.toString());
                    }
                }
                case FECHA_HORA -> {
                    if (valor instanceof LocalDateTime f) {
                        celda.setCellValue(f);
                    } else {
                        celda.setCellValue(valor.toString());
                    }
                }
                default -> celda.setCellValue(valor.toString());
            }
        }
    }

    private static double aDouble(Object valor) {
        if (valor instanceof BigDecimal b) {
            return b.doubleValue();
        }
        if (valor instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("Se esperaba un número y llegó " + valor.getClass().getSimpleName());
    }

    private void escribirNotas(Workbook libro, Estilos estilos, DocumentoTabular doc) {
        Sheet hoja = libro.createSheet(HOJA_NOTAS);
        hoja.setColumnWidth(0, 28 * 256);
        hoja.setColumnWidth(1, 80 * 256);
        int f = 0;
        Row titulo = hoja.createRow(f++);
        titulo.createCell(0).setCellValue("Livorno Clothing — Control Stock");
        titulo.getCell(0).setCellStyle(estilos.negrita);
        hoja.createRow(f++).createCell(0).setCellValue(doc.titulo());
        f++;
        for (FiltroReporte filtro : doc.filtros()) {
            Row r = hoja.createRow(f++);
            r.createCell(0).setCellValue(filtro.etiqueta());
            r.createCell(1).setCellValue(filtro.valor());
        }
        Row por = hoja.createRow(f++);
        por.createCell(0).setCellValue("Generado por");
        por.createCell(1).setCellValue(doc.generadoPor());
        Row en = hoja.createRow(f++);
        en.createCell(0).setCellValue("Generado el");
        en.createCell(1).setCellValue(doc.generadoEn().format(FORMATO_FECHA_HORA));
        if (!doc.notas().isEmpty()) {
            f++;
            Row cab = hoja.createRow(f++);
            cab.createCell(0).setCellValue("Notas");
            cab.getCell(0).setCellStyle(estilos.negrita);
            for (String nota : doc.notas()) {
                hoja.createRow(f++).createCell(0).setCellValue(nota);
            }
        }
    }

    /** Nombre de hoja válido (máx. 31, sin caracteres prohibidos) y único sin distinguir mayúsculas. */
    private static String nombreHoja(String nombre, Set<String> usados) {
        String base = nombre.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (base.isEmpty()) {
            base = "Datos";
        }
        if (base.length() > 31) {
            base = base.substring(0, 31).trim();
        }
        String candidato = base;
        int n = 2;
        while (!usados.add(candidato.toLowerCase(Locale.ROOT))) {
            String sufijo = " " + n++;
            candidato = base.substring(0, Math.min(base.length(), 31 - sufijo.length())) + sufijo;
        }
        return candidato;
    }

    /** Estilos del libro (se crean una sola vez: Excel limita el total de estilos). */
    private static final class Estilos {
        private final Workbook libro;
        private final CreationHelper ayuda;
        final CellStyle encabezado;
        final CellStyle negrita;
        private final CellStyle[] datos = new CellStyle[TipoColumna.values().length];
        private final CellStyle[] totales = new CellStyle[TipoColumna.values().length];

        Estilos(Workbook libro) {
            this.libro = libro;
            this.ayuda = libro.getCreationHelper();
            Font fuenteNegrita = libro.createFont();
            fuenteNegrita.setBold(true);
            encabezado = libro.createCellStyle();
            encabezado.setFont(fuenteNegrita);
            encabezado.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            negrita = libro.createCellStyle();
            negrita.setFont(fuenteNegrita);
            for (TipoColumna tipo : TipoColumna.values()) {
                datos[tipo.ordinal()] = estilo(tipo, null);
                totales[tipo.ordinal()] = estilo(tipo, fuenteNegrita);
            }
        }

        private CellStyle estilo(TipoColumna tipo, Font fuente) {
            CellStyle estilo = libro.createCellStyle();
            String formato = switch (tipo) {
                case ENTERO -> "#,##0";
                case DINERO -> "#,##0.00";
                case PORCENTAJE -> "0.0";
                case FECHA -> "yyyy-mm-dd";
                case FECHA_HORA -> "yyyy-mm-dd hh:mm";
                case TEXTO -> null;
            };
            if (formato != null) {
                estilo.setDataFormat(ayuda.createDataFormat().getFormat(formato));
            }
            if (fuente != null) {
                estilo.setFont(fuente);
            }
            return estilo;
        }

        CellStyle dato(TipoColumna tipo) {
            return datos[tipo.ordinal()];
        }

        CellStyle total(TipoColumna tipo) {
            return totales[tipo.ordinal()];
        }
    }
}
