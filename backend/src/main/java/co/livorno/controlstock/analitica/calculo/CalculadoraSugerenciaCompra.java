package co.livorno.controlstock.analitica.calculo;

import co.livorno.controlstock.analitica.modelo.FuenteDemanda;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Sugerencia de compra con política (s, S) (ADR-010, decisión 6), cálculo puro. Nunca toca stock.
 *
 * <pre>
 * umbral = puntoReorden ?? stockMinimo ; posición = disponible + enCamino
 * Candidata si posición ≤ umbral (las que tendrían AGOTADO o STOCK_BAJO).
 * demandaR = Σ tasa_v en [corte + L, corte + L + R)      con pronóstico (fuente PRONOSTICO)
 *          = d̄ observada × R                              sin pronóstico (fuente HISTORICA)
 * S = umbral + demandaR ; ROP null y demandaR = 0 → S = 2 × stockMinimo
 * faltante = ceil(S − posición) ; faltante ≤ 0 → no entra ; cantidad = ceil(faltante / loteMinimo) × loteMinimo
 * </pre>
 *
 * Ejemplo: ROP 31, pronóstico noviembre 60 (2/día) y diciembre 93 (3/día), corte 25-oct, L = 15, R = 30 → demanda del
 * 9-nov al 8-dic = 22 × 2 + 8 × 3 = 68; S = 99; posición 10 → 89 → lote 6 → 90. Variante SIN_DEMANDA agotada: S = 0 →
 * no entra (no se compra lo que no se vende).
 *
 * <p>El filtro de variantes y productos activos y la exclusión de las que ya están en otro BORRADOR son del servicio.
 * {@code ceil} tolera 1e-9: un valor que en aritmética exacta es entero no sube una unidad por el redondeo.
 */
public final class CalculadoraSugerenciaCompra {

    private final int periodoRevisionDias;

    /**
     * @param periodoRevisionDias R ({@code app.compras.periodo-revision-dias}, 30 por defecto), ≥ 1
     */
    public CalculadoraSugerenciaCompra(int periodoRevisionDias) {
        if (periodoRevisionDias < 1) {
            throw new IllegalArgumentException("El periodo de revisión debe ser de al menos 1 día: "
                    + periodoRevisionDias);
        }
        this.periodoRevisionDias = periodoRevisionDias;
    }

    /**
     * Líneas sugeridas para las variantes dadas, en orden ascendente de id; las que no son candidatas o tienen cantidad
     * 0 no aparecen.
     *
     * @param corte primer día del lead time (hoy)
     * @throws IllegalArgumentException si hay ids repetidos
     */
    public List<LineaSugerencia> sugerir(List<InsumoSugerencia> insumos, LocalDate corte) {
        Objects.requireNonNull(insumos, "insumos");
        Objects.requireNonNull(corte, "corte");
        List<InsumoSugerencia> ordenados = new ArrayList<>(insumos);
        ordenados.sort(Comparator.comparingLong(InsumoSugerencia::varianteId));
        Set<Long> vistos = new HashSet<>();
        List<LineaSugerencia> lineas = new ArrayList<>();
        for (InsumoSugerencia insumo : ordenados) {
            Objects.requireNonNull(insumo, "insumo");
            if (!vistos.add(insumo.varianteId())) {
                throw new IllegalArgumentException("Variante repetida en la sugerencia: " + insumo.varianteId());
            }
            sugerir(insumo, corte).ifPresent(lineas::add);
        }
        return lineas;
    }

    /**
     * Línea sugerida para una variante, o vacío si no es candidata (posición &gt; umbral) o su cantidad es 0.
     *
     * @param corte primer día del lead time (hoy)
     */
    public Optional<LineaSugerencia> sugerir(InsumoSugerencia insumo, LocalDate corte) {
        Objects.requireNonNull(insumo, "insumo");
        Objects.requireNonNull(corte, "corte");
        int umbral = insumo.umbral();
        long posicion = insumo.posicion();
        if (posicion > umbral) {
            return Optional.empty();
        }

        FuenteDemanda fuente;
        double demanda;
        if (insumo.pronostico() != null) {
            fuente = FuenteDemanda.PRONOSTICO;
            demanda = insumo.pronostico().demanda(corte.plusDays(insumo.leadTimeDias()), periodoRevisionDias);
        } else {
            fuente = FuenteDemanda.HISTORICA;
            demanda = insumo.demandaMediaHistorica() * periodoRevisionDias;
        }

        boolean sinRopNiDemanda = insumo.puntoReorden() == null && demanda <= CalculadoraReorden.TOLERANCIA;
        double objetivo = sinRopNiDemanda ? 2.0 * insumo.stockMinimo() : umbral + demanda;
        double neto = objetivo - posicion;
        long faltante = (long) Math.ceil(neto - CalculadoraReorden.TOLERANCIA);
        if (faltante <= 0) {
            return Optional.empty();
        }
        long lote = insumo.loteMinimo();
        long cantidad = Math.multiplyExact((faltante + lote - 1) / lote, lote);

        String justificacion = justificar(insumo, fuente, demanda, sinRopNiDemanda, neto, faltante, cantidad);
        return Optional.of(new LineaSugerencia(insumo.varianteId(), umbral, posicion, fuente, escala2(demanda),
                escala2(objetivo), Math.toIntExact(faltante), Math.toIntExact(cantidad), justificacion,
                insumo.claseAbc(), insumo.disponible()));
    }

    /**
     * "ROP 31 + demanda 30 d 68 − disponible 10 − en camino 0 = 89 → lote 6: 90". Variantes: "Stock mínimo 5" sin ROP;
     * "demanda histórica" sin pronóstico; "= 88,6 → 89" si S − posición no es entero; sin sufijo de lote si el lote es 1;
     * "Sin ROP ni demanda: 2 × stock mínimo 5 − …" en el respaldo.
     */
    private String justificar(InsumoSugerencia insumo, FuenteDemanda fuente, double demanda, boolean sinRopNiDemanda,
            double neto, long faltante, long cantidad) {
        StringBuilder texto = new StringBuilder();
        if (sinRopNiDemanda) {
            texto.append("Sin ROP ni demanda: 2 × stock mínimo ").append(insumo.stockMinimo());
        } else {
            texto.append(insumo.puntoReorden() != null ? "ROP " + insumo.puntoReorden()
                    : "Stock mínimo " + insumo.stockMinimo());
            texto.append(fuente == FuenteDemanda.PRONOSTICO ? " + demanda " : " + demanda histórica ")
                    .append(periodoRevisionDias).append(" d ").append(numero(demanda));
        }
        texto.append(" − disponible ").append(insumo.disponible())
                .append(" − en camino ").append(insumo.enCamino())
                .append(" = ").append(numero(neto));
        if (Math.abs(neto - faltante) > CalculadoraReorden.TOLERANCIA) {
            texto.append(" → ").append(faltante);
        }
        if (insumo.loteMinimo() > 1) {
            texto.append(" → lote ").append(insumo.loteMinimo()).append(": ").append(cantidad);
        }
        return texto.toString();
    }

    /** Hasta 2 decimales, sin ceros a la derecha y con coma decimal: 68 → "68", 12,6 → "12,6". */
    private static String numero(double valor) {
        return escala2(valor).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static BigDecimal escala2(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
    }
}
