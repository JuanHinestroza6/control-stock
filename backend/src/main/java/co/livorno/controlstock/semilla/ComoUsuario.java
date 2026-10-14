package co.livorno.controlstock.semilla;

import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Ejecuta una acción como un usuario concreto y restaura el contexto de seguridad al terminar (un solo hilo). */
@Component
@Profile("seed")
public class ComoUsuario {

    public <T> T ejecutar(UsuarioAutenticado usuario, Supplier<T> accion) {
        SecurityContext previo = SecurityContextHolder.getContext();
        try {
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(usuario, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol()))));
            SecurityContextHolder.setContext(contexto);
            return accion.get();
        } finally {
            if (previo.getAuthentication() == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.setContext(previo);
            }
        }
    }

    public void ejecutar(UsuarioAutenticado usuario, Runnable accion) {
        ejecutar(usuario, () -> {
            accion.run();
            return null;
        });
    }
}
