package co.livorno.controlstock.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import db.migration.V2__UsuarioAdminInicial;
import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class UsuarioAdminInicialTest {

    @Autowired UsuarioRepository repo;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void flyway_alMigrar_aplicaV1yV2() {
        Integer aplicadas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1','2') AND success = 1",
                Integer.class);

        assertThat(aplicadas).isEqualTo(2);
    }

    @Test
    void flyway_alMigrar_creaAdminConHashBcryptValido() {
        Usuario admin = repo.findByEmail("admin.prueba@livorno.test").orElseThrow();

        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.isActivo()).isTrue();
        assertThat(new BCryptPasswordEncoder().matches("ClaveAdminPrueba123!", admin.getPasswordHash())).isTrue();
    }

    @Test
    void migracionV2_ejecutadaDeNuevo_noDuplicaAdmin() throws Exception {
        try (Connection con = dataSource.getConnection()) {
            V2__UsuarioAdminInicial.insertarAdminSiNoExiste(con,
                    Map.of("admin-email", "otro@livorno.test", "admin-password", "x"), new BCryptPasswordEncoder());
        }

        Integer admins = jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE rol = 'ADMIN'", Integer.class);
        assertThat(admins).isEqualTo(1);
        assertThat(repo.findByEmail("otro@livorno.test")).isEmpty();
    }

    @Test
    void usuario_alPersistir_guardaRolComoStringYAuditoria() {
        Usuario guardado = repo.saveAndFlush(new Usuario("Ana", "  Ana.Vendedora@Livorno.test ", "hash", Rol.VENDEDOR));

        String rolEnBd = jdbc.queryForObject("SELECT rol FROM usuario WHERE id = ?", String.class, guardado.getId());
        Usuario leido = repo.findById(guardado.getId()).orElseThrow();

        assertThat(rolEnBd).isEqualTo("VENDEDOR");
        assertThat(leido.getEmail()).isEqualTo("ana.vendedora@livorno.test");
        assertThat(leido.getCreadoEn()).isNotNull();
        assertThat(leido.getActualizadoEn()).isNotNull();
        repo.delete(leido);
    }
}
