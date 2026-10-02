package co.livorno.controlstock.comun.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.usuario.dto.RestablecerPasswordRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PasswordValidaTest {

    static jakarta.validation.ValidatorFactory fabrica;
    static Validator validador;

    @BeforeAll
    static void iniciar() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrar() {
        fabrica.close();
    }

    private String mensaje(String password) {
        return validador.validate(new RestablecerPasswordRequest(password)).stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage()).collect(Collectors.joining("|"));
    }

    @Test
    void valida_nulaOVacia_esObligatoria() {
        assertThat(mensaje(null)).contains("passwordTemporal: La contraseña es obligatoria");
        assertThat(mensaje("   ")).contains("obligatoria");
    }

    @Test
    void valida_once_caracteres_rechaza() {
        assertThat(mensaje("a".repeat(11))).contains("al menos 12");
    }

    @Test
    void valida_doce_caracteres_acepta() {
        assertThat(mensaje("Segura-Clave1")).isEmpty();
        assertThat(mensaje("a".repeat(12))).isEmpty();
    }

    @Test
    void valida_setentaYDosCaracteres_acepta() {
        assertThat(mensaje("a".repeat(72))).isEmpty();
    }

    @Test
    void valida_setentaYTresCaracteres_rechaza() {
        assertThat(mensaje("a".repeat(73))).contains("72");
    }

    @Test
    void valida_menosDeSetentaYDosCaracteresPeroMasDeSetentaYDosBytes_rechaza() {
        // 40 emojis = 80 bytes UTF-8 pero 40 caracteres (code points)
        assertThat(mensaje("😀".repeat(40))).contains("72 bytes");
        // 36 caracteres con tildes = 72 bytes exactos si son 2 bytes cada una: válido
        assertThat(mensaje("é".repeat(36))).isEmpty();
        assertThat(mensaje("é".repeat(37))).contains("72 bytes");
    }

    @Test
    void valida_empiezaPorCambiar_rechazaSinImportarMayusculas() {
        assertThat(mensaje("cambiar-esta-clave")).contains("cambiar");
        assertThat(mensaje("CAMBIAR12345678")).contains("cambiar");
        assertThat(mensaje("Mi-cambiar-clave1")).isEmpty();
    }
}
