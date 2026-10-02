package co.livorno.controlstock.usuario.servicio;

import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.web.Ordenamiento;
import co.livorno.controlstock.usuario.dto.ActualizarUsuarioRequest;
import co.livorno.controlstock.usuario.dto.CrearUsuarioRequest;
import co.livorno.controlstock.usuario.dto.RestablecerPasswordRequest;
import co.livorno.controlstock.usuario.dto.UsuarioResponse;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {

    public static final Set<String> ORDEN_PERMITIDO = Set.of("nombre", "email", "rol", "creadoEn");

    static final String CORREO_DUPLICADO = "Ya existe un usuario con ese correo.";
    static final String PROPIA_CUENTA = "No puedes desactivar tu propia cuenta.";
    static final String PROPIO_ROL = "No puedes cambiar tu propio rol.";
    static final String PROPIA_PASSWORD = "Usa Mi cuenta para cambiar tu contraseña.";
    static final String ULTIMO_ADMIN = "Debe quedar al menos un administrador activo.";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final UsuarioActual usuarioActual;
    private final Clock clock;

    public Page<UsuarioResponse> listar(String q, Rol rol, Boolean activo, Pageable pageable) {
        Ordenamiento.validar(pageable, ORDEN_PERMITIDO);
        return usuarios.buscar(UsuarioRepository.patronContiene(q), rol, activo, pageable).map(UsuarioResponse::de);
    }

    public UsuarioResponse obtener(Long id) {
        return UsuarioResponse.de(buscar(id));
    }

    /** La contraseña la asigna el ADMIN: es temporal y obliga a cambiarla en el primer inicio de sesión. */
    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest req) {
        String email = normalizar(req.email());
        if (usuarios.existsByEmail(email)) {
            throw new ConflictoException(CORREO_DUPLICADO);
        }
        Usuario usuario = new Usuario(req.nombre().strip(), email, null, req.rol());
        usuario.asignarPasswordTemporal(encoder.encode(req.passwordTemporal()), LocalDateTime.now(clock));
        return UsuarioResponse.de(guardar(usuario));
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, ActualizarUsuarioRequest req) {
        Usuario usuario = buscar(id);
        if (usuario.getRol() != req.rol()) {
            if (esElMismo(id)) {
                throw new ExcepcionNegocio(PROPIO_ROL);
            }
            if (usuario.getRol() == Rol.ADMIN) {
                exigirOtroAdminActivo(usuario);
            }
        }
        String email = normalizar(req.email());
        if (usuarios.existsByEmailAndIdNot(email, id)) {
            throw new ConflictoException(CORREO_DUPLICADO);
        }
        usuario.actualizarDatos(req.nombre(), email);
        usuario.cambiarRol(req.rol());
        return UsuarioResponse.de(guardar(usuario));
    }

    /** Igual al estado actual es un no-op. Nadie se desactiva a sí mismo ni deja el sistema sin ADMIN activo. */
    @Transactional
    public UsuarioResponse cambiarEstado(Long id, boolean activo) {
        Usuario usuario = buscar(id);
        if (usuario.isActivo() != activo) {
            if (activo) {
                usuario.activar();
            } else {
                if (esElMismo(id)) {
                    throw new ExcepcionNegocio(PROPIA_CUENTA);
                }
                if (usuario.getRol() == Rol.ADMIN) {
                    exigirOtroAdminActivo(usuario);
                }
                usuario.desactivar();
            }
            guardar(usuario);
        }
        return UsuarioResponse.de(usuario);
    }

    /** Contraseña temporal: obliga a cambiarla al iniciar sesión y revoca los tokens vigentes del usuario. */
    @Transactional
    public void restablecerPassword(Long id, RestablecerPasswordRequest req) {
        Usuario usuario = buscar(id);
        if (esElMismo(id)) {
            throw new ExcepcionNegocio(PROPIA_PASSWORD);
        }
        usuario.asignarPasswordTemporal(encoder.encode(req.passwordTemporal()), LocalDateTime.now(clock));
        guardar(usuario);
    }

    /**
     * Bloquea a los ADMIN activos (pesimista) y exige que, además del objetivo, quede al menos otro.
     * Si el objetivo ya está inactivo no figura entre los bloqueados y no hay riesgo.
     */
    private void exigirOtroAdminActivo(Usuario objetivo) {
        List<Usuario> admins = usuarios.bloquearAdminsActivos();
        boolean objetivoCuenta = admins.stream().anyMatch(a -> a.getId().equals(objetivo.getId()));
        if (objetivoCuenta && admins.size() <= 1) {
            throw new ExcepcionNegocio(ULTIMO_ADMIN);
        }
    }

    private boolean esElMismo(Long id) {
        return id.equals(usuarioActual.id());
    }

    private static String normalizar(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private Usuario buscar(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }

    private Usuario guardar(Usuario usuario) {
        try {
            return usuarios.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException(CORREO_DUPLICADO);
        }
    }
}
