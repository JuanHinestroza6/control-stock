package co.livorno.controlstock.usuario.dto;

import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import java.time.LocalDateTime;

/** Nunca incluye el hash de la contraseña. */
public record UsuarioResponse(Long id, String nombre, String email, Rol rol, boolean activo,
        boolean debeCambiarPassword, LocalDateTime creadoEn, LocalDateTime actualizadoEn) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getEmail(), u.getRol(), u.isActivo(),
                u.isDebeCambiarPassword(), u.getCreadoEn(), u.getActualizadoEn());
    }
}
