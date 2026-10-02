package co.livorno.controlstock.usuario.modelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UsuarioTest {

    private static final LocalDateTime AHORA = LocalDateTime.parse("2026-10-01T10:15:30.987654");

    private Usuario usuario() {
        return new Usuario("Ana", "ana@livorno.test", "hash-viejo", Rol.VENDEDOR);
    }

    @Test
    void nuevo_noDebeCambiarPasswordNiTieneFechaDeCambio() {
        Usuario u = usuario();

        assertThat(u.isDebeCambiarPassword()).isFalse();
        assertThat(u.getPasswordCambiadoEn()).isNull();
    }

    @Test
    void asignarPasswordTemporal_marcaDebeCambiarYFijaFechaTruncadaASegundos() {
        Usuario u = usuario();

        u.asignarPasswordTemporal("hash-temporal", AHORA);

        assertThat(u.getPasswordHash()).isEqualTo("hash-temporal");
        assertThat(u.isDebeCambiarPassword()).isTrue();
        assertThat(u.getPasswordCambiadoEn()).isEqualTo(LocalDateTime.parse("2026-10-01T10:15:30"));
    }

    @Test
    void cambiarPassword_quitaLaObligacionYFijaFecha() {
        Usuario u = usuario();
        u.asignarPasswordTemporal("hash-temporal", AHORA.minusDays(1));

        u.cambiarPassword("hash-nuevo", AHORA);

        assertThat(u.getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(u.isDebeCambiarPassword()).isFalse();
        assertThat(u.getPasswordCambiadoEn()).isEqualTo(LocalDateTime.parse("2026-10-01T10:15:30"));
    }

    @Test
    void actualizarDatos_normalizaNombreYCorreo() {
        Usuario u = usuario();

        u.actualizarDatos("  Ana María ", "  ANA.M@Livorno.TEST ");

        assertThat(u.getNombre()).isEqualTo("Ana María");
        assertThat(u.getEmail()).isEqualTo("ana.m@livorno.test");
    }

    @Test
    void cambiarRol_actualizaElRol() {
        Usuario u = usuario();

        u.cambiarRol(Rol.ADMIN);

        assertThat(u.getRol()).isEqualTo(Rol.ADMIN);
    }
}
