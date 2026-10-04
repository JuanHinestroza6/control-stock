package co.livorno.controlstock.semilla;

import java.time.LocalDate;

/** Resultado parcial de la carga (se amplía en B4/B5). {@code generado=false}: la base ya tenía datos. */
public record ResumenSemilla(boolean generado, LocalDate inicio, LocalDate fin, int usuarios, int productos,
                             int variantes, long unidadesIniciales) {

    public static ResumenSemilla yaHabiaDatos() {
        return new ResumenSemilla(false, null, null, 0, 0, 0, 0);
    }
}
