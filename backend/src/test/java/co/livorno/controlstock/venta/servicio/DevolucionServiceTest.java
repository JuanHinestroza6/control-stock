package co.livorno.controlstock.venta.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.servicio.MovimientoService;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import co.livorno.controlstock.venta.dto.DevolucionRequest;
import co.livorno.controlstock.venta.dto.DevolucionResponse;
import co.livorno.controlstock.venta.modelo.Devolucion;
import co.livorno.controlstock.venta.modelo.Venta;
import co.livorno.controlstock.venta.repositorio.DevolucionRepository;
import co.livorno.controlstock.venta.repositorio.VentaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DevolucionServiceTest {

    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock VentaRepository ventas;
    @Mock DevolucionRepository devoluciones;
    @Mock UsuarioRepository usuarios;
    @Mock MovimientoService movimientos;
    @Mock UsuarioActual usuarioActual;

    DevolucionService servicio;
    Venta venta;

    @BeforeEach
    void preparar() {
        servicio = new DevolucionService(ventas, devoluciones, usuarios, movimientos, usuarioActual, RELOJ);
        Producto p = new Producto("LIV001", "Camiseta", new Categoria("Camisetas"), new BigDecimal("20000"),
                new BigDecimal("59900"), null, null);
        Variante v = new Variante(p, "LIV001-M-NEG", Talla.M, "NEGRO", 1);
        ReflectionTestUtils.setField(v, "id", 5L);
        venta = new Venta(new Usuario("Ana", "ana@livorno.co", "h", Rol.VENDEDOR), LocalDateTime.of(2026, 10, 2, 9, 0));
        venta.agregarLinea(v, 3, new BigDecimal("59900"), new BigDecimal("59900"), new BigDecimal("20000"));
        ReflectionTestUtils.setField(venta, "id", 42L);
        ReflectionTestUtils.setField(venta.getDetalles().get(0), "id", 11L);
    }

    private void conVentaYUsuario() {
        when(ventas.bloquear(42L)).thenReturn(Optional.of(venta));
    }

    private void conUsuario() {
        when(usuarioActual.id()).thenReturn(3L);
        when(usuarios.findById(3L)).thenReturn(Optional.of(new Usuario("Luis", "luis@livorno.co", "h", Rol.ADMIN)));
        when(devoluciones.saveAndFlush(any(Devolucion.class))).thenAnswer(i -> {
            Devolucion d = i.getArgument(0);
            ReflectionTestUtils.setField(d, "id", 9L);
            return d;
        });
    }

    private static DevolucionRequest pedido(Long detalle, int cantidad, boolean apta) {
        return new DevolucionRequest(detalle, cantidad, "talla equivocada", apta);
    }

    @Test
    void registrar_devolucionApta_sumaStockConMovimientoDevolucion() {
        conVentaYUsuario();
        conUsuario();
        when(devoluciones.sumaDevuelta(11L)).thenReturn(1);

        DevolucionResponse r = servicio.registrar(42L, pedido(11L, 2, true));

        assertThat(r.id()).isEqualTo(9L);
        assertThat(r.cantidad()).isEqualTo(2);
        assertThat(r.usuarioNombre()).isEqualTo("Luis");
        assertThat(r.aptaParaVenta()).isTrue();
        ArgumentCaptor<SolicitudMovimiento> c = ArgumentCaptor.forClass(SolicitudMovimiento.class);
        verify(movimientos).registrar(c.capture());
        assertThat(c.getValue().tipo()).isEqualTo(TipoMovimiento.DEVOLUCION);
        assertThat(c.getValue().varianteId()).isEqualTo(5L);
        assertThat(c.getValue().cantidad()).isEqualTo(2);
        assertThat(c.getValue().referenciaTipo()).isEqualTo("DEVOLUCION");
        assertThat(c.getValue().referenciaId()).isEqualTo(9L);
        assertThat(c.getValue().usuarioId()).isEqualTo(3L);
    }

    @Test
    void registrar_devolucionNoApta_soloSeRegistraSinMovimiento() {
        conVentaYUsuario();
        conUsuario();
        when(devoluciones.sumaDevuelta(11L)).thenReturn(0);

        DevolucionResponse r = servicio.registrar(42L, pedido(11L, 3, false));

        assertThat(r.aptaParaVenta()).isFalse();
        verify(devoluciones).saveAndFlush(any(Devolucion.class));
        verify(movimientos, never()).registrar(any());
    }

    @Test
    void registrar_cantidadSuperiorALoPendiente_lanza422() {
        conVentaYUsuario();
        when(devoluciones.sumaDevuelta(11L)).thenReturn(2);

        assertThatThrownBy(() -> servicio.registrar(42L, pedido(11L, 2, true)))
                .isInstanceOf(ExcepcionNegocio.class)
                .hasMessage("Solo quedan 1 unidades por devolver de LIV001-M-NEG.");
        verify(devoluciones, never()).saveAndFlush(any());
        verify(movimientos, never()).registrar(any());
    }

    @Test
    void registrar_ventaAnulada_lanza422() {
        conVentaYUsuario();
        venta.anular(new Usuario("Root", "r@livorno.co", "h", Rol.ADMIN), LocalDateTime.now(RELOJ), "motivo");

        assertThatThrownBy(() -> servicio.registrar(42L, pedido(11L, 1, true)))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("anulada");
        verify(movimientos, never()).registrar(any());
    }

    @Test
    void registrar_detalleDeOtraVenta_lanza404() {
        conVentaYUsuario();

        assertThatThrownBy(() -> servicio.registrar(42L, pedido(99L, 1, true)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void registrar_ventaInexistente_lanza404() {
        when(ventas.bloquear(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(7L, pedido(11L, 1, true)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
