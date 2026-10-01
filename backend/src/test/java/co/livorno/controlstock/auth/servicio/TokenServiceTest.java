package co.livorno.controlstock.auth.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.auth.config.PropiedadesJwt;
import co.livorno.controlstock.comun.config.ConfiguracionTiempo;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class TokenServiceTest {

    private static final PropiedadesJwt PROPS = new PropiedadesJwt("secreto-de-prueba-con-mas-de-32-bytes-ok", Duration.ofHours(8));
    private static final PropiedadesJwt OTRAS = new PropiedadesJwt("otro-secreto-completamente-distinto-32b+", Duration.ofHours(8));
    private final ConfiguracionSeguridad config = new ConfiguracionSeguridad();

    private Usuario usuario() {
        Usuario u = new Usuario("Ana Pérez", "ana@livorno.test", "hash", Rol.VENDEDOR);
        ReflectionTestUtils.setField(u, "id", 7L);
        return u;
    }

    private TokenService servicio(PropiedadesJwt props, Instant ahora) {
        return new TokenService(config.jwtEncoder(props), PROPS,
                Clock.fixed(ahora, ConfiguracionTiempo.ZONA));
    }

    @Test
    void emitir_usuarioValido_generaTokenConClaimsYExpiracion() {
        // dado
        Instant ahora = Instant.now();
        TokenService servicio = servicio(PROPS, ahora);

        // cuando
        TokenService.TokenEmitido emitido = servicio.emitir(usuario());
        Jwt jwt = config.jwtDecoder(PROPS).decode(emitido.token());

        // entonces
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat((String) jwt.getClaim("rol")).isEqualTo("VENDEDOR");
        assertThat((String) jwt.getClaim("nombre")).isEqualTo("Ana Pérez");
        assertThat(emitido.expiraEn()).isEqualTo(ahora.plus(Duration.ofHours(8)));
        assertThat(jwt.getExpiresAt().getEpochSecond()).isEqualTo(emitido.expiraEn().getEpochSecond());
    }

    @Test
    void decodificar_tokenVencido_lanzaExcepcion() {
        TokenService servicio = servicio(PROPS, Instant.parse("2020-01-01T00:00:00Z"));
        String token = servicio.emitir(usuario()).token();

        JwtDecoder decoder = config.jwtDecoder(PROPS);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void decodificar_tokenConOtroIssuer_lanzaExcepcion() {
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer("otro-emisor").subject("7")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        String token = config.jwtEncoder(PROPS).encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(
                        org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();

        assertThatThrownBy(() -> config.jwtDecoder(PROPS).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void decodificar_tokenFirmadoConOtroSecreto_lanzaExcepcion() {
        String token = servicio(OTRAS, Instant.now()).emitir(usuario()).token();

        assertThatThrownBy(() -> config.jwtDecoder(PROPS).decode(token)).isInstanceOf(JwtException.class);
    }
}
