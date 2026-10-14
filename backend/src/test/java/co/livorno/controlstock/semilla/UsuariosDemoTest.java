package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.util.Optional;
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

    private UsuariosDemo conRepo(UsuarioRepository repo) {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hash");
        return new UsuariosDemo(repo, encoder, validador);
    }

    @Test
    void crearOReutilizar_correoDemoConOtroRol_aborta() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        when(repo.findByEmail("admin@livorno.co")).thenReturn(Optional.of(
                new Usuario("Admin", "admin@livorno.co", "hash", Rol.VENDEDOR)));

        assertThatThrownBy(() -> conRepo(repo).crearOReutilizar("ClaveDemo-2026-ok"))
                .isInstanceOf(SemillaAbortadaException.class)
                .hasMessageContaining("admin@livorno.co").hasMessageContaining("VENDEDOR");
    }

    @Test
    void crearOReutilizar_correoDemoInactivo_aborta() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        Usuario inactivo = new Usuario("Admin", "admin@livorno.co", "hash", Rol.ADMIN);
        inactivo.desactivar();
        when(repo.findByEmail("admin@livorno.co")).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> conRepo(repo).crearOReutilizar("ClaveDemo-2026-ok"))
                .isInstanceOf(SemillaAbortadaException.class).hasMessageContaining("inactivo");
    }

    @Test
    void crearOReutilizar_correosExistentesCorrectos_losReutiliza() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        for (UsuariosDemo.Demo d : UsuariosDemo.USUARIOS) {
            when(repo.findByEmail(d.email())).thenReturn(Optional.of(new Usuario(d.nombre(), d.email(), "hash", d.rol())));
        }

        assertThat(conRepo(repo).crearOReutilizar("ClaveDemo-2026-ok")).hasSize(4);
    }
}
