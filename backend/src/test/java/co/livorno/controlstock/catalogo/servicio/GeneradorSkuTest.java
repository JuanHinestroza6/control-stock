package co.livorno.controlstock.catalogo.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeneradorSkuTest {

    @Test
    void generar_formatoBase_codigoTallaColor3() {
        assertThat(GeneradorSku.generar("LIV001", "M", "Negro", s -> false)).isEqualTo("LIV001-M-NEG");
    }

    @Test
    void generar_colorConTildeYEnie_seNormaliza() {
        assertThat(GeneradorSku.generar("LIV001", "L", "MARRÓN", s -> false)).isEqualTo("LIV001-L-MAR");
        assertThat(GeneradorSku.generar("LIV001", "L", "Ñandú", s -> false)).isEqualTo("LIV001-L-NAN");
    }

    @Test
    void generar_colorDeUnaODosLetras_usaLasQueTiene() {
        assertThat(GeneradorSku.generar("LIV001", "S", "a", s -> false)).isEqualTo("LIV001-S-A");
        assertThat(GeneradorSku.generar("LIV001", "S", "rj", s -> false)).isEqualTo("LIV001-S-RJ");
    }

    @Test
    void generar_colorConEspacios_ignoraEspaciosAlAbreviar() {
        assertThat(GeneradorSku.generar("LIV001", "S", "  azul   cielo ", s -> false)).isEqualTo("LIV001-S-AZU");
        assertThat(GeneradorSku.generar("LIV001", "S", "a b c d", s -> false)).isEqualTo("LIV001-S-ABC");
    }

    @Test
    void generar_codigoYTallaEnMinusculas_seNormalizanAMayusculas() {
        assertThat(GeneradorSku.generar("liv001", "xl", "negro", s -> false)).isEqualTo("LIV001-XL-NEG");
    }

    @Test
    void generar_colisiones_agregaSufijoNumerico() {
        Set<String> usados = new HashSet<>(Set.of("LIV001-M-NEG"));
        assertThat(GeneradorSku.generar("LIV001", "M", "Negro", usados::contains)).isEqualTo("LIV001-M-NEG2");

        usados.add("LIV001-M-NEG2");
        assertThat(GeneradorSku.generar("LIV001", "M", "Negrito", usados::contains)).isEqualTo("LIV001-M-NEG3");
    }

    @Test
    void generar_dosColoresConMismaAbreviatura_producenSkuDistintosEnElMismoLote() {
        Set<String> usados = new HashSet<>();
        for (String color : new String[] {"Negro", "Negrito", "Neón"}) {
            usados.add(GeneradorSku.generar("LIV001", "M", color, usados::contains));
        }

        assertThat(usados).containsExactlyInAnyOrder("LIV001-M-NEG", "LIV001-M-NEG2", "LIV001-M-NEO");
    }

    @Test
    void generar_colorVacioOSinCaracteresValidos_lanzaExcepcion() {
        assertThatThrownBy(() -> GeneradorSku.generar("LIV001", "M", "   ", s -> false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeneradorSku.generar("LIV001", "M", "---", s -> false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeneradorSku.generar("LIV001", "M", null, s -> false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizarColor_colapsaEspaciosYPasaAMayusculas() {
        assertThat(GeneradorSku.normalizarColor("  azul   marino ")).isEqualTo("AZUL MARINO");
        assertThat(GeneradorSku.normalizarColor("marrón")).isEqualTo("MARRÓN");
    }

    @Test
    void generar_skuMasLargoPosible_cabeEnLaColumna() {
        String sku = GeneradorSku.generar("A".repeat(20), "UNICA", "Negro", s -> s.length() < 45 && !s.endsWith("99"));

        assertThat(sku.length()).isLessThanOrEqualTo(40);
    }
}
