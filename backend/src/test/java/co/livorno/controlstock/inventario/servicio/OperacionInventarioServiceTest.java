package co.livorno.controlstock.inventario.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.AjusteRequest;
import co.livorno.controlstock.inventario.dto.EntradaRequest;
import co.livorno.controlstock.inventario.dto.LineaEntradaRequest;
import co.livorno.controlstock.inventario.dto.MovimientoResponse;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.Movimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OperacionInventarioServiceTest {

    VarianteRepository variantes = mock(VarianteRepository.class);
    MovimientoService movimientos = mock(MovimientoService.class);
    UsuarioActual usuarioActual = mock(UsuarioActual.class);
    OperacionInventarioService servicio = new OperacionInventarioService(variantes, movimientos, usuarioActual);

    Producto producto;
    Producto otro;

    @BeforeEach
    void preparar() {
        when(usuarioActual.obtener()).thenReturn(new UsuarioAutenticado(9L, "i@livorno.test", "Ines", "INVENTARIO"));
        Categoria cat = mock(Categoria.class);
        producto = new Producto("LIV001", "Camiseta", cat, new BigDecimal("20000"), new BigDecimal("59900"), null, null);
        otro = new Producto("LIV002", "Hoodie", cat, new BigDecimal("30000"), new BigDecimal("99900"), null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(producto, "id", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(otro, "id", 2L);
        when(movimientos.registrar(any())).thenAnswer(inv -> {
            SolicitudMovimiento s = inv.getArgument(0);
            Variante v = variantes.findById(s.varianteId()).orElseThrow();
            return Movimiento.de(v, s.tipo(), s.cantidad(), 0, 0, s.costoUnitario(), s.usuarioId(),
                    LocalDateTime.of(2026, 10, 2, 10, 0), s.referenciaTipo(), s.referenciaId(), s.observacion());
        });
    }

    private Variante variante(long id, Producto p, String sku) {
        Variante v = new Variante(p, sku, Talla.M, "NEGRO", 2);
        org.springframework.test.util.ReflectionTestUtils.setField(v, "id", id);
        when(variantes.findById(id)).thenReturn(Optional.of(v));
        return v;
    }

    private static LineaEntradaRequest linea(long id, int cant, String costo) {
        return new LineaEntradaRequest(id, cant, new BigDecimal(costo));
    }

    @Test
    void registrarEntrada_costoPositivo_creaUnMovimientoPorLineaYActualizaPrecioCompra() {
        variante(10, producto, "LIV001-M-NEG");
        variante(11, producto, "LIV001-L-NEG");

        List<MovimientoResponse> r = servicio.registrarEntrada(
                new EntradaRequest("Pedido", List.of(linea(10, 5, "25000"), linea(11, 3, "25000.00"))));

        assertThat(r).hasSize(2);
        assertThat(r.get(0).tipo()).isEqualTo(TipoMovimiento.ENTRADA);
        assertThat(r.get(0).usuarioNombre()).isEqualTo("Ines");
        assertThat(r.get(0).costoUnitario()).isEqualByComparingTo("25000");
        assertThat(producto.getPrecioCompra()).isEqualByComparingTo("25000.00");
        ArgumentCaptor<SolicitudMovimiento> c = ArgumentCaptor.forClass(SolicitudMovimiento.class);
        verify(movimientos, org.mockito.Mockito.times(2)).registrar(c.capture());
        assertThat(c.getAllValues()).allSatisfy(s -> {
            assertThat(s.referenciaTipo()).isNull();
            assertThat(s.observacion()).isEqualTo("Pedido");
            assertThat(s.usuarioId()).isEqualTo(9L);
        });
    }

    @Test
    void registrarEntrada_costoCero_seRegistraPeroNoCambiaPrecioCompra() {
        variante(10, producto, "LIV001-M-NEG");

        servicio.registrarEntrada(new EntradaRequest(null, List.of(linea(10, 5, "0"))));

        verify(movimientos).registrar(any());
        assertThat(producto.getPrecioCompra()).isEqualByComparingTo("20000.00");
    }

    @Test
    void registrarEntrada_productosDistintosConCostosDistintos_actualizaCadaUno() {
        variante(10, producto, "LIV001-M-NEG");
        variante(20, otro, "LIV002-M-NEG");

        servicio.registrarEntrada(new EntradaRequest(null, List.of(linea(10, 1, "21000"), linea(20, 1, "31000"))));

        assertThat(producto.getPrecioCompra()).isEqualByComparingTo("21000");
        assertThat(otro.getPrecioCompra()).isEqualByComparingTo("31000");
    }

    @Test
    void registrarEntrada_costosDistintosDelMismoProducto_422SinMoverStock() {
        variante(10, producto, "LIV001-M-NEG");
        variante(11, producto, "LIV001-L-NEG");

        assertThatThrownBy(() -> servicio.registrarEntrada(
                new EntradaRequest(null, List.of(linea(10, 1, "21000"), linea(11, 1, "22000")))))
                .isInstanceOf(ExcepcionNegocio.class)
                .hasMessage("Las variantes de LIV001 deben tener el mismo costo en una entrada.");
        verify(movimientos, never()).registrar(any());
    }

    @Test
    void registrarEntrada_varianteRepetida_400() {
        variante(10, producto, "LIV001-M-NEG");

        assertThatThrownBy(() -> servicio.registrarEntrada(
                new EntradaRequest(null, List.of(linea(10, 1, "21000"), linea(10, 2, "21000")))))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("La variante 10 está repetida en la entrada.");
    }

    @Test
    void registrarEntrada_varianteOProductoInactivo_422SinMoverStock() {
        Variante inactiva = variante(10, producto, "LIV001-M-NEG");
        inactiva.desactivar();
        variante(20, otro, "LIV002-M-NEG");
        otro.desactivar();

        assertThatThrownBy(() -> servicio.registrarEntrada(new EntradaRequest(null, List.of(linea(10, 1, "1")))))
                .isInstanceOf(ExcepcionNegocio.class).hasMessage("La variante LIV001-M-NEG está inactiva.");
        assertThatThrownBy(() -> servicio.registrarEntrada(new EntradaRequest(null, List.of(linea(20, 1, "1")))))
                .isInstanceOf(ExcepcionNegocio.class).hasMessage("La variante LIV002-M-NEG está inactiva.");
        verify(movimientos, never()).registrar(any());
    }

    @Test
    void registrarAjuste_sobreVarianteInactiva_seRegistra() {
        variante(10, producto, "LIV001-M-NEG").desactivar();

        MovimientoResponse r = servicio.registrarAjuste(
                new AjusteRequest(10L, TipoMovimiento.AJUSTE_POSITIVO, 2, "  Conteo físico  "));

        assertThat(r.tipo()).isEqualTo(TipoMovimiento.AJUSTE_POSITIVO);
        assertThat(r.observacion()).isEqualTo("Conteo físico");
        assertThat(r.costoUnitario()).isNull();
    }

    @Test
    void registrarAjuste_tipoQueNoEsAjuste_400() {
        assertThatThrownBy(() -> servicio.registrarAjuste(
                new AjusteRequest(10L, TipoMovimiento.VENTA, 2, "Intento de venta")))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(movimientos, never()).registrar(any());
    }

    @Test
    void registrarEntrada_lineasDesordenadas_registraOrdenadasPorVarianteId() {
        variante(10, producto, "LIV001-M-NEG");
        variante(11, producto, "LIV001-L-NEG");
        variante(20, otro, "LIV002-M-NEG");

        servicio.registrarEntrada(new EntradaRequest(null,
                List.of(linea(20, 1, "31000"), linea(10, 1, "21000"), linea(11, 1, "21000"))));

        ArgumentCaptor<SolicitudMovimiento> c = ArgumentCaptor.forClass(SolicitudMovimiento.class);
        verify(movimientos, org.mockito.Mockito.times(3)).registrar(c.capture());
        assertThat(c.getAllValues()).extracting(SolicitudMovimiento::varianteId).containsExactly(10L, 11L, 20L);
    }

    @Test
    void registrarEntrada_observacionEnBlancoOConEspacios_seGuardaNullORecortada() {
        variante(10, producto, "LIV001-M-NEG");
        ArgumentCaptor<SolicitudMovimiento> c = ArgumentCaptor.forClass(SolicitudMovimiento.class);

        servicio.registrarEntrada(new EntradaRequest("   ", List.of(linea(10, 1, "0"))));
        servicio.registrarEntrada(new EntradaRequest("  Pedido 7  ", List.of(linea(10, 1, "0"))));

        verify(movimientos, org.mockito.Mockito.times(2)).registrar(c.capture());
        assertThat(c.getAllValues()).extracting(SolicitudMovimiento::observacion).containsExactly(null, "Pedido 7");
    }
}
