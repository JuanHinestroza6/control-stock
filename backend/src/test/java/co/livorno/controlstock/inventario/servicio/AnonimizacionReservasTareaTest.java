package co.livorno.controlstock.inventario.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnonimizacionReservasTareaTest {

    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneId.of("America/Bogota"));
    static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 2, 10, 0);

    @Mock AnonimizacionReservasService servicio;

    @Test
    void ejecutar_usaComoLimiteExactoAhoraMenos90Dias() {
        AnonimizacionReservasTarea tarea = new AnonimizacionReservasTarea(servicio, RELOJ, 90);
        when(servicio.anonimizarLote(any(), eq(AnonimizacionReservasTarea.TAMANO_LOTE))).thenReturn(0);

        tarea.ejecutar();

        // la consulta usa cerradaEn <= limite: cerrada justo hace 90 dias entra, un segundo despues no
        verify(servicio).anonimizarLote(LocalDateTime.of(2026, 7, 4, 10, 0), AnonimizacionReservasTarea.TAMANO_LOTE);
    }

    @Test
    void procesar_variosLotes_sigueHastaUnLoteIncompletoYSumaTotal() {
        AnonimizacionReservasTarea tarea = new AnonimizacionReservasTarea(servicio, RELOJ, 90);
        int lote = AnonimizacionReservasTarea.TAMANO_LOTE;
        when(servicio.anonimizarLote(any(), eq(lote))).thenReturn(lote, lote, 7);

        int total = tarea.procesar(AHORA);

        assertThat(total).isEqualTo(2 * lote + 7);
        verify(servicio, times(3)).anonimizarLote(any(), eq(lote));
    }

    @Test
    void ejecutar_siElServicioFalla_noPropagaLaExcepcion() {
        AnonimizacionReservasTarea tarea = new AnonimizacionReservasTarea(servicio, RELOJ, 30);
        when(servicio.anonimizarLote(any(), eq(AnonimizacionReservasTarea.TAMANO_LOTE)))
                .thenThrow(new IllegalStateException("bd caida"));

        tarea.ejecutar();
    }

    @Test
    void constructor_diasMenorQueUno_lanzaIllegalArgument() {
        assertThatThrownBy(() -> new AnonimizacionReservasTarea(servicio, RELOJ, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
