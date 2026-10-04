package co.livorno.controlstock.semilla;

import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.usuario.dto.CrearUsuarioRequest;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Usuarios de demostración, uno por rol (ADR-008, D8). Se crean con el repositorio y el {@code PasswordEncoder}, sin
 * contraseña temporal. Si el correo ya existe se reutiliza sin tocarlo.
 */
@Component
@Profile("seed")
public class UsuariosDemo {

    record Demo(String nombre, String email, Rol rol) {
    }

    static final List<Demo> USUARIOS = List.of(
            new Demo("Administrador Demo", "admin@livorno.co", Rol.ADMIN),
            new Demo("Inventario Demo", "inventario@livorno.co", Rol.INVENTARIO),
            new Demo("Ventas Demo", "ventas@livorno.co", Rol.VENDEDOR),
            new Demo("Contabilidad Demo", "contabilidad@livorno.co", Rol.CONTABILIDAD));

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final Validator validador;

    public UsuariosDemo(UsuarioRepository usuarios, PasswordEncoder encoder, Validator validador) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.validador = validador;
    }

    /** Valida {@code SEED_PASSWORD} con la política de contraseñas; aborta con un mensaje claro si no la cumple. */
    public void validarPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new SemillaAbortadaException("Falta SEED_PASSWORD (app.semilla.password): contraseña de los "
                    + "usuarios demo, de 12 a 72 caracteres.");
        }
        var errores = validador.validateValue(CrearUsuarioRequest.class, "passwordTemporal", password);
        if (!errores.isEmpty()) {
            String detalle = errores.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining("; "));
            throw new SemillaAbortadaException("SEED_PASSWORD no cumple la política de contraseñas: " + detalle + ".");
        }
    }

    /** Crea los que falten y devuelve el usuario por rol. Requiere una transacción activa. */
    public Map<Rol, UsuarioAutenticado> crearOReutilizar(String password) {
        validarPassword(password);
        Map<Rol, UsuarioAutenticado> resultado = new EnumMap<>(Rol.class);
        for (Demo demo : USUARIOS) {
            Usuario usuario = usuarios.findByEmail(demo.email())
                    .orElseGet(() -> usuarios.save(
                            new Usuario(demo.nombre(), demo.email(), encoder.encode(password), demo.rol())));
            resultado.put(demo.rol(), new UsuarioAutenticado(usuario.getId(), usuario.getEmail(),
                    usuario.getNombre(), usuario.getRol().name(), false));
        }
        return resultado;
    }
}
