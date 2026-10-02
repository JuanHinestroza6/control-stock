package co.livorno.controlstock.auth.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.comun.config.ConfiguracionTiempo;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

/** Revocación por cambio de contraseña (ADR-005): iat contra passwordCambiadoEn, ambos en segundos. */
class ConvertidorUsuarioJwtRevocacionTest {

    private static final LocalDateTime CAMBIO = LocalDateTime.parse("2026-10-01T10:00:00");
    private static final Instant CAMBIO_INSTANTE = CAMBIO.atZone(ConfiguracionTiempo.ZONA).toInstant();

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final ConvertidorUsuarioJwt convertidor = new ConvertidorUsuarioJwt(repo);

    private Jwt jwtEmitidoEn(Instant iat) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject("3").claim("rol", "VENDEDOR")
                .issuedAt(iat).expiresAt(iat.plusSeconds(3600)).build();
    }

    private void usuarioConCambio(LocalDateTime cambio) {
        Usuario u = new Usuario("Ana", "ana@livorno.test", "hash", Rol.VENDEDOR);
        ReflectionTestUtils.setField(u, "id", 3L);
        if (cambio != null) {
            u.cambiarPassword("hash2", cambio);
        }
        when(repo.findById(3L)).thenReturn(Optional.of(u));
    }

    @Test
    void convert_tokenUnSegundoAntesDelCambio_lanza401() {
        usuarioConCambio(CAMBIO);

        assertThatThrownBy(() -> convertidor.convert(jwtEmitidoEn(CAMBIO_INSTANTE.minusSeconds(1))))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void convert_tokenEnElMismoSegundoDelCambio_esValido() {
        usuarioConCambio(CAMBIO);

        assertThat(convertidor.convert(jwtEmitidoEn(CAMBIO_INSTANTE)).isAuthenticated()).isTrue();
    }

    @Test
    void convert_tokenPosteriorAlCambio_esValido() {
        usuarioConCambio(CAMBIO);

        assertThat(convertidor.convert(jwtEmitidoEn(CAMBIO_INSTANTE.plusSeconds(5))).isAuthenticated()).isTrue();
    }

    @Test
    void convert_cambioConFraccionDeSegundo_seComparaEnSegundosTruncados() {
        usuarioConCambio(CAMBIO.plusNanos(900_000_000)); // se guarda truncado a 10:00:00

        assertThat(convertidor.convert(jwtEmitidoEn(CAMBIO_INSTANTE)).isAuthenticated()).isTrue();
    }

    @Test
    void convert_sinPasswordCambiadoEn_esValido() {
        usuarioConCambio(null);

        assertThat(convertidor.convert(jwtEmitidoEn(Instant.parse("2020-01-01T00:00:00Z"))).isAuthenticated())
                .isTrue();
    }

    @Test
    void convert_sinIat_conCambio_lanza401() {
        usuarioConCambio(CAMBIO);
        Jwt sinIat = Jwt.withTokenValue("t").header("alg", "HS256").subject("3").claim("rol", "VENDEDOR")
                .expiresAt(CAMBIO_INSTANTE.plusSeconds(3600)).build();

        assertThatThrownBy(() -> convertidor.convert(sinIat)).isInstanceOf(BadCredentialsException.class);
    }
}
