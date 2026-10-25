package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** B10, fase 7: unidades perdidas comparables y razón de la corrección por agotados en el resumen de la semilla. */
class ResumenSemillaTest {

    private static ResultadoSimulacion simulacion(long perdidas, long comparables) {
        return new ResultadoSimulacion(1096, 20_281, 40_000, 30_000, 900, 25_000, 600, perdidas, comparables, 139, 500,
                40, 3_000, 12, 1, 1, new BigDecimal("1000000.00"), 300, 200, 50, 50, 3_673, 3_575, 98, 1_060, List.of());
    }

    private static ResumenSemilla.CierreAnalitica cierre(String agregadas) {
        return new ResumenSemilla.CierreAnalitica(78, 82, 131, 291, Map.of("NORMAL", 42L), 291, 42,
                new BigDecimal("14.4"), 18, 6_500, 7, 22, new BigDecimal("0.1830"), 113, 101,
                agregadas == null ? null : new BigDecimal(agregadas));
    }

    private static ResumenSemilla resumen(long perdidas, long comparables, String agregadas) {
        return new ResumenSemilla(true, LocalDate.of(2023, 10, 24), LocalDate.of(2026, 10, 23), 4, 30, 299, 5_000,
                simulacion(perdidas, comparables), 5, 26, 16, 266_000, cierre(agregadas));
    }

    @Test
    void razonCorreccion_divideLoAgregadoEntreLasPerdidasComparables() {
        ResumenSemilla r = resumen(2_817, 1_200, "960.50");

        assertThat(r.razonCorreccion()).isEqualByComparingTo("0.80");
        assertThat(r.texto())
                .contains("2817 unidades (comparables con la corrección: 1200)")
                .contains("+960.50 unidades = 0.80× las perdidas comparables (esperado 0,5×–2×)");
    }

    @Test
    void razonCorreccion_sinPerdidasComparablesOSinDato_esNd() {
        assertThat(resumen(10, 0, "5").razonCorreccion()).isNull();
        assertThat(resumen(10, 4, null).razonCorreccion()).isNull();
        assertThat(resumen(10, 0, "5").texto()).contains("+5 unidades = n/d×");
    }
}
