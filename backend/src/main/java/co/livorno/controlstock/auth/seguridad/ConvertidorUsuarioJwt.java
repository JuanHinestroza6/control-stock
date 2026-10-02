package co.livorno.controlstock.auth.seguridad;

import co.livorno.controlstock.comun.config.ConfiguracionTiempo;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Recarga el usuario por id (claim sub) en cada peticion: inexistente o inactivo implica 401 y el rol aplicado
 * es el vigente en base de datos, no el del token.
 */
public class ConvertidorUsuarioJwt implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UsuarioRepository usuarios;

    public ConvertidorUsuarioJwt(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long id;
        try {
            id = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new BadCredentialsException("Token con sujeto inválido");
        }
        Usuario u = usuarios.findById(id).filter(Usuario::isActivo)
                .orElseThrow(() -> new BadCredentialsException("Usuario inexistente o inactivo"));
        // Revocación (ADR-005): un token emitido antes del último cambio de contraseña ya no vale.
        // Ambos instantes en segundos; emitido en el mismo segundo del cambio sigue siendo válido.
        if (u.getPasswordCambiadoEn() != null && jwt.getIssuedAt() != null) {
            long cambio = u.getPasswordCambiadoEn().atZone(ConfiguracionTiempo.ZONA).toInstant().getEpochSecond();
            if (jwt.getIssuedAt().getEpochSecond() < cambio) {
                throw new BadCredentialsException("Token emitido antes del último cambio de contraseña");
            }
        }
        UsuarioAutenticado principal = new UsuarioAutenticado(u.getId(), u.getEmail(), u.getNombre(), u.getRol().name(),
                u.isDebeCambiarPassword());
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRol().name())));
    }
}
