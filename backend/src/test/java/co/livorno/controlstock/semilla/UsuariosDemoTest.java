package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UsuariosDemoTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();
    private final UsuariosDemo usuarios = new UsuariosDemo(mock(UsuarioRepository.class),
            mock(PasswordEncoder.class), validador);

    @Test
    void validarPassword_nulaOVacia_abortaConMensajeClaro() {
        assertThatThrownBy(() -> usuarios.validarPassword(null)).isInstanceOf(SemillaAbortadaException.class)
                .hasMessageContaining("SEED_PASSWORD");
        assertThatThrownBy(() -> usuarios.validarPassword("  ")).isInstanceOf(SemillaAbortadaException.class);
    }

    @Test
    void validarPassword_cortaOConCambiar_abortaPorPolitica() {
        assertThatThrownBy(() -> usuarios.validarPassword("corta123")).isInstanceOf(SemillaAbortadaException.class)
                .hasMessageContaining("al menos 12");
        assertThatThrownBy(() -> usuarios.validarPassword("Cambiar-esta-clave-123"))
                .isInstanceOf(SemillaAbortadaException.class).hasMessageContaining("cambiar");
        assertThatThrownBy(() -> usuarios.validarPassword("a".repeat(73)))
                .isInstanceOf(SemillaAbortadaException.class);
    }

    @Test
    void validarPassword_valida_noLanza() {
        assertThatCode(() -> usuarios.validarPassword("ClaveDemo-2026-ok")).doesNotThrowAnyException();
    }
}
