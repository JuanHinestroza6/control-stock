package co.livorno.controlstock.analitica.modelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EjecucionAnaliticaPorcentajeTest {

    private EjecucionAnalitica terminada(int activas, int enAlerta) {
        LocalDateTime t = LocalDateTime.of(2026, 10, 14, 2, 0);
        EjecucionAnalitica e = EjecucionAnalitica.iniciar(OrigenEjecucion.NOCTURNA, null, t, LocalDate.of(2026, 10, 14));
        e.terminar(t.plusSeconds(1), new ResultadoEjecucion(activas, 0, 0, activas, 0, activas, 0, activas, enAlerta, 0));
        return e;
    }

    @Test
    void getPorcentajeAlertaStock_sinVariantesActivas_devuelveNullSinDividirEntreCero() {
        assertThat(terminada(0, 0).getPorcentajeAlertaStock()).isNull();
    }

    @Test
    void getPorcentajeAlertaStock_sinMedir_devuelveNull() {
        EjecucionAnalitica e = EjecucionAnalitica.iniciar(OrigenEjecucion.NOCTURNA, null,
                LocalDateTime.of(2026, 10, 14, 2, 0), LocalDate.of(2026, 10, 14));
        assertThat(e.getPorcentajeAlertaStock()).isNull();
    }

    @Test
    void getPorcentajeAlertaStock_141De299_es47Punto2ConEscala1() {
        BigDecimal p = terminada(299, 141).getPorcentajeAlertaStock();
        assertThat(p).isEqualTo(new BigDecimal("47.2"));
        assertThat(p.scale()).isEqualTo(1);
    }

    @Test
    void getPorcentajeAlertaStock_empateEnLaMitad_redondeaHaciaArriba() {
        // 1/16 = 6,25 % -> 6,3 (HALF_UP; HALF_EVEN daría 6,2)
        assertThat(terminada(16, 1).getPorcentajeAlertaStock()).isEqualTo(new BigDecimal("6.3"));
    }

    @Test
    void getPorcentajeAlertaStock_todasEnAlerta_es100() {
        assertThat(terminada(5, 5).getPorcentajeAlertaStock()).isEqualByComparingTo("100.0");
    }
}
