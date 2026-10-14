package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import co.livorno.controlstock.catalogo.modelo.Talla;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Pruebas puras (sin Spring) del modelo de demanda de los datos semilla. */
class ModeloDemandaTest {

    private static final LocalDate FIN = LocalDate.of(2025, 12, 31);
    private static final LocalDate INICIO = FIN.minusMonths(24).plusDays(1);  // 2024-01-01
    private static final double BASE = 12;
    private static final List<ProductoSemilla> CATALOGO = CatalogoSemilla.productos();

    /** Resultado de simular 24 meses con la semilla 42 (se calcula una vez para todas las pruebas). */
    private static Simulacion sim;

    @BeforeAll
    static void simular() {
        sim = Simulacion.correr(new ModeloDemanda(CATALOGO, INICIO, FIN, BASE, new Random(42)));
    }

    // ------------------------------------------------------------------ Poisson

    @Test
    void poisson_ejemploAMano_conUniformesFijos() {
        // λ = 1 → límite = e^(−1) ≈ 0,3679. Uniformes 0,9; 0,5; 0,6:
        //   p = 0,9            > 0,3679 → k = 1
        //   p = 0,9 × 0,5 = 0,45 > 0,3679 → k = 2
        //   p = 0,45 × 0,6 = 0,27 ≤ 0,3679 → para. Resultado k = 2.
        assertThat(ModeloDemanda.poisson(1.0, uniformes(0.9, 0.5, 0.6))).isEqualTo(2);
        // Primer uniforme ya bajo el límite (0,2 ≤ 0,3679) → 0.
        assertThat(ModeloDemanda.poisson(1.0, uniformes(0.2))).isZero();
    }

    @Test
    void poisson_lambdaCero_devuelveCeroSinConsumirAleatorios() {
        Random sinUso = uniformes();  // lanza si se pide un número
        assertThat(ModeloDemanda.poisson(0.0, sinUso)).isZero();
    }

    @Test
    void poisson_lambdaInvalido_lanza() {
        Random r = new Random(42);
        assertThatThrownBy(() -> ModeloDemanda.poisson(-0.1, r)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModeloDemanda.poisson(Double.NaN, r)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModeloDemanda.poisson(Double.POSITIVE_INFINITY, r))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void poisson_mediaYVarianza_dentroDel5PorCiento_en10milMuestras() {
        // En una Poisson, media = varianza = λ. 75 ejercita la suma por tramos (30 + 30 + 15).
        for (double lambda : new double[] {0.5, 4, 12, 75}) {
            Random r = new Random(42);
            int n = 10_000;
            double suma = 0;
            double sumaCuadrados = 0;
            for (int i = 0; i < n; i++) {
                int x = ModeloDemanda.poisson(lambda, r);
                assertThat(x).isNotNegative();
                suma += x;
                sumaCuadrados += (double) x * x;
            }
            double media = suma / n;
            double varianza = (sumaCuadrados - n * media * media) / (n - 1);
            assertThat(media).as("media λ=%s", lambda).isCloseTo(lambda, within(0.05 * lambda));
            assertThat(varianza).as("varianza λ=%s", lambda).isCloseTo(lambda, within(0.10 * lambda));
        }
    }

    @Test
    void poisson_mismaSemilla_mismaSecuencia() {
        Random a = new Random(42);
        Random b = new Random(42);
        for (int i = 0; i < 1_000; i++) {
            assertThat(ModeloDemanda.poisson(7.3, a)).isEqualTo(ModeloDemanda.poisson(7.3, b));
        }
    }

    // ------------------------------------------------------------------ pesos escalonados y participación

    @Test
    void pesosEscalonados_ejemploAMano_conTreintaProductos() {
        // Grupos 6 / 6 / 18. Cabeza: perfil 1,6 1,48 1,36 1,24 1,12 1,0 (suma 7,8) × 0,78 / 7,8
        //   = 0,16 0,148 0,136 0,124 0,112 0,10 (suma 0,78).
        // Medio: 0,12 / 6 = 0,02 cada uno. Cola: 0,10 / 18 = 0,005556 cada uno. Total 1.
        double[] w = ModeloDemanda.pesosEscalonados(30);
        double[] cabeza = {0.16, 0.148, 0.136, 0.124, 0.112, 0.10};
        for (int i = 0; i < 6; i++) {
            assertThat(w[i]).as("rango %d", i + 1).isCloseTo(cabeza[i], within(1e-12));
        }
        for (int i = 6; i < 12; i++) {
            assertThat(w[i]).as("rango %d", i + 1).isCloseTo(0.02, within(1e-12));
        }
        for (int i = 12; i < 30; i++) {
            assertThat(w[i]).as("rango %d", i + 1).isCloseTo(0.10 / 18, within(1e-12));
        }
        assertThat(Arrays.stream(w).sum()).isCloseTo(1.0, within(1e-12));
        assertThat(ModeloDemanda.participacionSuperior(w, 0.2)).isCloseTo(0.78, within(1e-12));
    }

    @Test
    void pesosEscalonados_catalogosPequenos_renormalizanLosGruposPresentes() {
        // n = 1: solo cabeza → 1.
        assertThat(ModeloDemanda.pesosEscalonados(1)).hasSize(1);
        assertThat(ModeloDemanda.pesosEscalonados(1)[0]).isCloseTo(1.0, within(1e-12));
        // n = 2: cabeza 1 (0,78), medio round(0,4) = 0, cola 1 (0,10) → 0,78 / 0,88 = 0,886364 y 0,10 / 0,88 = 0,113636.
        double[] dos = ModeloDemanda.pesosEscalonados(2);
        assertThat(dos[0]).isCloseTo(0.78 / 0.88, within(1e-12));
        assertThat(dos[1]).isCloseTo(0.10 / 0.88, within(1e-12));
        // n = 5: cabeza 1 (0,78), medio 1 (0,12), cola 3 (0,10 / 3 cada uno).
        double[] cinco = ModeloDemanda.pesosEscalonados(5);
        assertThat(cinco[0]).isCloseTo(0.78, within(1e-12));
        assertThat(cinco[1]).isCloseTo(0.12, within(1e-12));
        assertThat(cinco[4]).isCloseTo(0.10 / 3, within(1e-12));
        // Siempre no crecientes por rango.
        for (int n = 1; n <= 40; n++) {
            double[] w = ModeloDemanda.pesosEscalonados(n);
            assertThat(Arrays.stream(w).sum()).as("n=%d", n).isCloseTo(1.0, within(1e-12));
            for (int i = 1; i < n; i++) {
                assertThat(w[i]).as("n=%d rango %d", n, i + 1).isLessThanOrEqualTo(w[i - 1] + 1e-15);
            }
        }
        assertThatThrownBy(() -> ModeloDemanda.pesosEscalonados(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void participacionSuperior_ejemploAMano_yCasosBorde() {
        // [50, 30, 10, 5, 5], 20 % de 5 = 1 elemento → 50 / 100 = 0,5.
        assertThat(ModeloDemanda.participacionSuperior(new double[] {10, 50, 5, 30, 5}, 0.2)).isEqualTo(0.5);
        // 40 % de 5 = 2 → (50 + 30) / 100.
        assertThat(ModeloDemanda.participacionSuperior(new double[] {10, 50, 5, 30, 5}, 0.4)).isEqualTo(0.8);
        assertThat(ModeloDemanda.participacionSuperior(new double[] {}, 0.2)).isZero();
        assertThat(ModeloDemanda.participacionSuperior(new double[] {0, 0, 0}, 0.2)).isZero();
        // Negativo (más devoluciones que ventas) cuenta como 0: [4, −2] → 4 / 4.
        assertThat(ModeloDemanda.participacionSuperior(new double[] {4, -2}, 0.5)).isEqualTo(1.0);
        // Serie constante: 2 de 10 iguales → 0,2.
        assertThat(ModeloDemanda.participacionSuperior(new double[] {3, 3, 3, 3, 3, 3, 3, 3, 3, 3}, 0.2))
                .isCloseTo(0.2, within(1e-12));
        assertThatThrownBy(() -> ModeloDemanda.participacionSuperior(new double[] {1}, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ factores y λ

    @Test
    void factores_deMesYDia_segunElSkill() {
        assertThat(ModeloDemanda.factorMes(Month.DECEMBER)).isEqualTo(1.6);
        assertThat(ModeloDemanda.factorMes(Month.JUNE)).isEqualTo(1.25);
        assertThat(ModeloDemanda.factorMes(Month.FEBRUARY)).isEqualTo(0.7);
        assertThat(ModeloDemanda.factorDiaSemana(DayOfWeek.MONDAY)).isEqualTo(0.85);
        assertThat(ModeloDemanda.factorDiaSemana(DayOfWeek.FRIDAY)).isEqualTo(1.1);
        assertThat(ModeloDemanda.factorDiaSemana(DayOfWeek.SATURDAY)).isEqualTo(1.3);
        assertThat(ModeloDemanda.factorDiaSemana(DayOfWeek.SUNDAY)).isEqualTo(1.0);
        // Promedio semanal = (4 × 0,85 + 1,1 + 1,3 + 1,0) / 7 = 6,8 / 7.
        double semana = 0;
        for (DayOfWeek d : DayOfWeek.values()) {
            semana += ModeloDemanda.factorDiaSemana(d);
        }
        assertThat(semana / 7).isCloseTo(6.8 / 7, within(1e-12));
    }

    @Test
    void tendencia_unoPuntoCeroQuinceALaMes() {
        assertThat(ModeloDemanda.tendencia(0)).isEqualTo(1.0);
        // 1,015^12 = e^(12 × ln 1,015) = e^(12 × 0,014889) = e^0,178665 ≈ 1,19562.
        assertThat(ModeloDemanda.tendencia(12)).isCloseTo(1.19562, within(1e-5));
        assertThatThrownBy(() -> ModeloDemanda.tendencia(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lambdaTickets_ejemploAMano() {
        ModeloDemanda m = new ModeloDemanda(CATALOGO, INICIO, FIN, BASE, new Random(42));
        // Sábado 2024-12-07, mes 11 desde enero 2024:
        // λ = 12 × 1,6 (dic) × 1,3 (sáb) × 1,015^11 = 24,96 × 1,17795 ≈ 29,40.
        LocalDate dia = LocalDate.of(2024, 12, 7);
        assertThat(dia.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(m.mesesDesdeInicio(dia)).isEqualTo(11);
        assertThat(m.lambdaTickets(dia)).isCloseTo(29.40, within(0.01));
    }

    @Test
    void lambdaMensual_diciembreMayorQueJunioMayorQueFebrero() {
        ModeloDemanda m = new ModeloDemanda(CATALOGO, INICIO, FIN, BASE, new Random(42));
        double dic = lambdaDelMes(m, 2024, 12);
        double jun = lambdaDelMes(m, 2024, 6);
        double feb = lambdaDelMes(m, 2024, 2);
        assertThat(dic).isGreaterThan(jun);
        assertThat(jun).isGreaterThan(feb);
    }

    @Test
    void unidadesSimuladas_diciembreMayorQueFebrero_enAmbosAnios() {
        // Unidades por mes calendario de cada año simulado (año 0 = 2024, año 1 = 2025).
        for (int anio = 0; anio < 2; anio++) {
            assertThat(sim.unidadesPorMes[anio][12]).isGreaterThan(sim.unidadesPorMes[anio][6]);
            assertThat(sim.unidadesPorMes[anio][6]).isGreaterThan(sim.unidadesPorMes[anio][2]);
        }
        // Tendencia: el año 2 vende más que el año 1.
        assertThat(sumar(sim.unidadesPorMes[1])).isGreaterThan(sumar(sim.unidadesPorMes[0]));
    }

    // ------------------------------------------------------------------ simulación de 24 meses

    @Test
    void mismaSemilla_mismaSecuenciaDeTickets() {
        ModeloDemanda a = new ModeloDemanda(CATALOGO, INICIO, FIN, BASE, new Random(42));
        ModeloDemanda b = new ModeloDemanda(CATALOGO, INICIO, FIN, BASE, new Random(42));
        ModeloDemanda otra = new ModeloDemanda(CATALOGO, INICIO, FIN, BASE, new Random(43));
        boolean algunaDiferencia = false;
        for (LocalDate d = INICIO; d.isBefore(INICIO.plusDays(60)); d = d.plusDays(1)) {
            List<ModeloDemanda.Ticket> ta = a.ticketsDelDia(d);
            assertThat(b.ticketsDelDia(d)).isEqualTo(ta);
            algunaDiferencia |= !otra.ticketsDelDia(d).equals(ta);
        }
        assertThat(algunaDiferencia).as("otra semilla produce otra secuencia").isTrue();
    }

    @Test
    void pareto_top20PorCientoDeProductos_concentraAlMenos70PorCientoDeLasUnidades() {
        // Pesos teóricos de la cabeza: 78 %. Con semilla 42 la simulación da ≈ 77,6 %: elegir productos distintos
        // dentro de un ticket aplana un poco la cabeza.
        double participacion = ModeloDemanda.participacionSuperior(sim.unidadesPorProducto, 0.2);
        assertThat(participacion).isGreaterThanOrEqualTo(0.70).isBetween(0.75, 0.82);
    }

    @Test
    void cabezaPareja_ningunProductoSuperaEl18PorCientoDeLasUnidades() {
        // Teórico: el mayor tiene 16 % (rango 1); en la simulación ≈ 15,7 %.
        double total = sumar(sim.unidadesPorProducto);
        double mayor = Arrays.stream(sim.unidadesPorProducto).max().orElseThrow();
        assertThat(mayor / total).isLessThanOrEqualTo(0.18).isGreaterThan(0.10);
    }

    @Test
    void claseAPorIngreso_tieneEntre5y7Productos() {
        // ABC del skill sobre el ingreso simulado por producto (precio de lista × unidades): A = acumulado ≤ 80 %,
        // el primero siempre es A. Con la semilla 42 quedan 6 productos A.
        double[] ingresos = sim.ingresoPorProducto.clone();
        Arrays.sort(ingresos);
        double total = sumar(ingresos);
        double acumulado = 0;
        int claseA = 0;
        for (int i = ingresos.length - 1; i >= 0; i--) {
            acumulado += ingresos[i];
            if (claseA > 0 && acumulado / total > 0.80) {
                break;
            }
            claseA++;
        }
        assertThat(claseA).isBetween(5, 7);
    }

    @Test
    void totalSimulado_coincideConLaDemandaEsperada_dentroDel3PorCiento() {
        ModeloDemanda m = new ModeloDemanda(CATALOGO, INICIO, FIN, BASE, new Random(42));
        long dias = INICIO.datesUntil(FIN.plusDays(1)).count();
        double esperado = 0;
        for (ProductoSemilla p : CATALOGO) {
            esperado += m.demandaEsperada(p, INICIO, (int) dias);
        }
        assertThat(sumar(sim.unidadesPorProducto)).isCloseTo(esperado, within(0.03 * esperado));
    }

    @Test
    void estancados_ceroUnidadesEnLosUltimos3Meses_yVentasAntes() {
        ModeloDemanda m = new ModeloDemanda(CATALOGO, INICIO, FIN, BASE, new Random(42));
        assertThat(m.inicioEstancamiento()).isEqualTo(LocalDate.of(2025, 10, 1));
        List<ProductoSemilla> estancados = CATALOGO.stream().filter(ProductoSemilla::estancado).toList();
        assertThat(estancados).hasSizeBetween(2, 3);
        for (ProductoSemilla p : estancados) {
            int i = CATALOGO.indexOf(p);
            assertThat(sim.unidadesEstancamiento[i]).as("%s en los últimos 3 meses", p.codigo()).isZero();
            assertThat(sim.unidadesPorProducto[i] - sim.unidadesEstancamiento[i]).as("%s antes", p.codigo())
                    .isPositive();
            assertThat(m.demandaEsperadaDiaria(p, FIN)).isZero();
            assertThat(m.demandaEsperadaDiaria(p, LocalDate.of(2025, 9, 30))).isPositive();
        }
    }

    @Test
    void mezclaDeTallas_dentroDe3Puntos() {
        // Objetivo S 20 %, M 35 %, L 30 %, XL 15 % (líneas de prendas; los accesorios son UNICA).
        double total = sim.lineasPorTalla[Talla.S.ordinal()] + sim.lineasPorTalla[Talla.M.ordinal()]
                + sim.lineasPorTalla[Talla.L.ordinal()] + sim.lineasPorTalla[Talla.XL.ordinal()];
        assertThat(sim.lineasPorTalla[Talla.S.ordinal()] / total).isCloseTo(0.20, within(0.03));
        assertThat(sim.lineasPorTalla[Talla.M.ordinal()] / total).isCloseTo(0.35, within(0.03));
        assertThat(sim.lineasPorTalla[Talla.L.ordinal()] / total).isCloseTo(0.30, within(0.03));
        assertThat(sim.lineasPorTalla[Talla.XL.ordinal()] / total).isCloseTo(0.15, within(0.03));
        assertThat(sim.lineasPorTalla[Talla.XS.ordinal()]).isZero();
        assertThat(sim.lineasPorTalla[Talla.XXL.ordinal()]).isZero();
        assertThat(sim.lineasAccesorioNoUnica).isZero();
        // Primer color 50 %.
        assertThat(sim.lineasPrimerColor / sim.lineas).isCloseTo(0.50, within(0.03));
    }

    @Test
    void lineasCantidadesDescuentosYHoras_segunElPlan() {
        // Líneas por ticket 1/2/3 = 70/22/8 %; cantidad 2 en 15 %; descuento en 15 % de las líneas, 5–20 %.
        assertThat(sim.ticketsPorLineas[1] / sim.tickets).isCloseTo(0.70, within(0.03));
        assertThat(sim.ticketsPorLineas[2] / sim.tickets).isCloseTo(0.22, within(0.03));
        assertThat(sim.ticketsPorLineas[3] / sim.tickets).isCloseTo(0.08, within(0.03));
        assertThat(sim.lineasCantidadDos / sim.lineas).isCloseTo(0.15, within(0.02));
        assertThat(sim.lineasConDescuento / sim.lineas).isCloseTo(0.15, within(0.02));
        assertThat(sim.descuentoMinimo).isEqualTo(5);
        assertThat(sim.descuentoMaximo).isEqualTo(20);
        assertThat(sim.cantidadesFueraDeRango).isZero();
        assertThat(sim.productosRepetidosEnTicket).isZero();
        assertThat(sim.horasFueraDeRango).isZero();
        assertThat(sim.diasDesordenados).isZero();
    }

    // ------------------------------------------------------------------ esperanzas y casos borde

    @Test
    void pesoEfectivo_sumaUno_yRenormalizaSinLosEstancados() {
        ModeloDemanda m = new ModeloDemanda(CATALOGO, INICIO, FIN, BASE, new Random(42));
        for (LocalDate dia : List.of(INICIO, FIN)) {
            double suma = CATALOGO.stream().mapToDouble(p -> m.pesoEfectivo(p, dia)).sum();
            assertThat(suma).isCloseTo(1.0, within(1e-12));
        }
        ProductoSemilla estrella = CATALOGO.stream().filter(p -> p.rangoDemanda() == 1).findFirst().orElseThrow();
        assertThat(m.pesoEfectivo(estrella, FIN)).isGreaterThan(m.pesoEfectivo(estrella, INICIO));
    }

    @Test
    void demandaEsperadaDiaria_ejemploAMano() {
        // Catálogo de 2 productos: cabeza 1 (0,78) y cola 1 (0,10) → 0,78 / 0,88 = 0,886364 y 0,113636.
        // Lunes 2024-01-01 (mes 0): λ = 10 × 0,8 (ene) × 0,85 (lun) × 1 = 6,8 tickets.
        // Unidades del producto 1 = 6,8 × 1,38 × 1,15 × 0,886364 = 10,7916 × 0,886364 = 9,5653.
        ProductoSemilla a = producto("A01", 1, false);
        ProductoSemilla b = producto("B01", 2, false);
        ModeloDemanda m = new ModeloDemanda(List.of(a, b), INICIO, FIN, 10, new Random(1));
        assertThat(m.lambdaTickets(INICIO)).isCloseTo(6.8, within(1e-9));
        assertThat(m.demandaEsperadaDiaria(a, INICIO)).isCloseTo(9.5653, within(1e-3));
        assertThat(m.demandaEsperada(a, INICIO, 0)).isZero();
        assertThat(m.demandaEsperada(a, INICIO, 2)).isCloseTo(
                m.demandaEsperadaDiaria(a, INICIO) + m.demandaEsperadaDiaria(a, INICIO.plusDays(1)), within(1e-9));
    }

    @Test
    void participacionVariante_sumaUnoPorProducto() {
        // Ejemplo: talla M (0,35) × segundo de 3 colores (0,25) = 0,0875.
        ProductoSemilla p = CATALOGO.getFirst();
        assertThat(ModeloDemanda.participacionColor(1, 3)).isEqualTo(0.25);
        assertThat(ModeloDemanda.participacionColor(0, 1)).isEqualTo(1.0);
        for (ProductoSemilla producto : CATALOGO) {
            double suma = 0;
            for (Talla t : producto.tallas()) {
                for (int c = 0; c < producto.colores().size(); c++) {
                    suma += ModeloDemanda.participacionVariante(producto, t, c);
                }
            }
            assertThat(suma).as(producto.codigo()).isCloseTo(1.0, within(1e-12));
        }
        assertThatThrownBy(() -> ModeloDemanda.participacionVariante(p, Talla.UNICA, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModeloDemanda.participacionColor(3, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void precioConDescuento_redondeaALaCentena() {
        // 79.900 × 0,90 = 71.910 → 71.900; 59.900 × 0,95 = 56.905 → 56.900; 149.900 × 0,80 = 119.920 → 119.900.
        assertThat(ModeloDemanda.precioConDescuento(new BigDecimal("79900.00"), 10)).isEqualByComparingTo("71900");
        assertThat(ModeloDemanda.precioConDescuento(new BigDecimal("59900.00"), 5)).isEqualByComparingTo("56900");
        assertThat(ModeloDemanda.precioConDescuento(new BigDecimal("149900.00"), 20))
                .isEqualByComparingTo("119900");
        // 29.950 × 0,95 = 28.452,5 → 284,525 centenas → HALF_UP 285 → 28.500.
        assertThat(ModeloDemanda.precioConDescuento(new BigDecimal("29950"), 5)).isEqualByComparingTo("28500");
        assertThat(ModeloDemanda.precioConDescuento(new BigDecimal("79900"), 0)).isEqualTo(new BigDecimal("79900.00"));
        assertThat(ModeloDemanda.precioConDescuento(new BigDecimal("79900"), 10).scale()).isEqualTo(2);
        // Precio diminuto: el redondeo daría 0 → se conserva la lista (nunca 0 ni mayor que la lista).
        assertThat(ModeloDemanda.precioConDescuento(new BigDecimal("40"), 10)).isEqualByComparingTo("40");
        assertThatThrownBy(() -> ModeloDemanda.precioConDescuento(BigDecimal.ZERO, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModeloDemanda.precioConDescuento(BigDecimal.TEN, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_validaSusEntradas() {
        Random r = new Random(42);
        assertThatThrownBy(() -> new ModeloDemanda(List.of(), INICIO, FIN, BASE, r))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModeloDemanda(CATALOGO, FIN, INICIO, BASE, r))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModeloDemanda(CATALOGO, INICIO, FIN, 0, r))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModeloDemanda(CATALOGO, INICIO, FIN, Double.NaN, r))
                .isInstanceOf(IllegalArgumentException.class);
        // Rango repetido o fuera de 1..n.
        assertThatThrownBy(() -> new ModeloDemanda(List.of(producto("A01", 1, false), producto("B01", 1, false)),
                INICIO, FIN, BASE, r)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModeloDemanda(List.of(producto("A01", 3, false)), INICIO, FIN, BASE, r))
                .isInstanceOf(IllegalArgumentException.class);
        // Día anterior al inicio de la historia.
        ModeloDemanda m = new ModeloDemanda(CATALOGO, INICIO, FIN, BASE, r);
        assertThatThrownBy(() -> m.ticketsDelDia(INICIO.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> m.pesoEfectivo(producto("ZZ9", 1, false), INICIO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void historiaDeUnDia_yCatalogoTodoEstancado_sinTicketsVacios() {
        // Historia corta: inicio = fin; todo el día cae en el periodo de estancamiento.
        ProductoSemilla unico = producto("A01", 1, true);
        ModeloDemanda m = new ModeloDemanda(List.of(unico), FIN, FIN, 50, new Random(42));
        assertThat(m.lambdaTickets(FIN)).isPositive();
        assertThat(m.ticketsDelDia(FIN)).isEmpty();
        assertThat(m.pesoEfectivo(unico, FIN)).isZero();
        assertThat(m.demandaEsperadaDiaria(unico, FIN)).isZero();
    }

    // ------------------------------------------------------------------ utilidades

    private static double lambdaDelMes(ModeloDemanda m, int anio, int mes) {
        LocalDate desde = LocalDate.of(anio, mes, 1);
        return desde.datesUntil(desde.plusMonths(1)).mapToDouble(m::lambdaTickets).sum();
    }

    private static double sumar(double[] valores) {
        double s = 0;
        for (double v : valores) {
            s += v;
        }
        return s;
    }

    private static ProductoSemilla producto(String codigo, int rango, boolean estancado) {
        return new ProductoSemilla(codigo, "Prueba " + codigo, "Camisetas", new BigDecimal("59900.00"),
                new BigDecimal("26900.00"), List.of("NEGRO", "BLANCO"), 12, 1, rango, estancado, false);
    }

    /** {@link Random} que devuelve uniformes fijos en orden y falla si se le piden más. */
    private static Random uniformes(double... valores) {
        Deque<Double> cola = new ArrayDeque<>();
        for (double v : valores) {
            cola.add(v);
        }
        return new Random() {
            @Override
            public double nextDouble() {
                if (cola.isEmpty()) {
                    throw new AssertionError("Se pidieron más uniformes de los previstos");
                }
                return cola.poll();
            }
        };
    }

    /** Conteos de una simulación completa (24 meses) para las pruebas estadísticas. */
    private static final class Simulacion {
        final double[] unidadesPorProducto = new double[CATALOGO.size()];
        final double[] unidadesEstancamiento = new double[CATALOGO.size()];
        final double[] ingresoPorProducto = new double[CATALOGO.size()];
        final double[][] unidadesPorMes = new double[2][13];
        final double[] lineasPorTalla = new double[Talla.values().length];
        final double[] ticketsPorLineas = new double[4];
        double tickets;
        double lineas;
        double lineasPrimerColor;
        double lineasCantidadDos;
        double lineasConDescuento;
        int descuentoMinimo = Integer.MAX_VALUE;
        int descuentoMaximo = Integer.MIN_VALUE;
        int cantidadesFueraDeRango;
        int productosRepetidosEnTicket;
        int horasFueraDeRango;
        int diasDesordenados;
        int lineasAccesorioNoUnica;

        static Simulacion correr(ModeloDemanda m) {
            Simulacion s = new Simulacion();
            for (LocalDate d = INICIO; !d.isAfter(FIN); d = d.plusDays(1)) {
                LocalTime anterior = LocalTime.MIN;
                for (ModeloDemanda.Ticket t : m.ticketsDelDia(d)) {
                    s.registrarTicket(m, d, t, anterior);
                    anterior = t.hora();
                }
            }
            return s;
        }

        private void registrarTicket(ModeloDemanda m, LocalDate d, ModeloDemanda.Ticket t, LocalTime anterior) {
            tickets++;
            ticketsPorLineas[t.lineas().size()]++;
            if (t.hora().isBefore(anterior)) {
                diasDesordenados++;
            }
            if (t.hora().isBefore(LocalTime.of(9, 0)) || !t.hora().isBefore(LocalTime.of(21, 0))) {
                horasFueraDeRango++;
            }
            if (t.lineas().stream().map(ModeloDemanda.Linea::producto).distinct().count() != t.lineas().size()) {
                productosRepetidosEnTicket++;
            }
            for (ModeloDemanda.Linea l : t.lineas()) {
                int i = CATALOGO.indexOf(l.producto());
                lineas++;
                unidadesPorProducto[i] += l.cantidad();
                ingresoPorProducto[i] += l.cantidad() * l.producto().precioVenta().doubleValue();
                if (!d.isBefore(m.inicioEstancamiento())) {
                    unidadesEstancamiento[i] += l.cantidad();  // unidades en los últimos 3 meses
                }
                unidadesPorMes[d.getYear() - INICIO.getYear()][d.getMonthValue()] += l.cantidad();
                lineasPorTalla[l.talla().ordinal()]++;
                if (l.producto().esAccesorio() != (l.talla() == Talla.UNICA)) {
                    lineasAccesorioNoUnica++;
                }
                if (l.indiceColor() == 0) {
                    lineasPrimerColor++;
                }
                if (l.cantidad() == 2) {
                    lineasCantidadDos++;
                } else if (l.cantidad() != 1) {
                    cantidadesFueraDeRango++;
                }
                if (l.descuentoPorcentaje() > 0) {
                    lineasConDescuento++;
                    descuentoMinimo = Math.min(descuentoMinimo, l.descuentoPorcentaje());
                    descuentoMaximo = Math.max(descuentoMaximo, l.descuentoPorcentaje());
                }
            }
        }
    }
}
