package co.livorno.controlstock.comun.exportacion;

import java.util.Objects;

/** Columna de una sección: encabezado, tipo y ancho relativo (aprox. en caracteres). */
public record ColumnaReporte(String encabezado, TipoColumna tipo, int ancho) {

    public ColumnaReporte {
        Objects.requireNonNull(encabezado, "encabezado");
        Objects.requireNonNull(tipo, "tipo");
        if (ancho < 1) {
            throw new IllegalArgumentException("El ancho de la columna debe ser positivo.");
        }
    }

    public static ColumnaReporte texto(String encabezado, int ancho) {
        return new ColumnaReporte(encabezado, TipoColumna.TEXTO, ancho);
    }

    public static ColumnaReporte entero(String encabezado, int ancho) {
        return new ColumnaReporte(encabezado, TipoColumna.ENTERO, ancho);
    }

    public static ColumnaReporte dinero(String encabezado, int ancho) {
        return new ColumnaReporte(encabezado, TipoColumna.DINERO, ancho);
    }

    public static ColumnaReporte porcentaje(String encabezado, int ancho) {
        return new ColumnaReporte(encabezado, TipoColumna.PORCENTAJE, ancho);
    }

    public static ColumnaReporte fecha(String encabezado, int ancho) {
        return new ColumnaReporte(encabezado, TipoColumna.FECHA, ancho);
    }

    public static ColumnaReporte fechaHora(String encabezado, int ancho) {
        return new ColumnaReporte(encabezado, TipoColumna.FECHA_HORA, ancho);
    }
}
