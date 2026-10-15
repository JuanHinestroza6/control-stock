package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ejecucion nocturna de la analitica (02:00 America/Bogota, {@code app.analitica.cron}). Se apaga con
 * {@code app.tareas.habilitadas=false}. Nunca propaga excepciones: un fallo se registra y se reintenta la noche
 * siguiente.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnaliticaNocturnaTarea {

    private final EjecucionAnaliticaService ejecucion;

    @Scheduled(cron = "${app.analitica.cron:0 0 2 * * *}", zone = "America/Bogota")
    public void ejecutar() {
        try {
            ejecucion.ejecutar(OrigenEjecucion.NOCTURNA, null);
        } catch (EjecucionEnCursoException e) {
            log.info("Analítica nocturna omitida: ya hay una ejecución en curso");
        } catch (RuntimeException e) {
            log.error("Falló la ejecución nocturna de la analítica", e);
        }
    }
}
