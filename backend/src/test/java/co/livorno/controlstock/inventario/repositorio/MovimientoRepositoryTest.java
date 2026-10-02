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
import co.livorno.controlstock.inventario.modelo.Movimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class})
@ActiveProfiles("test")
class MovimientoRepositoryTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 2, 10, 0);

    @Autowired MovimientoRepository movimientos;
    @Autowired VarianteRepository variantes;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired JdbcTemplate jdbc;

    Variante variante;

    @BeforeEach
    void preparar() {
        Producto p = productos.saveAndFlush(new Producto("LIV001", "Camiseta",
                categorias.findAllByOrderByNombreAsc().get(0), new BigDecimal("20000"), new BigDecimal("59900"), null,
                null));
        variante = variantes.saveAndFlush(new Variante(p, "LIV001-M-NEG", Talla.M, "NEGRO", 1));
    }

    private int insertar(String tipo, int cantidad, int fa, int fd, int ra, int rd, String costo, String refTipo,
            Long refId) {
        return jdbc.update("""
                insert into movimiento (variante_id, tipo, cantidad, fisico_antes, fisico_despues, reservado_antes,
                    reservado_despues, costo_unitario, fecha, referencia_tipo, referencia_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, now(6), ?, ?)""", variante.getId(), tipo, cantidad, fa, fd, ra, rd,
                costo, refTipo, refId);
    }

    @Test
    void save_movimientoDeEntrada_persisteYSeLee() {
        Movimiento m = movimientos.save(Movimiento.de(variante, TipoMovimiento.ENTRADA, 5, 0, 0,
                new BigDecimal("15000"), null, AHORA, "PLAN_COMPRA", 3L, "ingreso"));

        Movimiento leido = movimientos.findById(m.getId()).orElseThrow();
        assertThat(leido.getFisicoDespues()).isEqualTo(5);
        assertThat(leido.getUsuarioId()).isNull();
        assertThat(leido.getCostoUnitario()).isEqualByComparingTo("15000.00");
    }

    @Test
    void check_filasValidas_seAceptan() {
        assertThat(insertar("ENTRADA", 5, 0, 5, 0, 0, "100.00", null, null)).isEqualTo(1);
        assertThat(insertar("VENTA", 2, 5, 3, 0, 0, null, "VENTA", 1L)).isEqualTo(1);
    }

    @Test
    void check_violaciones_seRechazan() {
        // costo en un tipo distinto de ENTRADA
        assertThatThrownBy(() -> insertar("VENTA", 1, 5, 4, 0, 0, "1.00", null, null))
                .isInstanceOf(DataAccessException.class);
        // ENTRADA sin costo
        assertThatThrownBy(() -> insertar("ENTRADA", 1, 0, 1, 0, 0, null, null, null))
                .isInstanceOf(DataAccessException.class);
        // costo negativo
        assertThatThrownBy(() -> insertar("ENTRADA", 1, 0, 1, 0, 0, "-1.00", null, null))
                .isInstanceOf(DataAccessException.class);
        // cantidad 0
        assertThatThrownBy(() -> insertar("VENTA", 0, 5, 5, 0, 0, null, null, null))
                .isInstanceOf(DataAccessException.class);
        // tipo desconocido
        assertThatThrownBy(() -> insertar("OTRO", 1, 5, 4, 0, 0, null, null, null))
                .isInstanceOf(DataAccessException.class);
        // saldo negativo
        assertThatThrownBy(() -> insertar("VENTA", 1, 0, -1, 0, 0, null, null, null))
                .isInstanceOf(DataAccessException.class);
        // reservado mayor que físico
        assertThatThrownBy(() -> insertar("RESERVA", 1, 1, 1, 1, 2, null, null, null))
                .isInstanceOf(DataAccessException.class);
        // referencia incompleta
        assertThatThrownBy(() -> insertar("VENTA", 1, 5, 4, 0, 0, null, "VENTA", null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void repositorio_noExponeBorradoNiActualizacion() {
        assertThat(JpaRepository.class.isAssignableFrom(MovimientoRepository.class)).isFalse();
        assertThat(MovimientoRepository.class.getMethods()).extracting(Method::getName)
                .noneMatch(n -> n.startsWith("delete") || n.startsWith("update"));
    }
}
