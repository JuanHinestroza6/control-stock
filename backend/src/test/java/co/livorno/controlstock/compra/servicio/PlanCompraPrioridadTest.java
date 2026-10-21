package co.livorno.controlstock.compra.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.compra.servicio.FuenteSugerenciaCompra.LineaSugerida;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Orden de prioridad de la sugerencia cuando hay más candidatas que el tope del plan (ADR-010, decisión 6). */
class PlanCompraPrioridadTest {

    private static LineaSugerida linea(long id, Character clase, int disponible, int umbral, long posicion) {
        return new LineaSugerida(id, 1, "x", clase, disponible, umbral, posicion);
    }

    private static List<Long> ordenar(LineaSugerida... lineas) {
        return java.util.Arrays.stream(lineas).sorted(PlanCompraService.PRIORIDAD).map(LineaSugerida::varianteId)
                .toList();
    }

    @Test
    void prioridad_agotadaPrimeroAunqueSeaClaseC() {
        // dado: A con stock, C agotada
        // cuando / entonces
        assertThat(ordenar(linea(1, 'A', 3, 10, 3), linea(2, 'C', 0, 10, 0))).containsExactly(2L, 1L);
    }

    @Test
    void prioridad_clasesEnOrdenABCYSinClase() {
        assertThat(ordenar(linea(1, null, 0, 10, 0), linea(2, 'C', 0, 10, 0), linea(3, 'B', 0, 10, 0),
                linea(4, 'A', 0, 10, 0))).containsExactly(4L, 3L, 2L, 1L);
    }

    @Test
    void prioridad_mismaClase_mayorFaltanteRelativoPrimero() {
        // dado: relativos 0,5 (ROP 10, posición 5), 0,4 (ROP 100, posición 60) y 0 (ROP 0, posición 0)
        assertThat(ordenar(linea(1, 'B', 60, 100, 60), linea(2, 'B', 5, 10, 5), linea(3, 'B', 1, 0, 0)))
                .containsExactly(2L, 1L, 3L);
    }

    @Test
    void prioridad_empateTotal_desempataPorId() {
        assertThat(ordenar(linea(9, 'A', 2, 10, 2), linea(4, 'A', 2, 10, 2))).containsExactly(4L, 9L);
    }
}
