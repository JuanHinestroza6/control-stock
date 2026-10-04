package co.livorno.controlstock.inventario.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.inventario.modelo.EstadoAlerta;
import co.livorno.controlstock.inventario.modelo.TipoAlerta;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V12 con MySQL real: mapeo validate, CHECK, UNIQUE de alerta activa y las sentencias del repositorio. */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class})
@ActiveProfiles("test")
class AlertaRepositoryTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 10, 2, 10, 0);
    private static final LocalDateTime T2 = T1.plusHours(1);

    @Autowired AlertaRepository alertas;
    @Autowired VarianteRepository variantes;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired JdbcTemplate jdbc;

    Long varianteId;

    @BeforeEach
    void preparar() {
        Producto p = productos.saveAndFlush(new Producto("LIV001", "Camiseta",
                categorias.findAllByOrderByNombreAsc().get(0), new BigDecimal("20000"), new BigDecimal("59900"), null,
                null));
        varianteId = variantes.saveAndFlush(new Variante(p, "LIV001-M-NEG", Talla.M, "NEGRO", 1)).getId();
    }

    private int contar(String estado) {
        return jdbc.queryForObject("select count(*) from alerta where variante_id = ? and estado = ?",
                Integer.class, varianteId, estado);
    }

    @Test
    void abrirOActualizar_sinActiva_creaYConActivaSoloActualizaRevisadaEn() {
        alertas.abrirOActualizar(varianteId, "AGOTADO", T1);
        alertas.abrirOActualizar(varianteId, "AGOTADO", T2);

        var lista = alertas.findAll();
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).getCreadaEn()).isEqualTo(T1);
        assertThat(lista.get(0).getRevisadaEn()).isEqualTo(T2);
        assertThat(lista.get(0).getEstado()).isEqualTo(EstadoAlerta.ACTIVA);
    }

    @Test
    void cerrarActiva_marcaAtendidaSinUsuarioYPermiteUnaNuevaActiva() {
        alertas.abrirOActualizar(varianteId, "STOCK_BAJO", T1);

        int cerradas = alertas.cerrarActiva(varianteId, TipoAlerta.STOCK_BAJO, T2);
        alertas.abrirOActualizar(varianteId, "STOCK_BAJO", T2);

        assertThat(cerradas).isEqualTo(1);
        assertThat(contar("ATENDIDA")).isEqualTo(1);
        assertThat(contar("ACTIVA")).isEqualTo(1);
        assertThat(alertas.findByVarianteIdAndTipoAndEstado(varianteId, TipoAlerta.STOCK_BAJO, EstadoAlerta.ATENDIDA)
                .orElseThrow().getAtendidaPor()).isNull();
    }

    @Test
    void uniqueActiva_dosActivasDelMismoTipo_seRechazan() {
        insertar("AGOTADO", "ACTIVA", null);

        assertThatThrownBy(() -> insertar("AGOTADO", "ACTIVA", null)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void uniqueActiva_variasAtendidasDelMismoTipo_sePermiten() {
        insertar("AGOTADO", "ATENDIDA", T2);
        insertar("AGOTADO", "ATENDIDA", T2);
        insertar("AGOTADO", "ACTIVA", null);

        assertThat(contar("ATENDIDA")).isEqualTo(2);
    }

    @Test
    void check_estadoAtendidaSinFechaOTipoInvalido_seRechaza() {
        assertThatThrownBy(() -> insertar("AGOTADO", "ATENDIDA", null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertar("OTRO", "ACTIVA", null)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void buscarYResumen_soloVariantesActivasYFiltrosOpcionales() {
        alertas.abrirOActualizar(varianteId, "AGOTADO", T1);
        alertas.abrirOActualizar(varianteId, "STOCK_BAJO", T1);
        alertas.cerrarActiva(varianteId, TipoAlerta.STOCK_BAJO, T2);

        var todas = alertas.buscar(null, null, org.springframework.data.domain.PageRequest.of(0, 10));
        var activas = alertas.buscar(EstadoAlerta.ACTIVA, null, org.springframework.data.domain.PageRequest.of(0, 10));
        var bajo = alertas.buscar(null, TipoAlerta.STOCK_BAJO, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(todas.getTotalElements()).isEqualTo(2);
        assertThat(activas.getContent()).extracting(a -> a.getTipo()).containsExactly(TipoAlerta.AGOTADO);
        assertThat(bajo.getContent()).extracting(a -> a.getEstado()).containsExactly(EstadoAlerta.ATENDIDA);
        assertThat(activas.getContent().get(0).getVariante().getProducto().getNombre()).isEqualTo("Camiseta");
        assertThat(alertas.contarActivasPorTipo()).hasSize(1);
        assertThat(alertas.bloquear(activas.getContent().get(0).getId())).isPresent();

        jdbc.update("update variante set activo = false where id = ?", varianteId);
        assertThat(alertas.buscar(null, null, org.springframework.data.domain.PageRequest.of(0, 10))
                .getTotalElements()).isZero();
        assertThat(alertas.contarActivasPorTipo()).isEmpty();
    }

    private void insertar(String tipo, String estado, LocalDateTime atendidaEn) {
        jdbc.update("insert into alerta (variante_id, tipo, estado, creada_en, revisada_en, atendida_en)"
                + " values (?, ?, ?, ?, ?, ?)", varianteId, tipo, estado, T1, T1, atendidaEn);
    }
}
