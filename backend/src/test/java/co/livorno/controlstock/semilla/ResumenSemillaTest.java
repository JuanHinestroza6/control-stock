package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * B10, fase 7: unidades perdidas comparables y razón de la corrección por agotados en el resumen de la semilla. La
 * ventana son los 12 meses completos antes del mes de la fecha de corte de la analítica de cierre.
 */
class ResumenSemillaTest {

    /** 100 unidades perdidas evitables por mes de oct-2025 a oct-2026 (13 meses) más 999 en sep-2025. */
    private static final Map<YearMonth, Long> POR_MES = porMes();

    private static Map<YearMonth, Long> porMes() {
        Map<YearMonth, Long> m = new java.util.HashMap<>();
        for (YearMonth mes = YearMonth.of(2025, 10); !mes.isAfter(YearMonth.of(2026, 10)); mes = mes.plusMonths(1)) {
            m.put(mes, 100L);
        }
        m.put(YearMonth.of(2025, 9), 999L);
        return Map.copyOf(m);
    }

    private static ResultadoSimulacion simulacion(Map<YearMonth, Long> porMes) {
        return new ResultadoSimulacion(1096, 20_281, 40_000, 30_000, 900, 25_000, 600, 2_817, porMes, 139, 500, 40,
                3_000, 12, 1, 1, new BigDecimal("1000000.00"), 300, 200, 50, 50, 3_673, 3_575, 98, 1_060, List.of());
    }

    private static ResumenSemilla.CierreAnalitica cierre(LocalDate corte, String agregadas) {
        return new ResumenSemilla.CierreAnalitica(78, 82, 131, 291, Map.of("NORMAL", 42L), 291, 42,
                new BigDecimal("14.4"), 18, 6_500, 7, 22, new BigDecimal("0.1830"), 113, 101, corte,
                agregadas == null ? null : new BigDecimal(agregadas));
    }

    private static ResumenSemilla resumen(Map<YearMonth, Long> porMes, LocalDate corte, String agregadas) {
        return new ResumenSemilla(true, LocalDate.of(2023, 10, 24), LocalDate.of(2026, 10, 23), 4, 30, 299, 5_000,
                simulacion(porMes), 5, 26, 16, 266_000, cierre(corte, agregadas));
    }

    @Test
    void comparables_sonLosDoceMesesCompletosAntesDelMesDelCorte() {
        ResultadoSimulacion s = simulacion(POR_MES);

        // corte 2026-10-24: oct-2025 a sep-2026 (12 × 100); fuera sep-2025 y el mes en curso
        assertThat(s.unidadesPerdidasComparables(YearMonth.of(2026, 10))).isEqualTo(1_200);
        // corte en enero de 2026: oct-2025 a dic-2025 (3 × 100) más sep-2025 (999)
        assertThat(s.unidadesPerdidasComparables(YearMonth.of(2026, 1))).isEqualTo(1_299);
    }

    @Test
    void razonCorreccion_usaLaVentanaDeLaFechaDeCorteDeLaAnalitica() {
        ResumenSemilla r = resumen(POR_MES, LocalDate.of(2026, 10, 24), "960.50");

        assertThat(r.unidadesPerdidasComparables()).isEqualTo(1_200);
        assertThat(r.razonCorreccion()).isEqualByComparingTo("0.80");
        assertThat(r.texto())
                .contains("ventas perdidas: 900 líneas")
                .contains("2817 unidades")
                .contains("corrección por agotados (12 meses antes de 2026-10): +960.50 unidades frente a 1200 perdidas"
                        + " comparables")
                .contains("= 0.80× (esperado 0,5×–2×)");
    }

    @Test
    void razonCorreccion_sinPerdidasComparablesOSinDato_esNd() {
        assertThat(resumen(Map.of(), LocalDate.of(2026, 10, 24), "5").razonCorreccion()).isNull();
        assertThat(resumen(POR_MES, LocalDate.of(2026, 10, 24), null).razonCorreccion()).isNull();
        assertThat(resumen(POR_MES, null, "5").unidadesPerdidasComparables()).isNull();
        assertThat(resumen(Map.of(), LocalDate.of(2026, 10, 24), "5").texto()).contains("+5 unidades frente a 0")
                .contains("= n/d×");
    }
}
