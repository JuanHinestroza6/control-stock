package co.livorno.controlstock.auth.servicio;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.auth.dto.LoginRequest;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final TokenService tokens = mock(TokenService.class);
    private AuthService service;

    @BeforeEach
    void preparar() {
        when(encoder.encode(anyString())).thenReturn("hash-relleno");
        service = new AuthService(repo, encoder, tokens);
    }

    @Test
    void login_emailInexistente_ejecutaMatchesContraHashDeRellenoYFalla() {
        when(repo.findByEmail("nadie@livorno.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest(" Nadie@Livorno.test ", "clave")))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Credenciales inválidas");

        verify(encoder).matches("clave", "hash-relleno");
        verify(tokens, never()).emitir(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void login_usuarioInactivo_fallaAunqueLaClaveCoincida() {
        Usuario u = new Usuario("Ana", "ana@livorno.test", "hash-real", Rol.VENDEDOR);
        u.desactivar();
        when(repo.findByEmail("ana@livorno.test")).thenReturn(Optional.of(u));
        when(encoder.matches("clave", "hash-real")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@livorno.test", "clave")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
