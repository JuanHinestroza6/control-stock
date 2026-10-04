package co.livorno.controlstock.venta.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.EstadoReserva;
import co.livorno.controlstock.inventario.modelo.Reserva;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.repositorio.ReservaRepository;
import co.livorno.controlstock.inventario.servicio.MovimientoService;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import co.livorno.controlstock.venta.dto.ConvertirReservasRequest;
import co.livorno.controlstock.venta.dto.PrecioReservaRequest;
import co.livorno.controlstock.venta.modelo.Venta;
import co.livorno.controlstock.venta.repositorio.DevolucionRepository;
import co.livorno.controlstock.venta.repositorio.VentaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VentaDesdeReservasServiceTest {

    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneId.of("America/Bogota"));
    static final LocalDateTime AHORA = LocalDateTime.now(RELOJ);

    @Mock VentaRepository ventas;
    @Mock DevolucionRepository devoluciones;
    @Mock VarianteRepository variantes;
    @Mock UsuarioRepository usuarios;
    @Mock MovimientoService movimientos;
    @Mock UsuarioActual usuarioActual;
    @Mock ReservaRepository reservas;

    VentaService servicio;
    Producto producto;
    Variante v5;
    Variante v9;

    @BeforeEach
    void preparar() {
        servicio = new VentaService(ventas, devoluciones, variantes, usuarios, movimientos, usuarioActual, RELOJ,
                reservas);
        producto = new Producto("LIV001", "Camiseta", new Categoria("Camisetas"), new BigDecimal("20000"),
                new BigDecimal("59900"), null, null);
        v5 = variante(5L, "LIV001-M-NEG", Talla.M);
        v9 = variante(9L, "LIV001-L-NEG", Talla.L);
        lenient().when(usuarioActual.id()).thenReturn(3L);
        lenient().when(usuarioActual.obtener()).thenReturn(new UsuarioAutenticado(3L, "a@l.co", "Ana", "VENDEDOR"));
        lenient().when(usuarios.findById(3L))
                .thenReturn(Optional.of(new Usuario("Ana", "ana@livorno.co", "h", Rol.VENDEDOR)));
        lenient().when(ventas.saveAndFlush(any(Venta.class))).thenAnswer(i -> {
            Venta v = i.getArgument(0);
            ReflectionTestUtils.setField(v, "id", 42L);
            return v;
        });
    }

    private Variante variante(Long id, String sku, Talla talla) {
        Variante v = new Variante(producto, sku, talla, "NEGRO", 1);
        ReflectionTestUtils.setField(v, "id", id);
        return v;
    }

    private Reserva reserva(Long id, Variante v, int cantidad, String contacto) {
        Reserva r = Reserva.de(v, cantidad, "Cliente", contacto, AHORA.minusHours(1), AHORA.plusHours(10), 3L);
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    private void bloqueadas(Reserva... rs) {
        when(reservas.bloquearVarias(any())).thenReturn(List.of(rs));
    }

    @Test
    void convertir_dosReservasDeLaMismaVariante_agrupaEnUnaLineaYCierraAmbas() {
        Reserva r1 = reserva(1L, v5, 2, "3001234567");
        Reserva r2 = reserva(2L, v5, 3, "300 123 4567"); // mismo contacto normalizado
        Reserva r3 = reserva(3L, v9, 1, "3001234567");
        bloqueadas(r1, r2, r3);

        var r = servicio.registrarDesdeReservas(new ConvertirReservasRequest(List.of(1L, 2L, 3L), null));

        assertThat(r.lineas()).hasSize(2);
        assertThat(r.lineas().get(0).cantidad()).isEqualTo(5);
        assertThat(r.lineas().get(0).precioUnitario()).isEqualByComparingTo("59900.00");
        assertThat(r.lineas().get(0).costoUnitario()).isNull(); // VENDEDOR no ve el costo
        assertThat(r.total()).isEqualByComparingTo("359400.00"); // 6 x 59900
        assertThat(r.reservas()).containsExactly("R-000001", "R-000002", "R-000003");
        ArgumentCaptor<SolicitudMovimiento> c = ArgumentCaptor.forClass(SolicitudMovimiento.class);
        verify(movimientos, times(2)).registrar(c.capture());
        assertThat(c.getAllValues()).allSatisfy(s -> {
            assertThat(s.tipo()).isEqualTo(TipoMovimiento.VENTA_RESERVA);
            assertThat(s.referenciaTipo()).isEqualTo("VENTA");
            assertThat(s.referenciaId()).isEqualTo(42L);
            assertThat(s.usuarioId()).isEqualTo(3L);
        });
        assertThat(c.getAllValues().get(0).observacion()).isEqualTo("Reservas R-000001, R-000002");
        assertThat(r1.getEstado()).isEqualTo(EstadoReserva.CONVERTIDA);
        assertThat(r1.getVentaId()).isEqualTo(42L);
        assertThat(r3.getCerradaPor()).isEqualTo(3L);
    }

    @Test
    void convertir_precioOpcional_seAplicaPorVarianteYNullTomaLaListaActual() {
        Reserva r1 = reserva(1L, v5, 1, "a@x.co");
        Reserva r2 = reserva(2L, v9, 1, "A@X.CO");
        bloqueadas(r1, r2);

        var r = servicio.registrarDesdeReservas(new ConvertirReservasRequest(List.of(1L, 2L),
                List.of(new PrecioReservaRequest(5L, new BigDecimal("50000")))));

        assertThat(r.lineas().get(0).precioUnitario()).isEqualByComparingTo("50000.00");
        assertThat(r.lineas().get(1).precioUnitario()).isEqualByComparingTo("59900.00");
        assertThat(r.descuentoTotal()).isEqualByComparingTo("9900.00");
    }

    @Test
    void convertir_costoUnitario_esElPrecioCompraAlConvertir() {
        bloqueadas(reserva(1L, v5, 1, "a@x.co"));
        when(usuarioActual.obtener()).thenReturn(new UsuarioAutenticado(3L, "a@l.co", "Ana", "ADMIN"));

        var r = servicio.registrarDesdeReservas(new ConvertirReservasRequest(List.of(1L), null));

        assertThat(r.lineas().get(0).costoUnitario()).isEqualByComparingTo("20000.00");
    }

    @Test
    void convertir_precioMayorALista_lanza422SinMovimientos() {
        bloqueadas(reserva(1L, v5, 1, "a@x.co"));

        assertThatThrownBy(() -> servicio.registrarDesdeReservas(new ConvertirReservasRequest(List.of(1L),
                List.of(new PrecioReservaRequest(5L, new BigDecimal("60000"))))))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("no puede superar el de lista");
        verify(movimientos, never()).registrar(any());
    }

    @Test
    void convertir_clientesDistintos_lanza422() {
        bloqueadas(reserva(1L, v5, 1, "a@x.co"), reserva(2L, v9, 1, "b@x.co"));

        assertThatThrownBy(() -> servicio.registrarDesdeReservas(new ConvertirReservasRequest(List.of(1L, 2L), null)))
                .isInstanceOf(ExcepcionNegocio.class).hasMessage("Las reservas deben ser del mismo cliente.");
        verify(ventas, never()).saveAndFlush(any());
    }

    @Test
    void convertir_reservaNoActiva_lanza409() {
        Reserva cancelada = reserva(1L, v5, 1, "a@x.co");
        cancelada.cancelar("ya no la quiere", AHORA, 3L);
        bloqueadas(cancelada);

        assertThatThrownBy(() -> servicio.registrarDesdeReservas(new ConvertirReservasRequest(List.of(1L), null)))
                .isInstanceOf(ConflictoException.class).hasMessage("La reserva R-000001 ya está CANCELADA.");
        verify(movimientos, never()).registrar(any());
    }

    @Test
    void convertir_reservaInexistente_lanza404() {
        bloqueadas(reserva(1L, v5, 1, "a@x.co"));

        assertThatThrownBy(() -> servicio.registrarDesdeReservas(new ConvertirReservasRequest(List.of(1L, 77L), null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void convertir_idsRepetidosOPrecioDeVarianteAjena_lanza400() {
        assertThatThrownBy(() -> servicio.registrarDesdeReservas(new ConvertirReservasRequest(List.of(1L, 1L), null)))
                .isInstanceOf(SolicitudInvalidaException.class);

        bloqueadas(reserva(1L, v5, 1, "a@x.co"));
        assertThatThrownBy(() -> servicio.registrarDesdeReservas(new ConvertirReservasRequest(List.of(1L),
                List.of(new PrecioReservaRequest(9L, BigDecimal.TEN)))))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void convertir_varianteInactiva_lanza422() {
        v5.desactivar();
        bloqueadas(reserva(1L, v5, 1, "a@x.co"));

        assertThatThrownBy(() -> servicio.registrarDesdeReservas(new ConvertirReservasRequest(List.of(1L), null)))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("inactiva");
        verify(movimientos, never()).registrar(any());
    }
}
