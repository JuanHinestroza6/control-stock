package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.analitica.config.PropiedadesAnalitica;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Evidencia de que el ROP cumple el nivel de servicio de su clase (no se ajustó "a ojo").
 *
 * <p>Para cada λ y clase, con {@code Random(42)} (determinista):
 * <ol>
 *   <li>Se generan 90 días de historia con demanda diaria Poisson(λ) y se calcula el ROP con el mismo camino que la
 *       analítica real ({@link SerieDemanda} + {@link CalculadoraReorden}, L = 15). Así la prueba incluye el error de
 *       estimar d̄ y s con una muestra de 90 días.</li>
 *   <li>Se simula la política "pedir Q al llegar al ROP": los clientes llegan como un proceso de Poisson de tasa λ por
 *       día y compran una unidad cada uno (la demanda diaria resulta Poisson(λ)). Igual que el sistema, que re-evalúa
 *       la alerta después de cada movimiento, el pedido se dispara en la venta que deja el stock en el ROP y llega
 *       L = 15 días después. Ventas perdidas si no hay stock. Q = ROP + max(5, ⌈30λ⌉), de modo que al llegar un pedido
 *       el stock queda por encima del ROP y nunca hay dos pedidos en camino.</li>
 *   <li>Un ciclo (pedido → llegada) es "sin quiebre" si no se perdió ninguna venta durante el lead time.</li>
 * </ol>
 * 200 réplicas × 25 ciclos = 5000 ciclos por caso; error estándar ≈ 0,006 con p = 0,80. Se exige
 * fracción ≥ nivel de servicio − 0,03.
 */
class CalculadoraReordenMonteCarloTest {

    private static final int LEAD_TIME = 15;
    private static final int DIAS_HISTORIA = 90;
    private static final int REPLICAS = 200;
    private static final int CICLOS_POR_REPLICA = 25;
    private static final double TOLERANCIA = 0.03;

    private final PropiedadesAnalitica propiedades = CalculadoraReordenTest.propiedadesPorDefecto();
    private final CalculadoraReorden calculadora = new CalculadoraReorden(propiedades);

    @ParameterizedTest(name = "λ = {0}, clase {1}")
    @CsvSource({
        "0.05, A", "0.05, B", "0.05, C",
        "0.5, A", "0.5, B", "0.5, C",
        "3, A", "3, B", "3, C"
    })
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void politicaConElRop_cumpleElNivelDeServicioDeLaClase(double lambda, char clase) {
        Random azar = new Random(42);
        int ciclos = 0;
        int sinQuiebre = 0;
        for (int replica = 0; replica < REPLICAS; replica++) {
            int[] historia = new int[DIAS_HISTORIA];
            for (int d = 0; d < DIAS_HISTORIA; d++) {
                historia[d] = poisson(azar, lambda);
            }
            int rop = calculadora.calcular(SerieDemanda.de(historia), LEAD_TIME, clase).puntoReorden();
            sinQuiebre += simular(azar, lambda, rop, CICLOS_POR_REPLICA);
            ciclos += CICLOS_POR_REPLICA;
        }
        double fraccion = (double) sinQuiebre / ciclos;
        double nivel = propiedades.parametrosDe(clase).nivelServicio().doubleValue();

        assertThat(fraccion)
                .as("λ = %s, clase %s: ciclos sin quiebre %d/%d = %.4f (nivel de servicio %.2f)",
                        lambda, clase, sinQuiebre, ciclos, fraccion, nivel)
                .isGreaterThanOrEqualTo(nivel - TOLERANCIA);
    }

    /** Revisión continua con ventas unitarias; devuelve cuántos de los {@code ciclos} terminaron sin quiebre. */
    private static int simular(Random azar, double lambda, int rop, int ciclos) {
        int q = rop + Math.max(5, (int) Math.ceil(30 * lambda));
        int stock = rop + q;
        double t = 0;
        boolean pedidoEnCamino = false;
        double llegada = 0;
        boolean quiebre = false;
        int cerrados = 0;
        int sinQuiebre = 0;
        while (cerrados < ciclos) {
            t += -Math.log(1 - azar.nextDouble()) / lambda; // siguiente cliente
            if (pedidoEnCamino && llegada <= t) {
                stock += q;
                pedidoEnCamino = false;
                cerrados++;
                if (!quiebre) {
                    sinQuiebre++;
                }
                quiebre = false;
            }
            if (stock > 0) {
                stock--;
            } else {
                quiebre = true; // venta perdida durante el lead time
            }
            if (!pedidoEnCamino && stock <= rop) {
                pedidoEnCamino = true;
                llegada = t + LEAD_TIME;
            }
        }
        return sinQuiebre;
    }

    /** Muestra de Poisson(λ) por el método de Knuth (λ pequeño). */
    private static int poisson(Random azar, double lambda) {
        double limite = Math.exp(-lambda);
        int k = 0;
        double producto = azar.nextDouble();
        while (producto > limite) {
            k++;
            producto *= azar.nextDouble();
        }
        return k;
    }
}
