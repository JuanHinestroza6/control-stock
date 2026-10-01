package co.livorno.controlstock.comun.seguridad;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Acceso al usuario autenticado; el principal debe ser un UsuarioAutenticado (lo establece la capa auth). */
@Component
public class UsuarioActual {

    public Optional<UsuarioAutenticado> opcional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public UsuarioAutenticado obtener() {
        return opcional().orElseThrow(() -> new IllegalStateException("No hay un usuario autenticado en el contexto"));
    }

    public Long id() {
        return obtener().id();
    }
}
