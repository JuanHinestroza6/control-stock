package co.livorno.controlstock.auth.dto;

import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.usuario.modelo.Usuario;

public record UsuarioSesionResponse(Long id, String nombre, String email, String rol, boolean debeCambiarPassword) {

    /** Sesión sin cambio de contraseña pendiente. */
    public UsuarioSesionResponse(Long id, String nombre, String email, String rol) {
        this(id, nombre, email, rol, false);
    }

    public static UsuarioSesionResponse de(Usuario u) {
        return new UsuarioSesionResponse(u.getId(), u.getNombre(), u.getEmail(), u.getRol().name(),
                u.isDebeCambiarPassword());
    }

    public static UsuarioSesionResponse de(UsuarioAutenticado u) {
        return new UsuarioSesionResponse(u.id(), u.nombre(), u.email(), u.rol(), u.debeCambiarPassword());
    }
}
