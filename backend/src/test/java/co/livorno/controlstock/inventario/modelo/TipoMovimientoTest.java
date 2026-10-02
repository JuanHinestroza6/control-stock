package co.livorno.controlstock.inventario.modelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TipoMovimientoTest {

    @Test
    void efecto_losNueveTipos_aplicanLosDeltasDelDominio() {
        Map<TipoMovimiento, int[]> esperado = Map.of(
                TipoMovimiento.ENTRADA, new int[] {4, 0},
                TipoMovimiento.VENTA, new int[] {-4, 0},
                TipoMovimiento.VENTA_RESERVA, new int[] {-4, -4},
                TipoMovimiento.DEVOLUCION, new int[] {4, 0},
                TipoMovimiento.AJUSTE_POSITIVO, new int[] {4, 0},
                TipoMovimiento.AJUSTE_NEGATIVO, new int[] {-4, 0},
                TipoMovimiento.RESERVA, new int[] {0, 4},
                TipoMovimiento.LIBERACION, new int[] {0, -4},
                TipoMovimiento.ANULACION_VENTA, new int[] {4, 0});

        assertThat(TipoMovimiento.values()).hasSize(9).containsExactlyInAnyOrderElementsOf(esperado.keySet());
        esperado.forEach((tipo, d) -> assertThat(tipo.efecto(4)).as(tipo.name())
                .isEqualTo(new EfectoMovimiento(d[0], d[1])));
    }
}
