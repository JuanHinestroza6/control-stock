package co.livorno.controlstock.auth.dto;

import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.usuario.modelo.Usuario;

public record UsuarioSesionResponse(Long id, String nombre, String email, String rol) {

    public static UsuarioSesionResponse de(Usuario u) {
        return new UsuarioSesionResponse(u.getId(), u.getNombre(), u.getEmail(), u.getRol().name());
    }

    public static UsuarioSesionResponse de(UsuarioAutenticado u) {
        return new UsuarioSesionResponse(u.id(), u.nombre(), u.email(), u.rol());
    }
}
