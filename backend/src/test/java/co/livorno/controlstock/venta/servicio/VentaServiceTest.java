package co.livorno.controlstock.venta.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.servicio.MovimientoService;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import co.livorno.controlstock.venta.dto.LineaVentaRequest;
import co.livorno.controlstock.venta.dto.RegistrarVentaRequest;
import co.livorno.controlstock.venta.dto.VentaDetalleResponse;
import co.livorno.controlstock.venta.modelo.Venta;
import co.livorno.controlstock.venta.repositorio.VentaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock VentaRepository ventas;
    @Mock VarianteRepository variantes;
    @Mock UsuarioRepository usuarios;
    @Mock MovimientoService movimientos;
    @Mock UsuarioActual usuarioActual;

    VentaService servicio;
    Producto producto;
    Variante v5;
    Variante v9;

    @BeforeEach
    void preparar() {
        servicio = new VentaService(ventas, variantes, usuarios, movimientos, usuarioActual, RELOJ);
        producto = new Producto("LIV001", "Camiseta", new Categoria("Camisetas"), new BigDecimal("20000"),
                new BigDecimal("59900"), null, null);
        v5 = variante(5L, "LIV001-M-NEG", Talla.M);
        v9 = variante(9L, "LIV001-L-NEG", Talla.L);
    }

    private Variante variante(Long id, String sku, Talla talla) {
        Variante v = new Variante(producto, sku, talla, "NEGRO", 1);
        ReflectionTestUtils.setField(v, "id", id);
        return v;
    }

    private void comoUsuario(String rol) {
        when(usuarioActual.id()).thenReturn(3L);
        when(usuarios.findById(3L)).thenReturn(Optional.of(new Usuario("Ana", "ana@livorno.co", "h", Rol.valueOf(rol))));
        org.mockito.Mockito.lenient().when(usuarioActual.obtener()).thenReturn(new UsuarioAutenticado(3L, "ana@livorno.co", "Ana", rol));
    }

    private void guardarAsignandoId() {
        when(ventas.saveAndFlush(any(Venta.class))).thenAnswer(i -> {
            Venta v = i.getArgument(0);
            ReflectionTestUtils.setField(v, "id", 42L);
            return v;
        });
    }

    private static RegistrarVentaRequest pedido(LineaVentaRequest... l) {
        return new RegistrarVentaRequest(List.of(l));
    }

    @Test
    void registrar_lineasDesordenadas_ordenaPorVarianteYRegistraMovimientosVenta() {
        // dado
        comoUsuario("ADMIN");
        guardarAsignandoId();
        when(variantes.findById(5L)).thenReturn(Optional.of(v5));
        when(variantes.findById(9L)).thenReturn(Optional.of(v9));

        // cuando
        VentaDetalleResponse r = servicio.registrar(pedido(new LineaVentaRequest(9L, 1, null),
                new LineaVentaRequest(5L, 2, new BigDecimal("50000"))));

        // entonces
        assertThat(r.numero()).isEqualTo("V-000042");
        assertThat(r.total()).isEqualByComparingTo("159900.00");
        assertThat(r.descuentoTotal()).isEqualByComparingTo("19800.00");
        assertThat(r.lineas()).extracting(l -> l.varianteId()).containsExactly(5L, 9L);
        assertThat(r.lineas().get(0).precioLista()).isEqualByComparingTo("59900.00");
        assertThat(r.lineas().get(0).costoUnitario()).isEqualByComparingTo("20000.00");
        ArgumentCaptor<SolicitudMovimiento> c = ArgumentCaptor.forClass(SolicitudMovimiento.class);
        var orden = inOrder(ventas, movimientos);
        orden.verify(ventas).saveAndFlush(any(Venta.class));
        orden.verify(movimientos, org.mockito.Mockito.times(2)).registrar(c.capture());
        assertThat(c.getAllValues()).extracting(SolicitudMovimiento::varianteId).containsExactly(5L, 9L);
        assertThat(c.getAllValues()).allSatisfy(s -> {
            assertThat(s.tipo()).isEqualTo(TipoMovimiento.VENTA);
            assertThat(s.referenciaTipo()).isEqualTo("VENTA");
            assertThat(s.referenciaId()).isEqualTo(42L);
            assertThat(s.usuarioId()).isEqualTo(3L);
        });
    }

    @Test
    void registrar_comoVendedor_ocultaElCosto() {
        comoUsuario("VENDEDOR");
        guardarAsignandoId();
        when(variantes.findById(5L)).thenReturn(Optional.of(v5));

        VentaDetalleResponse r = servicio.registrar(pedido(new LineaVentaRequest(5L, 1, null)));

        assertThat(r.lineas().get(0).costoUnitario()).isNull();
        assertThat(r.lineas().get(0).precioUnitario()).isEqualByComparingTo("59900.00");
    }

    @Test
    void registrar_varianteRepetida_lanzaSolicitudInvalida() {
        assertThatThrownBy(() -> servicio.registrar(pedido(new LineaVentaRequest(5L, 1, null),
                new LineaVentaRequest(5L, 2, null)))).isInstanceOf(SolicitudInvalidaException.class);
        verify(ventas, never()).saveAndFlush(any());
    }

    @Test
    void registrar_varianteInactiva_lanza422SinMovimientos() {
        comoUsuario("ADMIN");
        v5.desactivar();
        when(variantes.findById(5L)).thenReturn(Optional.of(v5));

        assertThatThrownBy(() -> servicio.registrar(pedido(new LineaVentaRequest(5L, 1, null))))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("LIV001-M-NEG");
        verify(movimientos, never()).registrar(any());
    }

    @Test
    void registrar_precioSuperiorALista_lanza422() {
        comoUsuario("ADMIN");
        when(variantes.findById(5L)).thenReturn(Optional.of(v5));

        assertThatThrownBy(() -> servicio.registrar(pedido(new LineaVentaRequest(5L, 1, new BigDecimal("60000")))))
                .isInstanceOf(ExcepcionNegocio.class)
                .hasMessage("El precio de LIV001-M-NEG no puede superar el de lista (59900.00).");
        verify(ventas, never()).saveAndFlush(any());
    }

    @Test
    void registrar_varianteInexistente_lanza404() {
        comoUsuario("ADMIN");
        when(variantes.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(pedido(new LineaVentaRequest(5L, 1, null))))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void registrar_stockInsuficiente_propagaElErrorParaRevertirTodo() {
        comoUsuario("ADMIN");
        guardarAsignandoId();
        when(variantes.findById(5L)).thenReturn(Optional.of(v5));
        when(variantes.findById(9L)).thenReturn(Optional.of(v9));
        when(movimientos.registrar(any(SolicitudMovimiento.class)))
                .thenReturn(null).thenThrow(new ExcepcionNegocio("Stock insuficiente"));

        assertThatThrownBy(() -> servicio.registrar(pedido(new LineaVentaRequest(5L, 1, null),
                new LineaVentaRequest(9L, 1, null)))).isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void listar_ordenNoPermitido_lanzaSolicitudInvalida() {
        var pageable = PageRequest.of(0, 20, Sort.by("vendedor"));
        assertThatThrownBy(() -> servicio.listar(null, null, null, pageable))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void listar_hastaAnteriorADesde_lanzaSolicitudInvalida() {
        assertThatThrownBy(() -> servicio.listar(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1), null,
                PageRequest.of(0, 20))).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void obtener_ventaInexistente_lanza404() {
        when(ventas.conDetalle(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.obtener(7L)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
