package co.livorno.controlstock.inventario.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.comun.config.ConfiguracionTareas;
import co.livorno.controlstock.inventario.repositorio.ReservaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ExtendWith(MockitoExtension.class)
class VencimientoReservasTareaTest {

    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneId.of("America/Bogota"));
    static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 2, 10, 0);

    @Mock ReservaRepository reservas;
    @Mock ReservaService servicio;

    @Test
    void procesar_unaFallaYOtraYaCerrada_sigueConLasDemasYCuentaSoloLasVencidas() {
        VencimientoReservasTarea tarea = new VencimientoReservasTarea(reservas, servicio, RELOJ);
        when(reservas.idsVencidas(eq(AHORA), any())).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(servicio.vencer(1L, AHORA)).thenReturn(true);
        when(servicio.vencer(2L, AHORA)).thenThrow(new IllegalStateException("bloqueada"));
        when(servicio.vencer(3L, AHORA)).thenReturn(false);
        when(servicio.vencer(4L, AHORA)).thenReturn(true);

        int vencidas = tarea.procesar(AHORA);

        assertThat(vencidas).isEqualTo(2);
        verify(servicio).vencer(4L, AHORA);
    }

    @Test
    void ejecutar_usaElClockInyectado() {
        VencimientoReservasTarea tarea = new VencimientoReservasTarea(reservas, servicio, RELOJ);
        when(reservas.idsVencidas(eq(AHORA), any())).thenReturn(List.of());

        tarea.ejecutar();

        verify(reservas).idsVencidas(eq(AHORA), any());
        org.mockito.Mockito.verifyNoInteractions(servicio);
    }

    @Test
    void configuracion_conPropiedadFalse_noRegistraEnableScheduling() {
        var runner = new ApplicationContextRunner().withUserConfiguration(ConfiguracionTareas.class);

        runner.withPropertyValues("app.tareas.habilitadas=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ConfiguracionTareas.class));
        runner.run(ctx -> assertThat(ctx).hasSingleBean(ConfiguracionTareas.class));
        runner.withPropertyValues("app.tareas.habilitadas=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(ConfiguracionTareas.class));
        assertThat(ConfiguracionTareas.class.getAnnotation(ConditionalOnProperty.class).name())
                .containsExactly("app.tareas.habilitadas");
    }
}
