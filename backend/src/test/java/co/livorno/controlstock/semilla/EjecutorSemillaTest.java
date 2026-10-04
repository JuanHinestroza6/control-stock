package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class EjecutorSemillaTest {

    private final GeneradorDatosSemilla generador = mock(GeneradorDatosSemilla.class);

    private EjecutorSemilla ejecutor(boolean salir) {
        PropiedadesSemilla props = new PropiedadesSemilla(true, salir, 42, 24, null, 15, "x");
        EjecutorSemilla e = spy(new EjecutorSemilla(generador, props, mock(ConfigurableApplicationContext.class)));
        doNothing().when(e).terminar(anyInt());
        return e;
    }

    @Test
    void run_conExitoYSalir_terminaConCero() {
        when(generador.generar()).thenReturn(ResumenSemilla.yaHabiaDatos());
        EjecutorSemilla e = ejecutor(true);

        e.run();

        verify(e).terminar(0);
    }

    @Test
    void run_conAbortoYSalir_terminaConUno() {
        when(generador.generar()).thenThrow(new SemillaAbortadaException("base no vacía"));
        EjecutorSemilla e = ejecutor(true);

        e.run();

        verify(e).terminar(1);
    }

    @Test
    void run_conAbortoSinSalir_relanzaYNoTermina() {
        when(generador.generar()).thenThrow(new SemillaAbortadaException("base no vacía"));
        EjecutorSemilla e = ejecutor(false);

        assertThatThrownBy(e::run).isInstanceOf(SemillaAbortadaException.class);
        verify(e, never()).terminar(anyInt());
    }

    @Test
    void run_conExitoSinSalir_noTermina() {
        when(generador.generar()).thenReturn(ResumenSemilla.yaHabiaDatos());
        EjecutorSemilla e = ejecutor(false);

        e.run();

        verify(e, never()).terminar(anyInt());
    }
}
