package co.livorno.controlstock.catalogo.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class})
@ActiveProfiles("test")
class CategoriaMigracionTest {

    @Autowired CategoriaRepository repositorio;

    @Test
    void v3_cargaLasSeisCategoriasBase() {
        List<String> nombres = repositorio.findAllByOrderByNombreAsc().stream().map(Categoria::getNombre).toList();

        assertThat(nombres).containsExactly("Accesorios", "Camisetas", "Chaquetas", "Hoodies", "Jeans", "Joggers");
    }

    @Test
    void existsByNombre_ignoraMayusculasYTildes() {
        assertThat(repositorio.existsByNombre("CAMISÉTAS")).isTrue();
    }

    @Test
    void guardar_nombreDuplicadoSinTildes_violaUnicidad() {
        assertThatThrownBy(() -> repositorio.saveAndFlush(new Categoria("camisetás")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
