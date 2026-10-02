package co.livorno.controlstock.auth.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.auth.dto.LoginRequest;
import co.livorno.controlstock.auth.dto.LoginResponse;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final TokenService tokens = mock(TokenService.class);
    private final LimitadorIntentosLogin limitador = mock(LimitadorIntentosLogin.class);
    private AuthService service;

    @BeforeEach
    void preparar() {
        when(encoder.encode(anyString())).thenReturn("hash-relleno");
        service = new AuthService(repo, encoder, tokens, limitador, RELOJ);
    }

    static final java.time.Clock RELOJ = java.time.Clock.fixed(Instant.parse("2026-10-01T15:00:30.789Z"),
            co.livorno.controlstock.comun.config.ConfiguracionTiempo.ZONA);

    private Usuario conPasswordTemporal() {
        Usuario u = new Usuario("Ana", "ana@livorno.test", "hash-actual", Rol.VENDEDOR);
        u.asignarPasswordTemporal("hash-actual", java.time.LocalDateTime.parse("2026-10-01T09:00:00"));
        return u;
    }

    @Test
    void cambiarPassword_valida_limpiaObligacionFijaFechaYEmiteTokenNuevo() {
        Usuario u = conPasswordTemporal();
        when(repo.findById(1L)).thenReturn(Optional.of(u));
        when(encoder.matches("Actual-123456", "hash-actual")).thenReturn(true);
        when(encoder.matches("Nueva-1234567", "hash-actual")).thenReturn(false);
        when(encoder.encode("Nueva-1234567")).thenReturn("hash-nuevo");
        when(tokens.emitir(u)).thenReturn(new TokenService.TokenEmitido("tok-nuevo", Instant.now()));

        LoginResponse r = service.cambiarPassword(1L,
                new co.livorno.controlstock.auth.dto.CambiarPasswordRequest("Actual-123456", "Nueva-1234567"));

        assertThat(r.token()).isEqualTo("tok-nuevo");
        assertThat(r.usuario().debeCambiarPassword()).isFalse();
        assertThat(u.getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(u.isDebeCambiarPassword()).isFalse();
        assertThat(u.getPasswordCambiadoEn()).isEqualTo(java.time.LocalDateTime.parse("2026-10-01T10:00:30"));
        verify(repo).saveAndFlush(u);
    }

    @Test
    void cambiarPassword_actualIncorrecta_lanza422SinCambios() {
        Usuario u = conPasswordTemporal();
        when(repo.findById(1L)).thenReturn(Optional.of(u));
        when(encoder.matches("mala", "hash-actual")).thenReturn(false);

        assertThatThrownBy(() -> service.cambiarPassword(1L,
                new co.livorno.controlstock.auth.dto.CambiarPasswordRequest("mala", "Nueva-1234567")))
                .isInstanceOf(co.livorno.controlstock.comun.excepcion.ExcepcionNegocio.class)
                .hasMessage("La contraseña actual no es correcta.");
        assertThat(u.isDebeCambiarPassword()).isTrue();
        verify(tokens, never()).emitir(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cambiarPassword_nuevaIgualALaActual_lanza422() {
        Usuario u = conPasswordTemporal();
        when(repo.findById(1L)).thenReturn(Optional.of(u));
        when(encoder.matches("Actual-123456", "hash-actual")).thenReturn(true);

        assertThatThrownBy(() -> service.cambiarPassword(1L,
                new co.livorno.controlstock.auth.dto.CambiarPasswordRequest("Actual-123456", "Actual-123456")))
                .isInstanceOf(co.livorno.controlstock.comun.excepcion.ExcepcionNegocio.class)
                .hasMessage("La nueva contraseña debe ser distinta de la actual.");
        verify(repo, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void login_emailInexistente_ejecutaMatchesContraHashDeRellenoYFalla() {
        when(repo.findByEmail("nadie@livorno.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest(" Nadie@Livorno.test ", "clave"), "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Credenciales inválidas");

        verify(encoder).matches("clave", "hash-relleno");
        verify(tokens, never()).emitir(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void login_claveBloqueada_lanzaAntesDeConsultarUsuarioNiVerificarPassword() {
        org.mockito.Mockito.doThrow(new co.livorno.controlstock.comun.excepcion.DemasiadosIntentosException(300))
                .when(limitador).verificarNoBloqueado("ana@livorno.test", "9.9.9.9");

        assertThatThrownBy(() -> service.login(new LoginRequest("Ana@Livorno.test", "clave"), "9.9.9.9"))
                .isInstanceOf(co.livorno.controlstock.comun.excepcion.DemasiadosIntentosException.class);

        verify(repo, never()).findByEmail(anyString());
        verify(encoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_fallo_registraFalloYExito_registraExito() {
        when(repo.findByEmail("nadie@livorno.test")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(new LoginRequest("nadie@livorno.test", "x"), "1.2.3.4"))
                .isInstanceOf(BadCredentialsException.class);
        verify(limitador).registrarFallo("nadie@livorno.test", "1.2.3.4");

        Usuario u = new Usuario("Ana", "ana@livorno.test", "hash-real", Rol.VENDEDOR);
        when(repo.findByEmail("ana@livorno.test")).thenReturn(Optional.of(u));
        when(encoder.matches("clave", "hash-real")).thenReturn(true);
        when(tokens.emitir(u)).thenReturn(new TokenService.TokenEmitido("tok", Instant.now()));
        service.login(new LoginRequest("ana@livorno.test", "clave"), "1.2.3.4");
        verify(limitador).registrarExito("ana@livorno.test", "1.2.3.4");
    }

    @Test
    void login_conPasswordTemporal_informaDebeCambiarPassword() {
        Usuario u = new Usuario("Ana", "ana@livorno.test", "hash-real", Rol.VENDEDOR);
        u.asignarPasswordTemporal("hash-real", java.time.LocalDateTime.parse("2026-10-01T10:00:00"));
        when(repo.findByEmail("ana@livorno.test")).thenReturn(Optional.of(u));
        when(encoder.matches("clave", "hash-real")).thenReturn(true);
        when(tokens.emitir(u)).thenReturn(new TokenService.TokenEmitido("tok", Instant.now()));

        assertThat(service.login(new LoginRequest("ana@livorno.test", "clave"), "1.2.3.4")
                .usuario().debeCambiarPassword()).isTrue();
    }

    @Test
    void login_usuarioInactivo_fallaAunqueLaClaveCoincida() {
        Usuario u = new Usuario("Ana", "ana@livorno.test", "hash-real", Rol.VENDEDOR);
        u.desactivar();
        when(repo.findByEmail("ana@livorno.test")).thenReturn(Optional.of(u));
        when(encoder.matches("clave", "hash-real")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@livorno.test", "clave"), "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_emailConMayusculasYEspacios_normalizaYEmiteToken() {
        Usuario u = new Usuario("Ana", "ana@livorno.test", "hash-real", Rol.VENDEDOR);
        Instant expira = Instant.parse("2026-10-02T00:00:00Z");
        when(repo.findByEmail("ana@livorno.test")).thenReturn(Optional.of(u));
        when(encoder.matches("clave", "hash-real")).thenReturn(true);
        when(tokens.emitir(u)).thenReturn(new TokenService.TokenEmitido("tok", expira));

        LoginResponse r = service.login(new LoginRequest("  ANA@Livorno.TEST ", "clave"), "127.0.0.1");

        assertThat(r.token()).isEqualTo("tok");
        assertThat(r.tipo()).isEqualTo("Bearer");
        assertThat(r.expiraEn()).isEqualTo(expira);
        assertThat(r.usuario().email()).isEqualTo("ana@livorno.test");
        assertThat(r.usuario().rol()).isEqualTo("VENDEDOR");
    }

    @Test
    void login_passwordIncorrectaConUsuarioActivo_fallaSinEmitirToken() {
        Usuario u = new Usuario("Ana", "ana@livorno.test", "hash-real", Rol.VENDEDOR);
        when(repo.findByEmail("ana@livorno.test")).thenReturn(Optional.of(u));
        when(encoder.matches("mala", "hash-real")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@livorno.test", "mala"), "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class);

        verify(tokens, never()).emitir(org.mockito.ArgumentMatchers.any());
    }
}
