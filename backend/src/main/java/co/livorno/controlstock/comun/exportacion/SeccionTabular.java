package co.livorno.controlstock.comun.exportacion;

import java.util.List;
import java.util.Objects;

/**
 * Una tabla del documento (una hoja en Excel, una tabla en el PDF).
 *
 * @param nombre      título de la sección y nombre de la hoja de Excel
 * @param columnas    columnas en orden
 * @param filas       filas; cada una con un valor por columna (BigDecimal, Number, LocalDate, LocalDateTime, texto o null)
 * @param totales     fila de totales opcional (null = sin totales); misma longitud que las columnas, null deja la celda vacía
 * @param mensaje     texto opcional (p. ej. "No hay ventas en el periodo"); se muestra cuando no hay filas
 * @param soloExcel   si es true, el PDF la omite con una nota ("El detalle está disponible en Excel")
 * @param imagenPdf   imagen PNG opcional que el PDF inserta antes de la tabla (punto de extensión para gráficos); Excel la ignora
 */
public record SeccionTabular(
        String nombre,
        List<ColumnaReporte> columnas,
        List<List<Object>> filas,
        List<Object> totales,
        String mensaje,
        boolean soloExcel,
        byte[] imagenPdf) {

    public SeccionTabular {
        Objects.requireNonNull(nombre, "nombre");
        columnas = List.copyOf(columnas);
        filas = filas == null ? List.of() : filas;
        if (totales != null && totales.size() != columnas.size()) {
            throw new IllegalArgumentException("La fila de totales debe tener una celda por columna.");
        }
    }

    public static SeccionTabular de(String nombre, List<ColumnaReporte> columnas, List<List<Object>> filas) {
        return new SeccionTabular(nombre, columnas, filas, null, null, false, null);
    }

    public SeccionTabular conTotales(List<Object> nuevosTotales) {
        return new SeccionTabular(nombre, columnas, filas, nuevosTotales, mensaje, soloExcel, imagenPdf);
    }

    public SeccionTabular conMensaje(String nuevoMensaje) {
        return new SeccionTabular(nombre, columnas, filas, totales, nuevoMensaje, soloExcel, imagenPdf);
    }

    public SeccionTabular soloEnExcel() {
        return new SeccionTabular(nombre, columnas, filas, totales, mensaje, true, imagenPdf);
    }

    /** Inserta una imagen PNG (p. ej. un gráfico) antes de la tabla en el PDF. */
    public SeccionTabular conImagenPdf(byte[] png) {
        return new SeccionTabular(nombre, columnas, filas, totales, mensaje, soloExcel, png);
    }
}
