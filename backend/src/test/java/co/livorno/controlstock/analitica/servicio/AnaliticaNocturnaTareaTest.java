package co.livorno.controlstock.analitica.servicio;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class AnaliticaNocturnaTareaTest {

    private final EjecucionAnaliticaService ejecucion = mock(EjecucionAnaliticaService.class);
    private final AnaliticaNocturnaTarea tarea = new AnaliticaNocturnaTarea(ejecucion);

    @Test
    void ejecutar_llamaAlOrquestadorConOrigenNocturna() {
        tarea.ejecutar();

        verify(ejecucion).ejecutar(OrigenEjecucion.NOCTURNA, null);
    }

    @Test
    void ejecutar_excepcionInesperada_laCapturaYNoLaPropaga() {
        when(ejecucion.ejecutar(OrigenEjecucion.NOCTURNA, null)).thenThrow(new IllegalStateException("boom"));

        assertThatCode(tarea::ejecutar).doesNotThrowAnyException();
    }

    @Test
    void ejecutar_hayOtraEnCurso_seOmiteSinPropagar() {
        when(ejecucion.ejecutar(OrigenEjecucion.NOCTURNA, null)).thenThrow(new EjecucionEnCursoException());

        assertThatCode(tarea::ejecutar).doesNotThrowAnyException();
    }

    @Test
    void ejecutar_estaProgramadaConElCronDeConfiguracionEnBogota() throws NoSuchMethodException {
        Method m = AnaliticaNocturnaTarea.class.getMethod("ejecutar");
        Scheduled s = m.getAnnotation(Scheduled.class);

        org.assertj.core.api.Assertions.assertThat(s.cron()).contains("app.analitica.cron");
        org.assertj.core.api.Assertions.assertThat(s.zone()).isEqualTo("America/Bogota");
    }
}
