package co.livorno.controlstock.usuario.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.usuario.dto.ActualizarUsuarioRequest;
import co.livorno.controlstock.usuario.dto.CrearUsuarioRequest;
import co.livorno.controlstock.usuario.dto.RestablecerPasswordRequest;
import co.livorno.controlstock.usuario.dto.UsuarioResponse;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-01T15:00:30.789Z"),
            co.livorno.controlstock.comun.config.ConfiguracionTiempo.ZONA);

    @Mock UsuarioRepository usuarios;
    @Mock PasswordEncoder encoder;
    @Mock UsuarioActual usuarioActual;

    UsuarioService servicio;

    @BeforeEach
    void iniciar() {
        servicio = new UsuarioService(usuarios, encoder, usuarioActual, RELOJ);
    }

    private Usuario usuario(long id, String email, Rol rol) {
        Usuario u = new Usuario("Nombre " + id, email, "hash", rol);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private void actual(long id) {
        when(usuarioActual.id()).thenReturn(id);
    }

    // ---------- crear ----------

    @Test
    void crear_correoNuevo_normalizaCodificaYMarcaCambioObligatorio() {
        when(usuarios.existsByEmail("ana@livorno.co")).thenReturn(false);
        when(encoder.encode("Temporal-12345")).thenReturn("HASH");
        when(usuarios.saveAndFlush(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponse r = servicio.crear(new CrearUsuarioRequest("  Ana  ", "  ANA@Livorno.CO ", Rol.VENDEDOR,
                "Temporal-12345"));

        assertThat(r.email()).isEqualTo("ana@livorno.co");
        assertThat(r.nombre()).isEqualTo("Ana");
        assertThat(r.rol()).isEqualTo(Rol.VENDEDOR);
        assertThat(r.activo()).isTrue();
        assertThat(r.debeCambiarPassword()).isTrue();
        org.mockito.ArgumentCaptor<Usuario> c = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(c.capture());
        assertThat(c.getValue().getPasswordHash()).isEqualTo("HASH");
        assertThat(c.getValue().getPasswordCambiadoEn()).isEqualTo(LocalDateTime.parse("2026-10-01T10:00:30"));
    }

    @Test
    void crear_correoDuplicado_lanzaConflictoSinGuardar() {
        when(usuarios.existsByEmail("ana@livorno.co")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(new CrearUsuarioRequest("Ana", "ana@livorno.co", Rol.ADMIN,
                "Temporal-12345"))).isInstanceOf(ConflictoException.class);
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void crear_carreraEnUnicidad_lanzaConflicto() {
        when(encoder.encode(any())).thenReturn("HASH");
        when(usuarios.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> servicio.crear(new CrearUsuarioRequest("Ana", "ana@livorno.co", Rol.ADMIN,
                "Temporal-12345"))).isInstanceOf(ConflictoException.class);
    }

    @Test
    void crear_correoConLetraITurca_seNormalizaConLocaleRoot() {
        when(encoder.encode(any())).thenReturn("HASH");
        when(usuarios.saveAndFlush(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        java.util.Locale anterior = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"));
        try {
            UsuarioResponse r = servicio.crear(new CrearUsuarioRequest("Isa", "ISA@LIVORNO.CO", Rol.VENDEDOR,
                    "Temporal-12345"));
            assertThat(r.email()).isEqualTo("isa@livorno.co");
        } finally {
            java.util.Locale.setDefault(anterior);
        }
    }

    // ---------- actualizar ----------

    @Test
    void actualizar_cambiaRolPropio_lanza422() {
        Usuario yo = usuario(1, "yo@livorno.co", Rol.ADMIN);
        when(usuarios.findById(1L)).thenReturn(Optional.of(yo));
        actual(1);

        assertThatThrownBy(() -> servicio.actualizar(1L, new ActualizarUsuarioRequest("Yo", "yo@livorno.co",
                Rol.VENDEDOR))).isInstanceOf(ExcepcionNegocio.class).hasMessage("No puedes cambiar tu propio rol.");
        assertThat(yo.getRol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    void actualizar_propioMismoRol_actualizaNombreYCorreo() {
        Usuario yo = usuario(1, "yo@livorno.co", Rol.ADMIN);
        when(usuarios.findById(1L)).thenReturn(Optional.of(yo));
        when(usuarios.existsByEmailAndIdNot("nuevo@livorno.co", 1L)).thenReturn(false);
        when(usuarios.saveAndFlush(yo)).thenReturn(yo);

        UsuarioResponse r = servicio.actualizar(1L, new ActualizarUsuarioRequest(" Nuevo ", "Nuevo@Livorno.co",
                Rol.ADMIN));

        assertThat(r.nombre()).isEqualTo("Nuevo");
        assertThat(r.email()).isEqualTo("nuevo@livorno.co");
    }

    @Test
    void actualizar_quitaRolAlUnicoAdminActivo_lanza422() {
        Usuario unico = usuario(2, "a@livorno.co", Rol.ADMIN);
        when(usuarios.findById(2L)).thenReturn(Optional.of(unico));
        when(usuarios.bloquearAdminsActivos()).thenReturn(List.of(unico));
        actual(1);

        assertThatThrownBy(() -> servicio.actualizar(2L, new ActualizarUsuarioRequest("A", "a@livorno.co",
                Rol.INVENTARIO))).isInstanceOf(ExcepcionNegocio.class)
                .hasMessage("Debe quedar al menos un administrador activo.");
        assertThat(unico.getRol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    void actualizar_quitaRolConOtroAdminActivo_aplica() {
        Usuario a = usuario(2, "a@livorno.co", Rol.ADMIN);
        Usuario b = usuario(3, "b@livorno.co", Rol.ADMIN);
        when(usuarios.findById(2L)).thenReturn(Optional.of(a));
        when(usuarios.bloquearAdminsActivos()).thenReturn(List.of(a, b));
        when(usuarios.existsByEmailAndIdNot("a@livorno.co", 2L)).thenReturn(false);
        when(usuarios.saveAndFlush(a)).thenReturn(a);
        actual(3);

        UsuarioResponse r = servicio.actualizar(2L, new ActualizarUsuarioRequest("A", "a@livorno.co", Rol.CONTABILIDAD));

        assertThat(r.rol()).isEqualTo(Rol.CONTABILIDAD);
    }

    @Test
    void actualizar_correoDeOtroUsuario_lanzaConflicto() {
        Usuario u = usuario(2, "u@livorno.co", Rol.VENDEDOR);
        when(usuarios.findById(2L)).thenReturn(Optional.of(u));
        when(usuarios.existsByEmailAndIdNot("otro@livorno.co", 2L)).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(2L, new ActualizarUsuarioRequest("U", "OTRO@livorno.co",
                Rol.VENDEDOR))).isInstanceOf(ConflictoException.class);
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void actualizar_inexistente_lanza404() {
        when(usuarios.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(9L, new ActualizarUsuarioRequest("U", "u@livorno.co",
                Rol.VENDEDOR))).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---------- estado ----------

    @Test
    void cambiarEstado_desactivarseASiMismo_lanza422() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(1, "yo@livorno.co", Rol.ADMIN)));
        actual(1);

        assertThatThrownBy(() -> servicio.cambiarEstado(1L, false)).isInstanceOf(ExcepcionNegocio.class)
                .hasMessage("No puedes desactivar tu propia cuenta.");
    }

    @Test
    void cambiarEstado_desactivarUnicoAdminActivo_lanza422() {
        Usuario unico = usuario(2, "a@livorno.co", Rol.ADMIN);
        when(usuarios.findById(2L)).thenReturn(Optional.of(unico));
        when(usuarios.bloquearAdminsActivos()).thenReturn(List.of(unico));
        actual(1);

        assertThatThrownBy(() -> servicio.cambiarEstado(2L, false)).isInstanceOf(ExcepcionNegocio.class)
                .hasMessage("Debe quedar al menos un administrador activo.");
        assertThat(unico.isActivo()).isTrue();
    }

    @Test
    void cambiarEstado_desactivarAdminConOtroActivo_desactiva() {
        Usuario a = usuario(2, "a@livorno.co", Rol.ADMIN);
        Usuario b = usuario(3, "b@livorno.co", Rol.ADMIN);
        when(usuarios.findById(2L)).thenReturn(Optional.of(a));
        when(usuarios.bloquearAdminsActivos()).thenReturn(List.of(a, b));
        when(usuarios.saveAndFlush(a)).thenReturn(a);
        actual(3);

        assertThat(servicio.cambiarEstado(2L, false).activo()).isFalse();
    }

    @Test
    void cambiarEstado_desactivarNoAdmin_noBloqueaAdmins() {
        Usuario v = usuario(4, "v@livorno.co", Rol.VENDEDOR);
        when(usuarios.findById(4L)).thenReturn(Optional.of(v));
        when(usuarios.saveAndFlush(v)).thenReturn(v);
        actual(1);

        assertThat(servicio.cambiarEstado(4L, false).activo()).isFalse();
        verify(usuarios, never()).bloquearAdminsActivos();
    }

    @Test
    void cambiarEstado_reactivar_activaYNoValidaReglas() {
        Usuario v = usuario(4, "v@livorno.co", Rol.VENDEDOR);
        v.desactivar();
        when(usuarios.findById(4L)).thenReturn(Optional.of(v));
        when(usuarios.saveAndFlush(v)).thenReturn(v);

        assertThat(servicio.cambiarEstado(4L, true).activo()).isTrue();
    }

    @Test
    void cambiarEstado_mismoEstado_esNoOpAunqueSeaUnoMismo() {
        Usuario yo = usuario(1, "yo@livorno.co", Rol.ADMIN);
        when(usuarios.findById(1L)).thenReturn(Optional.of(yo));

        assertThat(servicio.cambiarEstado(1L, true).activo()).isTrue();
        verify(usuarios, never()).saveAndFlush(any());
    }

    // ---------- restablecer ----------

    @Test
    void restablecerPassword_propioUsuario_lanza422() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(1, "yo@livorno.co", Rol.ADMIN)));
        actual(1);

        assertThatThrownBy(() -> servicio.restablecerPassword(1L, new RestablecerPasswordRequest("Temporal-12345")))
                .isInstanceOf(ExcepcionNegocio.class).hasMessage("Usa Mi cuenta para cambiar tu contraseña.");
        verify(encoder, never()).encode(any());
    }

    @Test
    void restablecerPassword_otroUsuario_marcaCambioYFijaPasswordCambiadoEnTruncado() {
        Usuario v = usuario(4, "v@livorno.co", Rol.VENDEDOR);
        v.cambiarPassword("viejo", LocalDateTime.parse("2026-01-01T00:00:00"));
        when(usuarios.findById(4L)).thenReturn(Optional.of(v));
        when(encoder.encode("Temporal-12345")).thenReturn("NUEVO");
        when(usuarios.saveAndFlush(v)).thenReturn(v);
        actual(1);

        servicio.restablecerPassword(4L, new RestablecerPasswordRequest("Temporal-12345"));

        assertThat(v.getPasswordHash()).isEqualTo("NUEVO");
        assertThat(v.isDebeCambiarPassword()).isTrue();
        assertThat(v.getPasswordCambiadoEn()).isEqualTo(LocalDateTime.parse("2026-10-01T10:00:30"));
    }

    // ---------- listar ----------

    @Test
    void listar_ordenFueraDeListaBlanca_lanzaSolicitudInvalida() {
        var pageable = PageRequest.of(0, 20, Sort.by("passwordHash"));

        assertThatThrownBy(() -> servicio.listar(null, null, null, pageable))
                .isInstanceOf(SolicitudInvalidaException.class);
    }
}
