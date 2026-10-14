package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.venta.repositorio.VentaRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class ProteccionesSemillaTest {

    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final VentaRepository ventas = mock(VentaRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MockEnvironment entorno = new MockEnvironment();
    private ProteccionesSemilla protecciones;

    @BeforeEach
    void preparar() {
        entorno.setActiveProfiles("dev", "seed");
        protecciones = new ProteccionesSemilla(entorno, productos, ventas, jdbc);
    }

    @Test
    void validarPerfil_conProd_aborta() {
        // dado el perfil prod activo
        entorno.setActiveProfiles("prod", "seed");
        // entonces
        assertThatThrownBy(protecciones::validarPerfil).isInstanceOf(SemillaAbortadaException.class)
                .hasMessageContaining("dev");
    }

    @Test
    void validarPerfil_soloSeed_aborta() {
        entorno.setActiveProfiles("seed");
        assertThatThrownBy(protecciones::validarPerfil).isInstanceOf(SemillaAbortadaException.class)
                .hasMessageContaining("solo corre con el perfil dev");
    }

    @Test
    void validarPerfil_perfilDesconocido_aborta() {
        entorno.setActiveProfiles("staging", "seed");
        assertThatThrownBy(protecciones::validarPerfil).isInstanceOf(SemillaAbortadaException.class);
    }

    @Test
    void validarPerfil_conDevOTest_noHaceNada() {
        assertThatCode(protecciones::validarPerfil).doesNotThrowAnyException();
        entorno.setActiveProfiles("test", "seed");
        assertThatCode(protecciones::validarPerfil).doesNotThrowAnyException();
    }

    @Test
    void validarFechaFin_hoyOFutura_aborta() {
        LocalDate hoy = LocalDate.of(2026, 10, 4);
        assertThatThrownBy(() -> protecciones.validarFechaFin(hoy, hoy)).isInstanceOf(SemillaAbortadaException.class);
        assertThatThrownBy(() -> protecciones.validarFechaFin(hoy.plusDays(1), hoy))
                .isInstanceOf(SemillaAbortadaException.class);
        assertThatCode(() -> protecciones.validarFechaFin(hoy.minusDays(1), hoy)).doesNotThrowAnyException();
    }

    @Test
    void evaluarBase_baseVacia_devuelveVacia() {
        when(ventas.count()).thenReturn(0L);
        when(productos.count()).thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        assertThat(protecciones.evaluarBase()).isEqualTo(ProteccionesSemilla.Estado.VACIA);
    }

    @Test
    void evaluarBase_conVentas_devuelveYaHayDatos() {
        when(ventas.count()).thenReturn(5L);
        when(productos.count()).thenReturn(30L);

        assertThat(protecciones.evaluarBase()).isEqualTo(ProteccionesSemilla.Estado.YA_HAY_DATOS);
    }

    @Test
    void evaluarBase_productosSinVentas_aborta() {
        when(ventas.count()).thenReturn(0L);
        when(productos.count()).thenReturn(3L);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        assertThatThrownBy(protecciones::evaluarBase).isInstanceOf(SemillaAbortadaException.class)
                .hasMessageContaining("docker compose down -v");
    }

    @Test
    void evaluarBase_movimientosSinVentas_aborta() {
        when(ventas.count()).thenReturn(0L);
        when(productos.count()).thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);

        assertThatThrownBy(protecciones::evaluarBase).isInstanceOf(SemillaAbortadaException.class);
    }
}
