package co.livorno.controlstock.analitica.calculo;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Corrección de la demanda censurada por agotados (ADR-010, decisión 1), funciones puras.
 *
 * <pre>
 * Disponibilidad diaria de la variante v (desde el kardex, orden fecha, id):
 *   disponible al abrir el día d = disponible tras el último movimiento anterior a d (0 antes del primero)
 *   d es "sin stock" si al abrir es 0 y ningún movimiento del día lo deja &gt; 0
 *   días antes de creadaEn no cuentan (el día de creación sí)
 *   frac_{v,m} = días con stock / días existentes de v en el mes m
 * Pesos: w_v = participación de v en la demanda observada del producto en toda la serie (total 0 → pesos iguales)
 * disp_m = Σ_{v existentes en m} w_v × frac_{v,m} / Σ_{v existentes en m} w_v
 * corregida_m = y_m = 0 ? 0 : y_m / max(disp_m, piso)            piso = app.pronostico.disponibilidad-minima (0,5)
 * </pre>
 *
 * Casos sin dato (no previstos en la fórmula): si las variantes que existen en el mes tienen todas peso 0, se promedian
 * con pesos iguales; si ninguna variante activa existía en el mes (o el producto no tiene variantes activas),
 * disp_m = 1 (no hay evidencia de agotado y no se corrige).
 *
 * Ejemplo a mano: dos variantes con w = 0,75 / 0,25; la segunda sin stock 15 de 30 días →
 * disp = 0,75 × 1 + 0,25 × 0,5 = 0,875 ; observada 70 → corregida 70 / 0,875 = 80.
 */
public final class CorrectorCensura {

    private CorrectorCensura() {
    }

    /**
     * Cuenta los días con stock de una variante en {@code [desde, hasta)}.
     *
     * @param saldos    disponible tras cada movimiento, en orden de kardex (día no decreciente)
     * @param creadaEn  día de creación de la variante: los días anteriores no existen
     * @throws IllegalArgumentException si los saldos no están ordenados por día o si {@code hasta < desde}
     */
    public static DiasConStock diasConStock(List<SaldoDisponible> saldos, LocalDate creadaEn, LocalDate desde,
            LocalDate hasta) {
        boolean[] dias = conStockPorDia(saldos, creadaEn, desde, hasta);
        LocalDate inicio = inicioEfectivo(creadaEn, desde, hasta);
        int existentes = (int) ChronoUnit.DAYS.between(inicio, hasta);
        return new DiasConStock(contar(dias, (int) ChronoUnit.DAYS.between(desde, inicio), dias.length), existentes);
    }

    /**
     * Días con stock por mes, para los {@code meses} meses que empiezan en {@code mesInicial} (los de la serie).
     * Un mes anterior a la creación queda con {@code existentes = 0}.
     */
    public static List<DiasConStock> diasConStockPorMes(List<SaldoDisponible> saldos, LocalDate creadaEn,
            YearMonth mesInicial, int meses) {
        Objects.requireNonNull(mesInicial, "mesInicial");
        if (meses < 0) {
            throw new IllegalArgumentException("El número de meses no puede ser negativo: " + meses);
        }
        LocalDate desde = mesInicial.atDay(1);
        LocalDate hasta = mesInicial.plusMonths(meses).atDay(1);
        boolean[] dias = conStockPorDia(saldos, creadaEn, desde, hasta);
        List<DiasConStock> resultado = new ArrayList<>(meses);
        for (int i = 0; i < meses; i++) {
            YearMonth mes = mesInicial.plusMonths(i);
            LocalDate inicioMes = mes.atDay(1);
            LocalDate finMes = mes.plusMonths(1).atDay(1);
            LocalDate inicio = inicioEfectivo(creadaEn, inicioMes, finMes);
            int desdeIdx = (int) ChronoUnit.DAYS.between(desde, inicio);
            int hastaIdx = (int) ChronoUnit.DAYS.between(desde, finMes);
            resultado.add(new DiasConStock(contar(dias, desdeIdx, hastaIdx), hastaIdx - desdeIdx));
        }
        return resultado;
    }

    /**
     * disp_m por mes, ponderada por la participación de cada variante en la demanda observada.
     *
     * @param meses número de meses de la serie; cada variante debe traer exactamente esos meses
     * @return un valor en [0, 1] por mes
     */
    public static double[] disponibilidad(List<DisponibilidadVariante> variantes, int meses) {
        Objects.requireNonNull(variantes, "variantes");
        if (meses < 0) {
            throw new IllegalArgumentException("El número de meses no puede ser negativo: " + meses);
        }
        long totalDemanda = 0;
        for (DisponibilidadVariante v : variantes) {
            Objects.requireNonNull(v, "variante");
            if (v.porMes().size() != meses) {
                throw new IllegalArgumentException("La variante trae " + v.porMes().size()
                        + " meses de disponibilidad y la serie tiene " + meses + ".");
            }
            totalDemanda += Math.max(0, v.demandaObservada());
        }
        double[] pesos = new double[variantes.size()];
        for (int k = 0; k < pesos.length; k++) {
            pesos[k] = totalDemanda == 0
                    ? 1.0 / pesos.length
                    : (double) Math.max(0, variantes.get(k).demandaObservada()) / totalDemanda;
        }

        double[] disp = new double[meses];
        for (int m = 0; m < meses; m++) {
            double numerador = 0;
            double sumaPesos = 0;
            double sumaFracciones = 0;
            int existentes = 0;
            for (int k = 0; k < pesos.length; k++) {
                DiasConStock dias = variantes.get(k).porMes().get(m);
                if (!dias.existe()) {
                    continue;
                }
                double frac = dias.fraccion();
                numerador += pesos[k] * frac;
                sumaPesos += pesos[k];
                sumaFracciones += frac;
                existentes++;
            }
            if (existentes == 0) {
                disp[m] = 1.0;
            } else if (sumaPesos == 0) {
                disp[m] = sumaFracciones / existentes;
            } else {
                disp[m] = numerador / sumaPesos;
            }
        }
        return disp;
    }

    /**
     * corregida_m = y_m = 0 ? 0 : y_m / max(disp_m, piso).
     *
     * @param piso disponibilidad mínima, en (0, 1]
     * @throws IllegalArgumentException si los largos no coinciden, el piso está fuera de rango o una disponibilidad
     *                                  está fuera de [0, 1]
     */
    public static SerieCorregida corregir(SerieMensual serie, double[] disponibilidad, double piso) {
        Objects.requireNonNull(serie, "serie");
        Objects.requireNonNull(disponibilidad, "disponibilidad");
        if (!(piso > 0 && piso <= 1)) {
            throw new IllegalArgumentException("El piso de disponibilidad debe estar en (0, 1]: " + piso);
        }
        int n = serie.meses();
        if (disponibilidad.length != n) {
            throw new IllegalArgumentException("La disponibilidad trae " + disponibilidad.length
                    + " meses y la serie tiene " + n + ".");
        }
        int[] observada = serie.valores();
        double[] corregida = new double[n];
        for (int m = 0; m < n; m++) {
            double disp = disponibilidad[m];
            if (!(disp >= 0 && disp <= 1)) {
                throw new IllegalArgumentException("Disponibilidad fuera de [0, 1] en " + serie.mes(m) + ": " + disp);
            }
            corregida[m] = observada[m] == 0 ? 0.0 : observada[m] / Math.max(disp, piso);
        }
        return new SerieCorregida(serie.mesInicial(), observada, disponibilidad, corregida);
    }

    /** Atajo: disponibilidad ponderada de las variantes y corrección de la serie en un paso. */
    public static SerieCorregida corregir(SerieMensual serie, List<DisponibilidadVariante> variantes, double piso) {
        Objects.requireNonNull(serie, "serie");
        return corregir(serie, disponibilidad(variantes, serie.meses()), piso);
    }

    /**
     * true por cada día de {@code [desde, hasta)} con stock. Los días anteriores a la creación quedan en false; los
     * llamadores los excluyen del conteo de días existentes.
     */
    private static boolean[] conStockPorDia(List<SaldoDisponible> saldos, LocalDate creadaEn, LocalDate desde,
            LocalDate hasta) {
        Objects.requireNonNull(saldos, "saldos");
        Objects.requireNonNull(creadaEn, "creadaEn");
        Objects.requireNonNull(desde, "desde");
        Objects.requireNonNull(hasta, "hasta");
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("Periodo inválido: " + desde + " a " + hasta);
        }
        boolean[] dias = new boolean[(int) ChronoUnit.DAYS.between(desde, hasta)];
        LocalDate inicio = inicioEfectivo(creadaEn, desde, hasta);

        int idx = 0;
        int disponible = 0;
        LocalDate anterior = null;
        // Saldos previos al inicio: solo fijan el disponible con que abre el primer día.
        while (idx < saldos.size() && saldos.get(idx).dia().isBefore(inicio)) {
            anterior = verificarOrden(anterior, saldos.get(idx));
            disponible = saldos.get(idx).disponible();
            idx++;
        }
        for (LocalDate d = inicio; d.isBefore(hasta); d = d.plusDays(1)) {
            if (idx < saldos.size() && saldos.get(idx).dia().isBefore(d)) {
                // Un saldo de un día ya recorrido que no se consumió en su día: la lista no está ordenada.
                throw new IllegalArgumentException("Los saldos deben venir ordenados por día: " + saldos.get(idx).dia()
                        + " después de " + anterior);
            }
            boolean conStock = disponible > 0;
            while (idx < saldos.size() && saldos.get(idx).dia().equals(d)) {
                anterior = verificarOrden(anterior, saldos.get(idx));
                disponible = saldos.get(idx).disponible();
                conStock |= disponible > 0;
                idx++;
            }
            dias[(int) ChronoUnit.DAYS.between(desde, d)] = conStock;
        }
        // El resto (posterior a hasta) no influye, pero se verifica el orden para detectar consultas mal ordenadas.
        for (; idx < saldos.size(); idx++) {
            anterior = verificarOrden(anterior, saldos.get(idx));
        }
        return dias;
    }

    private static LocalDate verificarOrden(LocalDate anterior, SaldoDisponible saldo) {
        Objects.requireNonNull(saldo, "saldo");
        if (anterior != null && saldo.dia().isBefore(anterior)) {
            throw new IllegalArgumentException("Los saldos deben venir ordenados por día: " + saldo.dia()
                    + " después de " + anterior);
        }
        return saldo.dia();
    }

    /** max(desde, creadaEn), sin pasar de hasta. */
    private static LocalDate inicioEfectivo(LocalDate creadaEn, LocalDate desde, LocalDate hasta) {
        LocalDate inicio = creadaEn.isAfter(desde) ? creadaEn : desde;
        return inicio.isAfter(hasta) ? hasta : inicio;
    }

    private static int contar(boolean[] dias, int desdeIdx, int hastaIdx) {
        int cuenta = 0;
        for (int i = desdeIdx; i < hastaIdx; i++) {
            if (dias[i]) {
                cuenta++;
            }
        }
        return cuenta;
    }
}
