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
    private final PronosticoService pronostico = mock(PronosticoService.class);
    private final CalculoAnaliticoService calculo = mock(CalculoAnaliticoService.class);
    private final AlertaService alertas = mock(AlertaService.class);
    private final ConsultasEstadoAlertas estado = mock(ConsultasEstadoAlertas.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T07:00:00Z"), ZoneId.of("America/Bogota"));
    private final EjecucionAnaliticaService servicio =
            new EjecucionAnaliticaService(registro, pronostico, calculo, alertas, estado, clock);

    @Test
    void ejecutar_falloInesperadoFueraDelCalculo_liberaLaGuardia() {
        // dado: la consulta de variantes lanza una excepcion no capturada por el orquestador
        when(registro.iniciar(any(), any(), any())).thenReturn(1L);
        when(estado.idsTodasLasVariantes()).thenThrow(new IllegalStateException("bd caida"))
                .thenReturn(List.of());
        when(registro.terminar(anyLong(), any(ResultadoEjecucion.class))).thenReturn(mock(EjecucionAnalitica.class));
        when(pronostico.calcularYGuardar(anyLong()))
                .thenReturn(new ResultadoPronosticoGuardado(java.util.Map.of(), 0, 0, 0, null));
        when(calculo.calcularYAplicar(anyLong(), any())).thenReturn(new ResultadoCalculo(0, 0, 0, 0, 0));

        // cuando / entonces: la primera falla y la segunda ya no recibe EjecucionEnCursoException
        assertThatThrownBy(() -> servicio.ejecutar(OrigenEjecucion.NOCTURNA, null))
                .isInstanceOf(IllegalStateException.class).hasMessage("bd caida");
        assertThat(servicio.ejecutar(OrigenEjecucion.NOCTURNA, null)).isNotNull();
    }

    @Test
    void ejecutar_sinVariantes_terminaSinEvaluarYSinDividirEntreCero() {
        when(registro.iniciar(any(), any(), any())).thenReturn(1L);
        when(estado.idsTodasLasVariantes()).thenReturn(List.of());
        when(pronostico.calcularYGuardar(anyLong()))
                .thenReturn(new ResultadoPronosticoGuardado(java.util.Map.of(), 0, 0, 0, null));
        when(calculo.calcularYAplicar(anyLong(), any())).thenReturn(new ResultadoCalculo(0, 0, 0, 0, 0));
        when(registro.terminar(anyLong(), any(ResultadoEjecucion.class))).thenReturn(
                EjecucionAnalitica.iniciar(OrigenEjecucion.NOCTURNA, null, java.time.LocalDateTime.now(clock),
                        java.time.LocalDate.now(clock)));

        assertThat(servicio.ejecutar(OrigenEjecucion.NOCTURNA, null)).isNotNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(OrigenEjecucion.class)
    void ejecutar_cualquierOrigen_ejecutaElPasoDePronosticoAntesDelCalculo(OrigenEjecucion origen) {
        // dado
        when(registro.iniciar(any(), any(), any())).thenReturn(1L);
        when(estado.idsTodasLasVariantes()).thenReturn(List.of());
        when(pronostico.calcularYGuardar(anyLong()))
                .thenReturn(new ResultadoPronosticoGuardado(java.util.Map.of(), 0, 0, 0, null));
        when(calculo.calcularYAplicar(anyLong(), any())).thenReturn(new ResultadoCalculo(0, 0, 0, 0, 0));
        when(registro.terminar(anyLong(), any(ResultadoEjecucion.class))).thenReturn(mock(EjecucionAnalitica.class));

        // cuando
        servicio.ejecutar(origen, null);

        // entonces: pronostico, registro de sus agregados y luego calculo
        var orden = org.mockito.Mockito.inOrder(registro, pronostico, calculo);
        orden.verify(pronostico).calcularYGuardar(1L);
        orden.verify(registro).registrarPronostico(anyLong(), any());
        orden.verify(calculo).calcularYAplicar(anyLong(), any());
    }

    @Test
    void ejecutar_fallanPronosticoYCalculo_elMensajeEsElDelCalculo() {
        // dado: ambos pasos fallan
        when(registro.iniciar(any(), any(), any())).thenReturn(1L);
        when(estado.idsTodasLasVariantes()).thenReturn(List.of());
        when(pronostico.calcularYGuardar(anyLong())).thenThrow(new IllegalStateException("pronostico roto"));
        when(calculo.calcularYAplicar(anyLong(), any())).thenThrow(new IllegalStateException("calculo roto"));
        when(registro.fallar(anyLong(), any(String.class), any(ResultadoEjecucion.class)))
                .thenReturn(mock(EjecucionAnalitica.class));

        // cuando
        servicio.ejecutar(OrigenEjecucion.MANUAL, 3L);

        // entonces: mensaje del calculo; no se usa el del pronostico ni se termina como exitosa
        org.mockito.Mockito.verify(registro).fallar(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(EjecucionAnaliticaService.MENSAJE_CALCULO_FALLIDO),
                any(ResultadoEjecucion.class));
        org.mockito.Mockito.verify(registro, org.mockito.Mockito.never()).fallarConResultado(anyLong(), any(), any());
        org.mockito.Mockito.verify(registro, org.mockito.Mockito.never()).terminar(anyLong(), any());
    }
}
