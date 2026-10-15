package co.livorno.controlstock.analitica.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.analitica.modelo.ResultadoEjecucion;
import co.livorno.controlstock.analitica.repositorio.ConsultasEstadoAlertas;
import co.livorno.controlstock.inventario.servicio.AlertaService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class EjecucionAnaliticaServiceGuardiaTest {

    private final RegistroEjecucionService registro = mock(RegistroEjecucionService.class);
    private final CalculoAnaliticoService calculo = mock(CalculoAnaliticoService.class);
    private final AlertaService alertas = mock(AlertaService.class);
    private final ConsultasEstadoAlertas estado = mock(ConsultasEstadoAlertas.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T07:00:00Z"), ZoneId.of("America/Bogota"));
    private final EjecucionAnaliticaService servicio =
            new EjecucionAnaliticaService(registro, calculo, alertas, estado, clock);

    @Test
    void ejecutar_falloInesperadoFueraDelCalculo_liberaLaGuardia() {
        // dado: la consulta de variantes lanza una excepcion no capturada por el orquestador
        when(registro.iniciar(any(), any(), any())).thenReturn(1L);
        when(estado.idsTodasLasVariantes()).thenThrow(new IllegalStateException("bd caida"))
                .thenReturn(List.of());
        when(registro.terminar(anyLong(), any(ResultadoEjecucion.class))).thenReturn(mock(EjecucionAnalitica.class));
        when(calculo.calcularYAplicar(anyLong())).thenReturn(new ResultadoCalculo(0, 0, 0, 0, 0));

        // cuando / entonces: la primera falla y la segunda ya no recibe EjecucionEnCursoException
        assertThatThrownBy(() -> servicio.ejecutar(OrigenEjecucion.NOCTURNA, null))
                .isInstanceOf(IllegalStateException.class).hasMessage("bd caida");
        assertThat(servicio.ejecutar(OrigenEjecucion.NOCTURNA, null)).isNotNull();
    }

    @Test
    void ejecutar_sinVariantes_terminaSinEvaluarYSinDividirEntreCero() {
        when(registro.iniciar(any(), any(), any())).thenReturn(1L);
        when(estado.idsTodasLasVariantes()).thenReturn(List.of());
        when(calculo.calcularYAplicar(anyLong())).thenReturn(new ResultadoCalculo(0, 0, 0, 0, 0));
        when(registro.terminar(anyLong(), any(ResultadoEjecucion.class))).thenReturn(
                EjecucionAnalitica.iniciar(OrigenEjecucion.NOCTURNA, null, java.time.LocalDateTime.now(clock),
                        java.time.LocalDate.now(clock)));

        assertThat(servicio.ejecutar(OrigenEjecucion.NOCTURNA, null)).isNotNull();
    }
}
