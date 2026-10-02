package co.livorno.controlstock.auth.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

class ConvertidorUsuarioJwtTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final ConvertidorUsuarioJwt convertidor = new ConvertidorUsuarioJwt(repo);

    private static Jwt jwt(String sub, String rolClaim) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").claim("rol", rolClaim)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (sub != null) {
            b.subject(sub);
        }
        return b.build();
    }

    private static Usuario usuario(Rol rol) {
        Usuario u = new Usuario("Ana", "ana@livorno.test", "hash", rol);
        ReflectionTestUtils.setField(u, "id", 3L);
        return u;
    }

    @Test
    void convert_subNoNumerico_lanzaBadCredentials() {
        assertThatThrownBy(() -> convertidor.convert(jwt("abc", "ADMIN"))).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void convert_subAusente_lanzaBadCredentials() {
        assertThatThrownBy(() -> convertidor.convert(jwt(null, "ADMIN"))).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void convert_usuarioInexistente_lanzaBadCredentials() {
        when(repo.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> convertidor.convert(jwt("3", "ADMIN"))).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void convert_usuarioInactivo_lanzaBadCredentials() {
        Usuario u = usuario(Rol.ADMIN);
        u.desactivar();
        when(repo.findById(3L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> convertidor.convert(jwt("3", "ADMIN"))).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void convert_usuarioConPasswordTemporal_principalLlevaDebeCambiarPassword() {
        Usuario u = usuario(Rol.VENDEDOR);
        u.asignarPasswordTemporal("hash", java.time.LocalDateTime.parse("2026-10-01T10:00:00"));
        when(repo.findById(3L)).thenReturn(Optional.of(u));

        Authentication auth = convertidor.convert(jwt("3", "VENDEDOR"));

        assertThat(((UsuarioAutenticado) auth.getPrincipal()).debeCambiarPassword()).isTrue();
    }

    @Test
    void convert_rolDelTokenDistintoAlDeBd_aplicaElRolDeBd() {
        when(repo.findById(3L)).thenReturn(Optional.of(usuario(Rol.VENDEDOR)));

        Authentication auth = convertidor.convert(jwt("3", "ADMIN"));

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_VENDEDOR");
        assertThat(auth.getPrincipal()).isEqualTo(new UsuarioAutenticado(3L, "ana@livorno.test", "Ana", "VENDEDOR"));
        assertThat(auth.isAuthenticated()).isTrue();
    }
}
