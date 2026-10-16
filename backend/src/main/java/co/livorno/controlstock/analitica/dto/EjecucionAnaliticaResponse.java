package co.livorno.controlstock.analitica.dto;

import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.EstadoEjecucion;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una ejecución de la analítica. {@code usuario} es el nombre de quien la lanzó (solo MANUAL). */
public record EjecucionAnaliticaResponse(
        Long id,
        OrigenEjecucion origen,
        String usuario,
        LocalDateTime iniciadaEn,
        LocalDateTime terminadaEn,
        EstadoEjecucion estado,
        LocalDate fechaCorte,
        Long duracionMs,
        Integer variantesAnalizadas,
        Integer claseA,
        Integer claseB,
        Integer claseC,
        Integer conPuntoReorden,
        Integer alertasEvaluadas,
        Integer alertasFallidas,
        Integer variantesActivas,
        Integer enAlertaStock,
        BigDecimal porcentajeAlertaStock,
        Integer sinRotacionActivas,
        String mensaje,
        Integer productosPronosticados,
        Integer conHoltWinters,
        Integer conSes,
        BigDecimal wapeGlobal) {

    public static EjecucionAnaliticaResponse de(EjecucionAnalitica e, String nombreUsuario) {
        return new EjecucionAnaliticaResponse(e.getId(), e.getOrigen(), nombreUsuario, e.getIniciadaEn(),
                e.getTerminadaEn(), e.getEstado(), e.getFechaCorte(), e.getDuracionMs(), e.getVariantesAnalizadas(),
                e.getClaseA(), e.getClaseB(), e.getClaseC(), e.getConPuntoReorden(), e.getAlertasEvaluadas(),
                e.getAlertasFallidas(), e.getVariantesActivas(), e.getEnAlertaStock(),
                e.getPorcentajeAlertaStock(), e.getSinRotacionActivas(), e.getMensaje(),
                e.getProductosPronosticados(), e.getConHoltWinters(), e.getConSes(), e.getWapeGlobal());
    }
}
