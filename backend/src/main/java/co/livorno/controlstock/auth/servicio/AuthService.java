package co.livorno.controlstock.auth.servicio;

import co.livorno.controlstock.auth.dto.LoginRequest;
import co.livorno.controlstock.auth.dto.LoginResponse;
import co.livorno.controlstock.auth.dto.UsuarioSesionResponse;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final TokenService tokens;

    /** Hash de relleno (aleatorio, generado al construir) para igualar tiempos cuando el email no existe. */
    private final String hashRelleno;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, TokenService tokens) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.tokens = tokens;
        this.hashRelleno = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    /** Credenciales incorrectas, usuario inexistente o inactivo: mismo error, sin distinguir. */
    public LoginResponse login(LoginRequest solicitud) {
        String email = solicitud.email().trim().toLowerCase();
        Usuario usuario = usuarios.findByEmail(email).orElse(null);
        String hash = usuario != null ? usuario.getPasswordHash() : hashRelleno;
        boolean coincide = encoder.matches(solicitud.password(), hash); // siempre se ejecuta (tiempo constante)
        if (usuario == null || !usuario.isActivo() || !coincide) {
            throw new BadCredentialsException("Credenciales inválidas");
        }
        TokenService.TokenEmitido t = tokens.emitir(usuario);
        return LoginResponse.de(t.token(), t.expiraEn(), UsuarioSesionResponse.de(usuario));
    }
}
