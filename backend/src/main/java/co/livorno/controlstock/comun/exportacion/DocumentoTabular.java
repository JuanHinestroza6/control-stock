package co.livorno.controlstock.comun.exportacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Contenido neutral de un reporte exportable: lo arma el servicio del módulo y lo convierten
 * {@link GeneradorExcel} y {@link GeneradorPdf} (ADR-011). No depende de ningún módulo de negocio.
 *
 * @param titulo      título del reporte
 * @param filtros     filtros aplicados (puede ser vacío)
 * @param generadoPor nombre de quien lo genera
 * @param generadoEn  fecha y hora de generación (America/Bogota, del Clock)
 * @param secciones   una o varias tablas
 * @param notas       notas al pie / hoja "Notas"
 */
public record DocumentoTabular(
        String titulo,
        List<FiltroReporte> filtros,
        String generadoPor,
        LocalDateTime generadoEn,
        List<SeccionTabular> secciones,
        List<String> notas) {

    public DocumentoTabular {
        Objects.requireNonNull(titulo, "titulo");
        Objects.requireNonNull(generadoPor, "generadoPor");
        Objects.requireNonNull(generadoEn, "generadoEn");
        filtros = filtros == null ? List.of() : List.copyOf(filtros);
        secciones = List.copyOf(secciones);
        notas = notas == null ? List.of() : List.copyOf(notas);
    }
}
