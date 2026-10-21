package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class PronosticoMensualTest {

    private static final double EPS = 1e-9;

    /** Octubre 31 (1/día), noviembre 60 (2/día), diciembre 93 (3/día) de 2026. */
    private static PronosticoMensual octubreADiciembre() {
        return new PronosticoMensual(YearMonth.of(2026, 10),
                List.of(new BigDecimal("31.00"), new BigDecimal("60.00"), new BigDecimal("93.00")));
    }

    /*
     * Ejemplo del plan: desde el 25-oct, 15 días.
     *   25..31 oct = 7 días × 31/31 = 7 ; 1..8 nov = 8 días × 60/30 = 16 → μL = 23.
     */
    @Test
    void demanda_cruzaOctubreNoviembre_23() {
        assertThat(octubreADiciembre().demanda(LocalDate.of(2026, 10, 25), 15)).isCloseTo(23.0, within(EPS));
    }

    /*
     * Horizonte agotado: desde el 20-dic, 20 días.
     *   20..31 dic = 12 × 93/31 = 36 ; 1..8 ene (fuera del horizonte) = 8 × 3 (tasa de diciembre) = 24 → 60.
     */
    @Test
    void demanda_horizonteAgotado_usaTasaDelUltimoMes() {
        PronosticoMensual p = octubreADiciembre();

        assertThat(p.demanda(LocalDate.of(2026, 12, 20), 20)).isCloseTo(60.0, within(EPS));
        // Febrero de 2027 (28 días) también usa la tasa de diciembre: 93/31 = 3, no 93/28.
        assertThat(p.tasaDiaria(LocalDate.of(2027, 2, 10))).isCloseTo(3.0, within(EPS));
    }

    /*
     * Meses de distinto número de días: enero 31 (1/día), febrero 56 en 2027 (28 días → 2/día), marzo 62 (2/día).
     *   Desde el 30-ene, 5 días: 2 × 1 + 3 × 2 = 8.  Desde el 27-feb, 4 días: 2 × 2 + 2 × 2 = 8.
     */
    @Test
    void demanda_febreroDe28Dias() {
        PronosticoMensual p = new PronosticoMensual(YearMonth.of(2027, 1),
                List.of(new BigDecimal("31.00"), new BigDecimal("56.00"), new BigDecimal("62.00")));

        assertThat(p.demanda(LocalDate.of(2027, 1, 30), 5)).isCloseTo(8.0, within(EPS));
        assertThat(p.demanda(LocalDate.of(2027, 2, 27), 4)).isCloseTo(8.0, within(EPS));
        assertThat(p.tasaDiaria(LocalDate.of(2027, 2, 1))).isCloseTo(2.0, within(EPS));
    }

    @Test
    void demanda_ceroDias_esCero_yAntesDelPrimerMesUsaElPrimero() {
        PronosticoMensual p = octubreADiciembre();

        assertThat(p.demanda(LocalDate.of(2026, 10, 25), 0)).isZero();
        assertThat(p.tasaDiaria(LocalDate.of(2026, 9, 30))).isCloseTo(1.0, within(EPS));
    }

    @Test
    void validaciones() {
        YearMonth mes = YearMonth.of(2026, 10);
        assertThatThrownBy(() -> new PronosticoMensual(mes, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PronosticoMensual(mes, List.of(new BigDecimal("-0.01"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> octubreADiciembre().demanda(LocalDate.of(2026, 10, 1), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /* L cruza el fin de año: diciembre 31 (1/día), enero 62 (2/día). Desde 28-dic, 10 días → 4 × 1 + 6 × 2 = 16. */
    @Test
    void demanda_cruzaFinDeAnio_16() {
        PronosticoMensual p = new PronosticoMensual(YearMonth.of(2026, 12),
                List.of(new BigDecimal("31.00"), new BigDecimal("62.00")));

        assertThat(p.demanda(LocalDate.of(2026, 12, 28), 10)).isCloseTo(16.0, within(EPS));
        assertThat(p.tasaDiaria(LocalDate.of(2027, 1, 1))).isCloseTo(2.0, within(EPS));
    }

    /* Horizonte de un solo mes (noviembre 30 → 1/día): días de diciembre usan esa misma tasa. */
    @Test
    void demanda_horizonteDeUnMes_extiendeLaTasa() {
        PronosticoMensual p = new PronosticoMensual(YearMonth.of(2026, 11), List.of(new BigDecimal("30.00")));

        assertThat(p.demanda(LocalDate.of(2026, 11, 20), 20)).isCloseTo(20.0, within(EPS));
    }

    /* Febrero bisiesto (2028) tiene 29 días: 29.00 → 1/día. */
    @Test
    void tasaDiaria_febreroBisiesto_divideEntre29() {
        PronosticoMensual p = new PronosticoMensual(YearMonth.of(2028, 2), List.of(new BigDecimal("29.00")));

        assertThat(p.tasaDiaria(LocalDate.of(2028, 2, 29))).isCloseTo(1.0, within(EPS));
    }
}
