package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.analitica.modelo.FuenteDemanda;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Sugerencia de compra (s, S) (ADR-010, decisión 6). */
class CalculadoraSugerenciaCompraTest {

    private final CalculadoraSugerenciaCompra calculadora = new CalculadoraSugerenciaCompra(30);

    private static final LocalDate CORTE_25_OCT = LocalDate.of(2026, 10, 25);

    private static PronosticoMensual pronostico(String oct, String nov, String dic) {
        return new PronosticoMensual(YearMonth.of(2026, 10),
                List.of(new BigDecimal(oct), new BigDecimal(nov), new BigDecimal(dic)));
    }

    /** Pronóstico de la variante: octubre 31 (1/día), noviembre 60 (2/día), diciembre 93 (3/día). */
    private static final PronosticoMensual OCT_NOV_DIC = pronostico("31.00", "60.00", "93.00");

    /** Variante del ejemplo del plan (ROP 31 de A4, L = 15) con pronóstico. */
    private static InsumoSugerencia conPronostico(long id, int disponible, int enCamino, int lote) {
        return new InsumoSugerencia(id, 31, 4, disponible, enCamino, 15, lote, OCT_NOV_DIC, 0.0);
    }

    private static InsumoSugerencia historica(long id, Integer rop, int stockMinimo, int disponible, int enCamino,
            double demandaMedia) {
        return new InsumoSugerencia(id, rop, stockMinimo, disponible, enCamino, 15, 1, null, demandaMedia);
    }

    private LineaSugerencia unica(InsumoSugerencia insumo) {
        Optional<LineaSugerencia> linea = calculadora.sugerir(insumo, CORTE_25_OCT);
        assertThat(linea).isPresent();
        return linea.get();
    }

    // ---------------------------------------------------------------- ejemplo del plan

    /*
     * Ejemplo del plan (continúa el ROP 31 de A4). Corte 25-oct, L = 15, R = 30:
     *   ventana de R = [25-oct + 15, + 30) = [9-nov, 9-dic) → 9..30 nov = 22 días × 2 + 1..8 dic = 8 días × 3 = 44 + 24 = 68
     *   S = 31 + 68 = 99 ; posición = 10 + 0 = 10 ; faltante = 99 − 10 = 89 ; lote 6 → ceil(89/6) = 15 × 6 = 90.
     */
    @Test
    void sugerir_ejemploDelPlan_90() {
        LineaSugerencia l = unica(conPronostico(7, 10, 0, 6));

        assertThat(l.varianteId()).isEqualTo(7);
        assertThat(l.fuente()).isEqualTo(FuenteDemanda.PRONOSTICO);
        assertThat(l.umbral()).isEqualTo(31);
        assertThat(l.posicion()).isEqualTo(10);
        assertThat(l.demandaPeriodo().toPlainString()).isEqualTo("68.00");
        assertThat(l.nivelObjetivo().toPlainString()).isEqualTo("99.00");
        assertThat(l.faltante()).isEqualTo(89);
        assertThat(l.cantidad()).isEqualTo(90);
        assertThat(l.justificacion())
                .isEqualTo("ROP 31 + demanda 30 d 68 − disponible 10 − en camino 0 = 89 → lote 6: 90");
    }

    /*
     * Equivalencia con la fórmula de referencia (docs/especificaciones/algoritmos-analitica.md, ADR-010):
     * S = μ(L + R) + SS. Con μL = 23 y SS = 7,67 (A4):
     *   μL + SS + demanda R = 23 + 7,67 + 68 = 98,67 y S = ceil(23 + 7,67) + 68 = 31 + 68 = 99 (solo el ceil del ROP).
     * Aquí se verifica el lado práctico: lo que se pide deja la posición en S.
     */
    @Test
    void sugerir_sinLote_posicionMasCantidadIgualAS() {
        LineaSugerencia l = unica(conPronostico(7, 10, 0, 1));

        assertThat(l.cantidad()).isEqualTo(89);
        assertThat(l.posicion() + l.cantidad()).isEqualTo(99);
        assertThat(l.justificacion()).isEqualTo("ROP 31 + demanda 30 d 68 − disponible 10 − en camino 0 = 89");
    }

    // ---------------------------------------------------------------- lote mínimo

    /*
     * faltante 89: lote 12 → ceil(89/12) = 8 → 96 ; lote 89 → 89 ; lote 100 → 100.
     * Disponible 9 → faltante 90, ya múltiplo de 6 → 90 (no sube otro lote).
     */
    @Test
    void sugerir_loteMinimo_redondeaHaciaArribaAlMultiplo() {
        assertThat(unica(conPronostico(1, 10, 0, 12)).cantidad()).isEqualTo(96);
        assertThat(unica(conPronostico(1, 10, 0, 89)).cantidad()).isEqualTo(89);
        assertThat(unica(conPronostico(1, 10, 0, 100)).cantidad()).isEqualTo(100);

        LineaSugerencia exacta = unica(conPronostico(1, 9, 0, 6));
        assertThat(exacta.faltante()).isEqualTo(90);
        assertThat(exacta.cantidad()).isEqualTo(90);
        assertThat(exacta.justificacion()).endsWith("= 90 → lote 6: 90");
    }

    // ---------------------------------------------------------------- posición y candidatas

    /*
     * Lo que viene en camino cuenta en la posición:
     *   disponible 4 + en camino 6 = 10 → mismo faltante 89 → 90.
     *   posición 31 = umbral → candidata: 99 − 31 = 68 → lote 6 → 72.
     *   posición 32 > umbral 31 → no es candidata (aunque S − posición = 67 > 0).
     */
    @Test
    void sugerir_posicionIncluyeEnCamino_yFronteraDelUmbral() {
        LineaSugerencia conTransito = unica(conPronostico(1, 4, 6, 6));
        assertThat(conTransito.cantidad()).isEqualTo(90);
        assertThat(conTransito.justificacion())
                .isEqualTo("ROP 31 + demanda 30 d 68 − disponible 4 − en camino 6 = 89 → lote 6: 90");

        assertThat(unica(conPronostico(1, 25, 6, 6)).cantidad()).isEqualTo(72);
        assertThat(calculadora.sugerir(conPronostico(1, 20, 12, 6), CORTE_25_OCT)).isEmpty();
    }

    // ---------------------------------------------------------------- SIN_DEMANDA

    /*
     * Variante SIN_DEMANDA agotada (ROP 0, disponible 0): posición 0 ≤ 0 es candidata, pero
     *   con pronóstico en 0: demanda R = 0 → S = 0 + 0 = 0 → faltante 0 → no entra.
     *   sin pronóstico y d̄ = 0: demanda R = 0 × 30 = 0 → S = 0 → no entra.
     */
    @Test
    void sugerir_sinDemandaAgotada_noEntra() {
        PronosticoMensual ceros = pronostico("0", "0", "0");
        assertThat(calculadora.sugerir(new InsumoSugerencia(1, 0, 3, 0, 0, 15, 6, ceros, 0.0), CORTE_25_OCT))
                .isEmpty();
        assertThat(calculadora.sugerir(historica(2, 0, 3, 0, 0, 0.0), CORTE_25_OCT)).isEmpty();
    }

    /*
     * ROP 0 por SIN_DEMANDA en el lead time, pero el pronóstico tiene demanda después (estacional):
     *   octubre 0, noviembre 0, diciembre 31 (1/día). μL del 25-oct al 8-nov = 0 → ROP 0.
     *   Ventana R [9-nov, 9-dic): 22 × 0 + 8 × 1 = 8 → S = 8 → faltante 8 (la fórmula, no el método, decide).
     */
    @Test
    void sugerir_ropCeroConDemandaPosteriorAlLeadTime_entra() {
        LineaSugerencia l = unica(new InsumoSugerencia(1, 0, 3, 0, 0, 15, 1, pronostico("0", "0", "31"), 0.0));

        assertThat(l.cantidad()).isEqualTo(8);
        assertThat(l.justificacion()).isEqualTo("ROP 0 + demanda 30 d 8 − disponible 0 − en camino 0 = 8");
    }

    // ---------------------------------------------------------------- ROP null (SIN_HISTORIA)

    /*
     * ROP null, sin pronóstico y d̄ = 0 (variante nueva sin ventas): umbral = stockMinimo 5; posición 2 ≤ 5.
     *   S = 2 × stockMinimo = 10 → faltante 10 − 2 = 8.
     */
    @Test
    void sugerir_ropNullSinDemanda_dobleDelStockMinimo() {
        LineaSugerencia l = unica(historica(1, null, 5, 2, 0, 0.0));

        assertThat(l.umbral()).isEqualTo(5);
        assertThat(l.fuente()).isEqualTo(FuenteDemanda.HISTORICA);
        assertThat(l.nivelObjetivo().toPlainString()).isEqualTo("10.00");
        assertThat(l.cantidad()).isEqualTo(8);
        assertThat(l.justificacion())
                .isEqualTo("Sin ROP ni demanda: 2 × stock mínimo 5 − disponible 2 − en camino 0 = 8");
    }

    /*
     * ROP null con pronóstico del producto pero participación 0 (talla nueva de un producto con historia):
     *   demanda R = 0 → mismo respaldo S = 2 × 4 = 8 ; posición 0 → 8 → lote 6 → 12.
     */
    @Test
    void sugerir_ropNullConPronosticoEnCero_dobleDelStockMinimo() {
        LineaSugerencia l = unica(new InsumoSugerencia(1, null, 4, 0, 0, 15, 6, pronostico("0", "0", "0"), 0.0));

        assertThat(l.fuente()).isEqualTo(FuenteDemanda.PRONOSTICO);
        assertThat(l.cantidad()).isEqualTo(12);
        assertThat(l.justificacion())
                .isEqualTo("Sin ROP ni demanda: 2 × stock mínimo 4 − disponible 0 − en camino 0 = 8 → lote 6: 12");
    }

    /*
     * ROP null con algo de historia corta (d̄ = 0,5/día, < 30 días):
     *   umbral = stockMinimo 5 ; demanda R = 0,5 × 30 = 15 ; S = 20 ; posición 3 → 17.
     */
    @Test
    void sugerir_ropNullConDemandaHistorica_stockMinimoMasDemanda() {
        LineaSugerencia l = unica(historica(1, null, 5, 3, 0, 0.5));

        assertThat(l.cantidad()).isEqualTo(17);
        assertThat(l.justificacion())
                .isEqualTo("Stock mínimo 5 + demanda histórica 30 d 15 − disponible 3 − en camino 0 = 17");
    }

    /* ROP null: disponible 6 > stockMinimo 5 → no es candidata. stockMinimo 0 sin demanda: S = 0 → no entra. */
    @Test
    void sugerir_ropNull_noCandidatasOSinCantidad() {
        assertThat(calculadora.sugerir(historica(1, null, 5, 6, 0, 0.0), CORTE_25_OCT)).isEmpty();
        assertThat(calculadora.sugerir(historica(2, null, 0, 0, 0, 0.0), CORTE_25_OCT)).isEmpty();
    }

    // ---------------------------------------------------------------- demanda histórica

    /*
     * Sin pronóstico (producto SIN_HISTORIA en meses o fallo del pronóstico), ROP 12, d̄ = 0,4:
     *   demanda R = 0,4 × 30 = 12 ; S = 24 ; posición = 3 + 2 = 5 → faltante 19.
     */
    @Test
    void sugerir_sinPronostico_usaDemandaHistorica() {
        LineaSugerencia l = unica(historica(1, 12, 4, 3, 2, 0.4));

        assertThat(l.fuente()).isEqualTo(FuenteDemanda.HISTORICA);
        assertThat(l.demandaPeriodo().toPlainString()).isEqualTo("12.00");
        assertThat(l.cantidad()).isEqualTo(19);
        assertThat(l.justificacion())
                .isEqualTo("ROP 12 + demanda histórica 30 d 12 − disponible 3 − en camino 2 = 19");
    }

    /*
     * S − posición fraccionario: ROP 10, d̄ = 0,42 → demanda 12,6 ; S = 22,6 ; posición 4 → 18,6 → ceil 19.
     * Tolerancia: d̄ = 0,1 → 0,1 × 30 = 3,0000000000000004 en double; ROP 2, posición 2 → faltante 3 (no 4).
     */
    @Test
    void sugerir_faltanteFraccionario_seRedondeaHaciaArribaConTolerancia() {
        LineaSugerencia fraccion = unica(historica(1, 10, 4, 4, 0, 0.42));
        assertThat(fraccion.cantidad()).isEqualTo(19);
        assertThat(fraccion.justificacion())
                .isEqualTo("ROP 10 + demanda histórica 30 d 12,6 − disponible 4 − en camino 0 = 18,6 → 19");

        LineaSugerencia casiEntero = unica(historica(2, 2, 4, 2, 0, 0.1));
        assertThat(casiEntero.cantidad()).isEqualTo(3);
        assertThat(casiEntero.justificacion())
                .isEqualTo("ROP 2 + demanda histórica 30 d 3 − disponible 2 − en camino 0 = 3");
    }

    // ---------------------------------------------------------------- horizonte

    /*
     * Horizonte agotado: corte 25-dic, L = 15 → ventana R desde el 9-ene (más allá de diciembre) → tasa del último mes
     *   (93/31 = 3/día) × 30 = 90 ; ROP 50, posición 50 → S = 140 → 90.
     */
    @Test
    void sugerir_horizonteAgotado_usaLaTasaDelUltimoMes() {
        InsumoSugerencia insumo = new InsumoSugerencia(1, 50, 4, 50, 0, 15, 1, OCT_NOV_DIC, 0.0);
        LineaSugerencia l = calculadora.sugerir(insumo, LocalDate.of(2026, 12, 25)).orElseThrow();

        assertThat(l.demandaPeriodo().toPlainString()).isEqualTo("90.00");
        assertThat(l.cantidad()).isEqualTo(90);
    }

    /* R configurable: con R = 15 la ventana es [9-nov, 24-nov) → 15 × 2 = 30 ; S = 61 ; posición 10 → 51 → lote 6 → 54. */
    @Test
    void sugerir_periodoDeRevisionConfigurable() {
        LineaSugerencia l = new CalculadoraSugerenciaCompra(15).sugerir(conPronostico(1, 10, 0, 6), CORTE_25_OCT)
                .orElseThrow();

        assertThat(l.cantidad()).isEqualTo(54);
        assertThat(l.justificacion())
                .isEqualTo("ROP 31 + demanda 15 d 30 − disponible 10 − en camino 0 = 51 → lote 6: 54");
    }

    // ---------------------------------------------------------------- lista

    /* Orden ascendente de id; las no candidatas (id 2) y las de cantidad 0 (id 4) no aparecen. */
    @Test
    void sugerir_lista_ordenPorIdYSoloConCantidad() {
        List<LineaSugerencia> lineas = calculadora.sugerir(List.of(
                conPronostico(9, 10, 0, 6),
                conPronostico(2, 40, 0, 6),
                historica(4, 0, 3, 0, 0, 0.0),
                historica(1, 12, 4, 3, 2, 0.4)), CORTE_25_OCT);

        assertThat(lineas).extracting(LineaSugerencia::varianteId).containsExactly(1L, 9L);
        assertThat(lineas).extracting(LineaSugerencia::cantidad).containsExactly(19, 90);
        assertThat(calculadora.sugerir(List.of(), CORTE_25_OCT)).isEmpty();
    }

    @Test
    void sugerir_lista_idRepetido_rechaza() {
        assertThatThrownBy(() -> calculadora.sugerir(
                List.of(conPronostico(3, 10, 0, 6), conPronostico(3, 5, 0, 6)), CORTE_25_OCT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3");
    }

    // ---------------------------------------------------------------- casos borde adicionales

    /* L = 0: la ventana de R empieza en el corte: [25-oct, 24-nov) = 7 × 1 + 23 × 2 = 53 ; S = 84 ; 84 − 10 = 74. */
    @Test
    void sugerir_leadTimeCero_laVentanaEmpiezaEnElCorte() {
        InsumoSugerencia insumo = new InsumoSugerencia(1, 31, 4, 10, 0, 0, 1, OCT_NOV_DIC, 0.0);

        LineaSugerencia l = calculadora.sugerir(insumo, CORTE_25_OCT).orElseThrow();

        assertThat(l.demandaPeriodo().toPlainString()).isEqualTo("53.00");
        assertThat(l.nivelObjetivo().toPlainString()).isEqualTo("84.00");
        assertThat(l.cantidad()).isEqualTo(74);
    }

    /* ROP positivo, sin demanda y posición == umbral: S = umbral → faltante 0 → no entra. */
    @Test
    void sugerir_ropPositivoSinDemandaYPosicionEnElUmbral_noEntra() {
        assertThat(calculadora.sugerir(historica(1, 10, 4, 6, 4, 0.0), CORTE_25_OCT)).isEmpty();
    }

    /* ROP positivo, sin demanda y posición por debajo: S = ROP → se pide hasta el ROP (no el doble del mínimo). */
    @Test
    void sugerir_ropPositivoSinDemanda_pideHastaElRop() {
        LineaSugerencia l = unica(historica(1, 10, 4, 3, 0, 0.0));

        assertThat(l.nivelObjetivo().toPlainString()).isEqualTo("10.00");
        assertThat(l.cantidad()).isEqualTo(7);
    }

    /* ROP null y stockMinimo 0 con demanda histórica: S = 0 + demanda (no es el respaldo) ; 0,2 × 30 = 6 ; posición 0 → 6. */
    @Test
    void sugerir_ropNullStockMinimoCeroConDemanda_usaLaDemanda() {
        LineaSugerencia l = unica(historica(1, null, 0, 0, 0, 0.2));

        assertThat(l.umbral()).isZero();
        assertThat(l.cantidad()).isEqualTo(6);
        assertThat(l.justificacion())
                .isEqualTo("Stock mínimo 0 + demanda histórica 30 d 6 − disponible 0 − en camino 0 = 6");
    }

    /* ROP null, stockMinimo 5 y en camino 6 con disponible 0: posición 6 > umbral 5 → no es candidata. */
    @Test
    void sugerir_ropNullEnCaminoSuperaElUmbral_noEntra() {
        assertThat(calculadora.sugerir(historica(1, null, 5, 0, 6, 0.0), CORTE_25_OCT)).isEmpty();
    }

    /* Mismo conjunto en otro orden y llamadas repetidas dan el mismo resultado; la lista de entrada no se modifica. */
    @Test
    void sugerir_lista_esDeterministaYNoMutaLaEntrada() {
        List<InsumoSugerencia> entrada = new java.util.ArrayList<>(List.of(
                conPronostico(30, 10, 0, 6), historica(5, 12, 4, 3, 2, 0.4), conPronostico(12, 5, 0, 1)));
        List<InsumoSugerencia> copia = List.copyOf(entrada);
        List<InsumoSugerencia> invertida = new java.util.ArrayList<>(entrada);
        java.util.Collections.reverse(invertida);

        List<LineaSugerencia> primera = calculadora.sugerir(entrada, CORTE_25_OCT);
        List<LineaSugerencia> segunda = calculadora.sugerir(entrada, CORTE_25_OCT);
        List<LineaSugerencia> alReves = calculadora.sugerir(invertida, CORTE_25_OCT);

        assertThat(primera).extracting(LineaSugerencia::varianteId).containsExactly(5L, 12L, 30L);
        assertThat(segunda).isEqualTo(primera);
        assertThat(alReves).isEqualTo(primera);
        assertThat(entrada).isEqualTo(copia);
    }

    /* El repetido no tiene que ser adyacente en la entrada. */
    @Test
    void sugerir_lista_idRepetidoNoAdyacente_rechaza() {
        assertThatThrownBy(() -> calculadora.sugerir(List.of(
                conPronostico(8, 10, 0, 6), conPronostico(2, 10, 0, 6), conPronostico(8, 1, 0, 6)), CORTE_25_OCT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8");
    }

    @Test
    void sugerir_argumentosNulos_lanzanNullPointerException() {
        assertThatThrownBy(() -> calculadora.sugerir((List<InsumoSugerencia>) null, CORTE_25_OCT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculadora.sugerir(List.of(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculadora.sugerir((InsumoSugerencia) null, CORTE_25_OCT))
                .isInstanceOf(NullPointerException.class);
    }

    /* La justificación cabe en detalle_plan.justificacion (255) incluso con cifras grandes. */
    @Test
    void sugerir_justificacion_cabeEn255Caracteres() {
        LineaSugerencia l = unica(new InsumoSugerencia(Long.MAX_VALUE, 99_999, 99_999, 0, 0, 365, 1_000, null, 999.99));

        assertThat(l.justificacion().length()).isLessThanOrEqualTo(255);
        assertThat(l.cantidad() % 1_000).isZero();
    }

    // ---------------------------------------------------------------- validaciones

    @Test
    void validaciones_rechazanEntradasInvalidas() {
        assertThatThrownBy(() -> new CalculadoraSugerenciaCompra(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InsumoSugerencia(1, 5, 1, 0, 0, 15, 0, null, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InsumoSugerencia(1, -1, 1, 0, 0, 15, 1, null, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InsumoSugerencia(1, 5, 1, -1, 0, 15, 1, null, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InsumoSugerencia(1, 5, 1, 0, -1, 15, 1, null, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InsumoSugerencia(1, 5, 1, 0, 0, 15, 1, null, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InsumoSugerencia(1, 5, 1, 0, 0, 15, 1, null, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
