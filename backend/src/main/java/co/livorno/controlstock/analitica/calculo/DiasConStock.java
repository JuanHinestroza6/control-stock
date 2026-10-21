package co.livorno.controlstock.analitica.calculo;

/**
 * Días con stock de una variante en un periodo: {@code existentes} = días del periodo desde su creación,
 * {@code conStock} = de esos, los que no fueron "sin stock" (ADR-010, decisión 1).
 */
public record DiasConStock(int conStock, int existentes) {

    public DiasConStock {
        if (existentes < 0 || conStock < 0 || conStock > existentes) {
            throw new IllegalArgumentException(
                    "Días inválidos: conStock=" + conStock + ", existentes=" + existentes);
        }
    }

    /** La variante existía al menos un día del periodo. */
    public boolean existe() {
        return existentes > 0;
    }

    /**
     * frac = conStock / existentes.
     *
     * @throws IllegalStateException si la variante no existía en el periodo
     */
    public double fraccion() {
        if (existentes == 0) {
            throw new IllegalStateException("La variante no existía en el periodo.");
        }
        return (double) conStock / existentes;
    }
}
