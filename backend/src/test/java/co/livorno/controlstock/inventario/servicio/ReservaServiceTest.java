package co.livorno.controlstock.inventario.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
import co.livorno.controlstock.inventario.dto.CancelarReservaRequest;
import co.livorno.controlstock.inventario.dto.CrearReservaRequest;
import co.livorno.controlstock.inventario.dto.ExtenderReservaRequest;
import co.livorno.controlstock.inventario.dto.LineaReservaRequest;
import co.livorno.controlstock.inventario.dto.ReservaResponse;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.EstadoReserva;
import co.livorno.controlstock.inventario.modelo.Reserva;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.repositorio.ReservaRepository;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneId.of("America/Bogota"));
    static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 2, 10, 0);

    @Mock ReservaRepository reservas;
    @Mock VarianteRepository variantes;
    @Mock UsuarioRepository usuarios;
    @Mock MovimientoService movimientos;
    @Mock UsuarioActual usuarioActual;

    ReservaService servicio;
    Producto producto;
    Variante v5;
    Variante v9;

    @BeforeEach
    void preparar() {
        servicio = new ReservaService(reservas, variantes, usuarios, movimientos, usuarioActual, RELOJ, 48);
        producto = new Producto("LIV001", "Camiseta", new Categoria("Camisetas"), new BigDecimal("20000"),
                new BigDecimal("59900"), null, null);
        v5 = variante(5L, "LIV001-M-NEG", Talla.M);
        v9 = variante(9L, "LIV001-L-NEG", Talla.L);
        lenient().when(usuarioActual.id()).thenReturn(3L);
        Usuario ana = new Usuario("Ana", "ana@livorno.co", "h", Rol.VENDEDOR);
        ReflectionTestUtils.setField(ana, "id", 3L);
        lenient().when(usuarios.findAllById(any())).thenReturn(List.of(ana));
    }

    private Variante variante(Long id, String sku, Talla talla) {
        Variante v = new Variante(producto, sku, talla, "NEGRO", 1);
        ReflectionTestUtils.setField(v, "id", id);
        return v;
    }

    private void guardarAsignandoIds() {
        long[] siguiente = {100L};
        when(reservas.saveAndFlush(any(Reserva.class))).thenAnswer(i -> {
            Reserva r = i.getArgument(0);
            ReflectionTestUtils.setField(r, "id", siguiente[0]++);
            return r;
        });
    }

    private static CrearReservaRequest crear(Integer vigencia, LineaReservaRequest... lineas) {
        return new CrearReservaRequest("  Ana   Pérez ", " 300 123 ", vigencia, List.of(lineas));
    }

    private Reserva activa(Long id, Variante v, int cantidad) {
        Reserva r = Reserva.de(v, cantidad, "Ana", "300", AHORA, AHORA.plusHours(48), 3L);
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    @Test
    void crear_dosLineas_creaUnaReservaYUnMovimientoRESERVAPorLineaOrdenadosPorId() {
        guardarAsignandoIds();
        when(variantes.findById(5L)).thenReturn(Optional.of(v5));
        when(variantes.findById(9L)).thenReturn(Optional.of(v9));

        List<ReservaResponse> res = servicio.crear(crear(null, new LineaReservaRequest(9L, 1),
                new LineaReservaRequest(5L, 2)));

        assertThat(res).extracting(ReservaResponse::varianteId).containsExactly(5L, 9L);
        assertThat(res.get(0).numero()).isEqualTo("R-000100");
        assertThat(res.get(0).estado()).isEqualTo(EstadoReserva.ACTIVA);
        assertThat(res.get(0).clienteNombre()).isEqualTo("Ana Pérez");
        assertThat(res.get(0).clienteContacto()).isEqualTo("300 123");
        assertThat(res.get(0).creadaPor()).isEqualTo("Ana");
        // vigencia por defecto 48 h con reloj fijo
        assertThat(res.get(0).venceEn()).isEqualTo(AHORA.plusHours(48));
        ArgumentCaptor<SolicitudMovimiento> c = ArgumentCaptor.forClass(SolicitudMovimiento.class);
        verify(movimientos, Mockito.times(2)).registrar(c.capture());
        assertThat(c.getAllValues()).extracting(SolicitudMovimiento::varianteId).containsExactly(5L, 9L);
        assertThat(c.getAllValues().get(0)).satisfies(s -> {
            assertThat(s.tipo()).isEqualTo(TipoMovimiento.RESERVA);
            assertThat(s.cantidad()).isEqualTo(2);
            assertThat(s.referenciaTipo()).isEqualTo("RESERVA");
            assertThat(s.referenciaId()).isEqualTo(100L);
            assertThat(s.usuarioId()).isEqualTo(3L);
        });
    }

    @Test
    void crear_conVigenciaExplicita_usaEsaVigencia() {
        guardarAsignandoIds();
        when(variantes.findById(5L)).thenReturn(Optional.of(v5));

        List<ReservaResponse> res = servicio.crear(crear(6, new LineaReservaRequest(5L, 1)));

        assertThat(res.get(0).venceEn()).isEqualTo(AHORA.plusHours(6));
    }

    @Test
    void crear_nombreOContactoCortosOLargosTrasRecortar_lanza400SinGuardar() {
        var linea = List.of(new LineaReservaRequest(5L, 1));
        assertThatThrownBy(() -> servicio.crear(new CrearReservaRequest("  A  ", "3001234", null, linea)))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("nombre del cliente");
        assertThatThrownBy(() -> servicio.crear(new CrearReservaRequest("Ana", "  12  ", null, linea)))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("contacto del cliente");
        assertThatThrownBy(() -> servicio.crear(new CrearReservaRequest("x".repeat(121), "3001234", null, linea)))
                .isInstanceOf(SolicitudInvalidaException.class);
        Mockito.verifyNoInteractions(reservas, movimientos);
    }

    @Test
    void crear_varianteRepetida_lanza400SinGuardar() {
        assertThatThrownBy(() -> servicio.crear(crear(null, new LineaReservaRequest(5L, 1),
                new LineaReservaRequest(5L, 2))))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("La variante 5 está repetida en la reserva.");
        Mockito.verifyNoInteractions(reservas, movimientos);
    }

    @Test
    void crear_varianteInexistente_lanza404() {
        when(variantes.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(crear(null, new LineaReservaRequest(5L, 1))))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void crear_varianteInactiva_lanza422SinMovimiento() {
        v5.desactivar();
        when(variantes.findById(5L)).thenReturn(Optional.of(v5));

        assertThatThrownBy(() -> servicio.crear(crear(null, new LineaReservaRequest(5L, 1))))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("LIV001-M-NEG");
        verify(movimientos, never()).registrar(any());
        verify(reservas, never()).saveAndFlush(any());
    }

    @Test
    void crear_stockInsuficiente_propagaElErrorDelMovimiento() {
        guardarAsignandoIds();
        when(variantes.findById(5L)).thenReturn(Optional.of(v5));
        when(movimientos.registrar(any())).thenThrow(new ExcepcionNegocio("Stock insuficiente para LIV001-M-NEG"));

        assertThatThrownBy(() -> servicio.crear(crear(null, new LineaReservaRequest(5L, 1))))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("Stock insuficiente");
    }

    @Test
    void cancelar_activa_generaLiberacionConUsuarioYObservacion() {
        Reserva r = activa(12L, v5, 2);
        when(reservas.bloquear(12L)).thenReturn(Optional.of(r));

        ReservaResponse res = servicio.cancelar(12L, new CancelarReservaRequest("  Cliente desistió "));

        assertThat(res.estado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(res.motivoCancelacion()).isEqualTo("Cliente desistió");
        assertThat(res.cerradaPor()).isEqualTo("Ana");
        InOrder orden = Mockito.inOrder(reservas, movimientos);
        orden.verify(reservas).bloquear(12L); // reserva antes que variante (MovimientoService carga la variante)
        ArgumentCaptor<SolicitudMovimiento> c = ArgumentCaptor.forClass(SolicitudMovimiento.class);
        orden.verify(movimientos).registrar(c.capture());
        assertThat(c.getValue().tipo()).isEqualTo(TipoMovimiento.LIBERACION);
        assertThat(c.getValue().cantidad()).isEqualTo(2);
        assertThat(c.getValue().usuarioId()).isEqualTo(3L);
        assertThat(c.getValue().referenciaTipo()).isEqualTo("RESERVA");
        assertThat(c.getValue().referenciaId()).isEqualTo(12L);
        assertThat(c.getValue().observacion()).isEqualTo("Cancelada");
    }

    @Test
    void cancelar_noActiva_lanza409SinMovimiento() {
        Reserva r = activa(12L, v5, 2);
        r.convertir(77L, AHORA, 3L);
        when(reservas.bloquear(12L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> servicio.cancelar(12L, new CancelarReservaRequest("Cliente desistió")))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("La reserva R-000012 ya está CONVERTIDA.");
        verify(movimientos, never()).registrar(any());
    }

    @Test
    void cancelar_inexistente_lanza404() {
        when(reservas.bloquear(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cancelar(1L, new CancelarReservaRequest("Cliente desistió")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void extender_activa_fijaVenceEnAhoraMasHoras() {
        Reserva r = activa(12L, v5, 2);
        when(reservas.bloquear(12L)).thenReturn(Optional.of(r));

        ReservaResponse res = servicio.extender(12L, new ExtenderReservaRequest(24));

        assertThat(res.venceEn()).isEqualTo(AHORA.plusHours(24));
        verify(movimientos, never()).registrar(any());
    }

    @Test
    void extender_masDeSieteDias_lanza422() {
        Reserva r = Reserva.de(v5, 1, "Ana", "300", AHORA.minusDays(6), AHORA.plusHours(1), 3L);
        ReflectionTestUtils.setField(r, "id", 12L);
        when(reservas.bloquear(12L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> servicio.extender(12L, new ExtenderReservaRequest(48)))
                .isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void extender_noActiva_lanza409() {
        Reserva r = activa(12L, v5, 2);
        r.vencer(AHORA);
        when(reservas.bloquear(12L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> servicio.extender(12L, new ExtenderReservaRequest(24)))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void vencer_activa_generaLiberacionDelSistemaYLaMarcaVencida() {
        Reserva r = activa(12L, v5, 2);
        when(reservas.bloquear(12L)).thenReturn(Optional.of(r));

        boolean vencio = servicio.vencer(12L, AHORA.plusHours(49));

        assertThat(vencio).isTrue();
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.VENCIDA);
        assertThat(r.getCerradaPor()).isNull();
        ArgumentCaptor<SolicitudMovimiento> c = ArgumentCaptor.forClass(SolicitudMovimiento.class);
        verify(movimientos).registrar(c.capture());
        assertThat(c.getValue().tipo()).isEqualTo(TipoMovimiento.LIBERACION);
        assertThat(c.getValue().usuarioId()).isNull();
        assertThat(c.getValue().cantidad()).isEqualTo(2);
        assertThat(c.getValue().referenciaTipo()).isEqualTo("RESERVA");
        assertThat(c.getValue().referenciaId()).isEqualTo(12L);
        assertThat(c.getValue().observacion()).isEqualTo("Vencimiento automático");
    }

    @Test
    void vencer_noActivaOInexistente_devuelveFalseSinMovimiento() {
        Reserva convertida = activa(12L, v5, 2);
        convertida.convertir(77L, AHORA, 3L);
        when(reservas.bloquear(12L)).thenReturn(Optional.of(convertida));
        when(reservas.bloquear(13L)).thenReturn(Optional.empty());

        assertThat(servicio.vencer(12L, AHORA.plusHours(49))).isFalse();
        assertThat(servicio.vencer(13L, AHORA.plusHours(49))).isFalse();

        assertThat(convertida.getEstado()).isEqualTo(EstadoReserva.CONVERTIDA);
        verify(movimientos, never()).registrar(any());
    }

    @Test
    void listar_porVencer_usaLimiteDeDoceHorasYPatronEscapado() {
        when(reservas.buscar(any(), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(activa(12L, v5, 2))));

        var pagina = servicio.listar(EstadoReserva.ACTIVA, "50%", 5L, true,
                PageRequest.of(0, 20, Sort.by("venceEn")));

        assertThat(pagina.getContent()).hasSize(1);
        verify(reservas).buscar(Mockito.eq(EstadoReserva.ACTIVA), Mockito.eq(5L), Mockito.eq("%50!%%"),
                Mockito.eq(AHORA.plusHours(12)), any());
    }

    @Test
    void listar_ordenNoPermitido_lanza400() {
        assertThatThrownBy(() -> servicio.listar(null, null, null, false,
                PageRequest.of(0, 20, Sort.by("clienteNombre"))))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void listar_busquedaMuyLarga_lanza400() {
        assertThatThrownBy(() -> servicio.listar(null, "x".repeat(61), null, false, PageRequest.of(0, 20)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void obtener_inexistente_lanza404() {
        when(reservas.conVariante(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(1L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void obtener_cerradaPorElSistema_cerradaPorNullYVentaNumero() {
        Reserva r = activa(12L, v5, 2);
        r.vencer(AHORA.plusHours(49));
        when(reservas.conVariante(12L)).thenReturn(Optional.of(r));

        ReservaResponse res = servicio.obtener(12L);

        assertThat(res.estado()).isEqualTo(EstadoReserva.VENCIDA);
        assertThat(res.cerradaPor()).isNull();
        assertThat(res.ventaNumero()).isNull();
        assertThat(res.precioVenta()).isEqualByComparingTo("59900");
    }
}
