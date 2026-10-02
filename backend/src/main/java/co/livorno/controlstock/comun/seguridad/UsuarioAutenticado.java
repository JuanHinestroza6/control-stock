package co.livorno.controlstock.comun.seguridad;

public record UsuarioAutenticado(Long id, String email, String nombre, String rol, boolean debeCambiarPassword) {

    /** Usuario sin cambio de contraseña pendiente. */
    public UsuarioAutenticado(Long id, String email, String nombre, String rol) {
        this(id, email, nombre, rol, false);
    }
}
