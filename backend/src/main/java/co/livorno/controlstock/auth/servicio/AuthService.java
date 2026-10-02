package co.livorno.controlstock.auth.servicio;

import co.livorno.controlstock.auth.dto.CambiarPasswordRequest;
import co.livorno.controlstock.auth.dto.LoginRequest;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import java.time.Clock;
import java.time.LocalDateTime;
import co.livorno.controlstock.auth.dto.LoginResponse;
import co.livorno.controlstock.auth.dto.UsuarioSesionResponse;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final String CLAVE_CAMBIO_PASSWORD = "cambio-password:";
    private static final String IP_NO_APLICA = "-";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final LimitadorIntentosLogin limitador;

    /** Hash de relleno (aleatorio, generado al construir) para igualar tiempos cuando el email no existe. */
    private final String hashRelleno;

    private final Clock clock;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, TokenService tokens,
            LimitadorIntentosLogin limitador, Clock clock) {
        this.clock = clock;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.tokens = tokens;
        this.limitador = limitador;
        this.hashRelleno = encoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Credenciales incorrectas, usuario inexistente o inactivo: mismo error, sin distinguir.
     * El bloqueo por intentos se comprueba antes de verificar la contrasena y no depende de que el email exista.
     */
    public LoginResponse login(LoginRequest solicitud, String ip) {
        String email = solicitud.email().trim().toLowerCase(Locale.ROOT);
        limitador.verificarNoBloqueado(email, ip);

        Usuario usuario = usuarios.findByEmail(email).orElse(null);
        String hash = usuario != null ? usuario.getPasswordHash() : hashRelleno;
        boolean coincide = encoder.matches(solicitud.password(), hash); // siempre se ejecuta (tiempo constante)
        if (usuario == null || !usuario.isActivo() || !coincide) {
            limitador.registrarFallo(email, ip);
            throw new BadCredentialsException("Credenciales inválidas");
        }
        limitador.registrarExito(email, ip);
        TokenService.TokenEmitido t = tokens.emitir(usuario);
        return LoginResponse.de(t.token(), t.expiraEn(), UsuarioSesionResponse.de(usuario));
    }

    /**
     * El usuario autenticado cambia su propia contraseña. Contraseña actual incorrecta es 422 (no 401: la sesión
     * es válida). Fija passwordCambiadoEn (revoca los tokens anteriores) y emite un token nuevo con el mismo reloj,
     * por lo que su iat no es anterior al cambio y sigue siendo válido.
     */
    @Transactional
    public LoginResponse cambiarPassword(Long usuarioId, CambiarPasswordRequest solicitud) {
        String claveLimite = CLAVE_CAMBIO_PASSWORD + usuarioId;
        limitador.verificarNoBloqueado(claveLimite, IP_NO_APLICA);
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", usuarioId));
        if (!encoder.matches(solicitud.actual(), usuario.getPasswordHash())) {
            limitador.registrarFallo(claveLimite, IP_NO_APLICA);
            throw new ExcepcionNegocio("La contraseña actual no es correcta.");
        }
        if (encoder.matches(solicitud.nueva(), usuario.getPasswordHash())) {
            throw new ExcepcionNegocio("La nueva contraseña debe ser distinta de la actual.");
        }
        limitador.registrarExito(claveLimite, IP_NO_APLICA);
        usuario.cambiarPassword(encoder.encode(solicitud.nueva()), LocalDateTime.now(clock));
        usuarios.saveAndFlush(usuario);
        TokenService.TokenEmitido t = tokens.emitir(usuario);
        return LoginResponse.de(t.token(), t.expiraEn(), UsuarioSesionResponse.de(usuario));
    }
}
