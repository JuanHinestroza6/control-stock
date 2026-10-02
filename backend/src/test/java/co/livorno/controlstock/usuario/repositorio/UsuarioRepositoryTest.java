package co.livorno.controlstock.usuario.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class})
@ActiveProfiles("test")
class UsuarioRepositoryTest {

    @Autowired UsuarioRepository repositorio;

    private Usuario guardar(String nombre, String email, Rol rol, boolean activo) {
        Usuario u = new Usuario(nombre, email, "hash", rol);
        if (!activo) {
            u.desactivar();
        }
        return repositorio.saveAndFlush(u);
    }

    @Test
    void buscar_conFiltros_combinaQRolYActivo() {
        guardar("Ana Pérez", "ana.repo@livorno.co", Rol.VENDEDOR, true);
        guardar("Ana Gómez", "ana.gomez.repo@livorno.co", Rol.VENDEDOR, false);
        guardar("Luis", "luis.repo@livorno.co", Rol.INVENTARIO, true);
        var pagina = PageRequest.of(0, 20, Sort.by("nombre"));

        var todos = repositorio.buscar(UsuarioRepository.patronContiene("ana"), null, null, pagina);
        var activos = repositorio.buscar(UsuarioRepository.patronContiene("ana"), Rol.VENDEDOR, true, pagina);
        var porRol = repositorio.buscar(null, Rol.INVENTARIO, null, pagina);

        assertThat(todos.getContent()).extracting(Usuario::getNombre).containsExactly("Ana Gómez", "Ana Pérez");
        assertThat(activos.getContent()).extracting(Usuario::getEmail).containsExactly("ana.repo@livorno.co");
        assertThat(porRol.getContent()).extracting(Usuario::getEmail).contains("luis.repo@livorno.co");
    }

    @Test
    void buscar_conComodinesEnQ_losEscapa() {
        guardar("Uno", "uno.repo@livorno.co", Rol.VENDEDOR, true);
        var pagina = PageRequest.of(0, 20);

        assertThat(repositorio.buscar(UsuarioRepository.patronContiene("%"), null, null, pagina).getContent())
                .isEmpty();
        assertThat(repositorio.buscar(UsuarioRepository.patronContiene("uno_repo"), null, null, pagina)
                .getContent()).isEmpty();
    }

    @Test
    void bloquearAdminsActivos_devuelveSoloAdminsActivos() {
        Usuario activo = guardar("Admin A", "admin.a.repo@livorno.co", Rol.ADMIN, true);
        Usuario inactivo = guardar("Admin B", "admin.b.repo@livorno.co", Rol.ADMIN, false);
        guardar("Vend", "vend.repo@livorno.co", Rol.VENDEDOR, true);

        assertThat(repositorio.bloquearAdminsActivos()).extracting(Usuario::getId)
                .contains(activo.getId()).doesNotContain(inactivo.getId());
    }

    @Test
    void existsByEmailAndIdNot_excluyeAlPropioUsuario() {
        Usuario u = guardar("Dos", "dos.repo@livorno.co", Rol.VENDEDOR, true);

        assertThat(repositorio.existsByEmail("dos.repo@livorno.co")).isTrue();
        assertThat(repositorio.existsByEmailAndIdNot("dos.repo@livorno.co", u.getId())).isFalse();
        assertThat(repositorio.existsByEmailAndIdNot("dos.repo@livorno.co", u.getId() + 1000)).isTrue();
    }
}
