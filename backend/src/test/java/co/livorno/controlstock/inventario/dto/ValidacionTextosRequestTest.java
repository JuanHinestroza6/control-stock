package co.livorno.controlstock.inventario.dto;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.venta.dto.AnularVentaRequest;
import co.livorno.controlstock.venta.dto.DevolucionRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/** El largo mínimo (5) de observación/motivo se valida después de recortar espacios. */
class ValidacionTextosRequestTest {

    Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean ajusteValido(String obs) {
        return validador.validate(new AjusteRequest(1L, TipoMovimiento.AJUSTE_POSITIVO, 1, obs)).isEmpty();
    }

    private boolean anularValido(String motivo) {
        return validador.validate(new AnularVentaRequest(motivo)).isEmpty();
    }

    private boolean devolucionValida(String motivo) {
        return validador.validate(new DevolucionRequest(1L, 1, motivo, true)).isEmpty();
    }

    @Test
    void textos_conMenosDeCincoCaracteresTrasRecortar_sonInvalidos() {
        assertThat(ajusteValido("  abcd  ")).isFalse();
        assertThat(anularValido("     ab")).isFalse();
        assertThat(devolucionValida("abcd      ")).isFalse();
        assertThat(ajusteValido("     ")).isFalse();
    }

    @Test
    void textos_conCincoOMasCaracteresTrasRecortar_sonValidos() {
        assertThat(ajusteValido("  abcde ")).isTrue();
        assertThat(anularValido("  merma  ")).isTrue();
        assertThat(devolucionValida("a b c")).isTrue();
        assertThat(ajusteValido("x".repeat(255))).isTrue();
        assertThat(ajusteValido("x".repeat(256))).isFalse();
    }
}
