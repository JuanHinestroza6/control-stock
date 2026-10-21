package co.livorno.controlstock.analitica.calculo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Desagregación del pronóstico de un producto a sus variantes activas (ADR-010, decisión 2), función pura.
 *
 * <pre>
 * Ventana de mezcla [corte − 180, corte):
 *   frac_v  = días con stock / días existentes      (existentes = 0 → 1: sin evidencia de agotado)
 *   bruta_v = max(demanda_v, 0) / max(frac_v, piso) (piso = app.pronostico.disponibilidad-minima, 0,5)
 *   part_v  = bruta_v / Σ bruta                      (Σ bruta = 0 → todas 0)
 * pronóstico_v,mes = pronóstico_producto,mes × part_v, escala 2
 * Centésimas: se reparten por el método del mayor residuo para que Σ_v pronóstico_v,mes = pronóstico_producto,mes
 *   exactamente: cada variante recibe ⌊centavos × part_v⌋ y las centésimas que faltan van, de a una, a los mayores
 *   residuos (empate → menor id). Solo reciben centésimas las variantes con part_v > 0.
 * </pre>
 *
 * La corrección por días con stock evita que una talla agotada parezca impopular: vendió poco porque no había.
 *
 * Ejemplo a mano (180 días): M vende 60 con stock los 180 días (frac 1 → 60); L vende 30 con stock 90 días (frac 0,5 →
 * 60); S vende 10 con stock 45 días (frac 0,25 → piso 0,5 → 20). Σ = 140 → part 3/7, 3/7, 1/7. Producto 70 → 30, 30,
 * 10. Producto 100 → 42,857… / 42,857… / 14,285…: pisos 42,85 + 42,85 + 14,28 = 99,98; las 2 centésimas van a M y L
 * (residuo 0,714 &gt; 0,571) → 42,86 + 42,86 + 14,28 = 100,00 (con HALF_UP por separado sumaría 100,01).
 */
public final class Desagregador {

    private Desagregador() {
    }

    /**
     * Reparte el pronóstico del producto entre sus variantes activas.
     *
     * @param pronosticoProducto pronóstico del producto por mes (h = 1..horizonte), no negativo; se lleva a escala 2
     * @param variantes          variantes activas del producto (sin ids repetidos); las inactivas no se pasan (sin fila)
     * @param piso               piso de la fracción con stock, en (0, 1]
     * @return una fila por variante, en orden ascendente de id (el orden de inserción de R5)
     * @throws IllegalArgumentException con un pronóstico negativo, ids repetidos o un piso fuera de rango
     */
    public static List<PronosticoVariante> desagregar(List<BigDecimal> pronosticoProducto,
            List<DemandaVariante> variantes, double piso) {
        Objects.requireNonNull(pronosticoProducto, "pronosticoProducto");
        Objects.requireNonNull(variantes, "variantes");
        if (!(piso > 0.0 && piso <= 1.0)) {
            throw new IllegalArgumentException("El piso de disponibilidad debe estar en (0, 1]: " + piso);
        }
        List<Long> centavosProducto = new ArrayList<>(pronosticoProducto.size());
        for (BigDecimal cantidad : pronosticoProducto) {
            Objects.requireNonNull(cantidad, "cantidad del pronóstico");
            if (cantidad.signum() < 0) {
                throw new IllegalArgumentException("El pronóstico del producto no puede ser negativo: " + cantidad);
            }
            centavosProducto.add(cantidad.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact());
        }

        List<DemandaVariante> ordenadas = new ArrayList<>(variantes);
        ordenadas.sort(Comparator.comparingLong(DemandaVariante::varianteId));
        Set<Long> vistos = new HashSet<>();
        for (DemandaVariante v : ordenadas) {
            if (!vistos.add(v.varianteId())) {
                throw new IllegalArgumentException("Variante repetida en la desagregación: " + v.varianteId());
            }
        }

        double[] participacion = participaciones(ordenadas, piso);
        int n = ordenadas.size();
        long[][] centavos = new long[n][pronosticoProducto.size()];
        for (int mes = 0; mes < centavosProducto.size(); mes++) {
            long[] reparto = repartir(centavosProducto.get(mes), participacion);
            for (int i = 0; i < n; i++) {
                centavos[i][mes] = reparto[i];
            }
        }

        List<PronosticoVariante> resultado = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            List<BigDecimal> meses = new ArrayList<>(centavosProducto.size());
            for (long c : centavos[i]) {
                meses.add(BigDecimal.valueOf(c, 2));
            }
            resultado.add(new PronosticoVariante(ordenadas.get(i).varianteId(),
                    BigDecimal.valueOf(participacion[i]).setScale(6, RoundingMode.HALF_UP), meses));
        }
        return resultado;
    }

    /** part_v normalizada (todas 0 si ninguna variante tiene demanda en la ventana). */
    static double[] participaciones(List<DemandaVariante> variantes, double piso) {
        double[] bruta = new double[variantes.size()];
        double total = 0.0;
        for (int i = 0; i < bruta.length; i++) {
            DemandaVariante v = variantes.get(i);
            DiasConStock dias = v.diasConStock();
            double frac = dias.existe() ? dias.fraccion() : 1.0;
            bruta[i] = Math.max(0L, v.demanda()) / Math.max(frac, piso);
            total += bruta[i];
        }
        if (total > 0.0) {
            for (int i = 0; i < bruta.length; i++) {
                bruta[i] /= total;
            }
        }
        return bruta;
    }

    /** Mayor residuo: ⌊centavos × part⌋ y las centésimas restantes a los mayores residuos (empate → menor índice). */
    private static long[] repartir(long centavos, double[] participacion) {
        int n = participacion.length;
        long[] reparto = new long[n];
        double[] residuo = new double[n];
        List<Integer> conParticipacion = new ArrayList<>();
        long asignado = 0;
        for (int i = 0; i < n; i++) {
            if (participacion[i] <= 0.0) {
                continue;
            }
            double exacto = centavos * participacion[i];
            reparto[i] = (long) Math.floor(exacto);
            residuo[i] = exacto - reparto[i];
            asignado += reparto[i];
            conParticipacion.add(i);
        }
        if (conParticipacion.isEmpty()) {
            return reparto;
        }
        conParticipacion.sort(Comparator.comparingDouble((Integer i) -> residuo[i]).reversed()
                .thenComparingInt(i -> i));
        long faltan = centavos - asignado;
        for (int k = 0; faltan > 0; k = (k + 1) % conParticipacion.size(), faltan--) {
            reparto[conParticipacion.get(k)]++;
        }
        return reparto;
    }
}
