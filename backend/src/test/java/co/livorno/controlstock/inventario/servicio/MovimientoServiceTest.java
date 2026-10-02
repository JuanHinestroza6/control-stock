package co.livorno.controlstock.inventario.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.evento.StockCambiado;
import co.livorno.controlstock.inventario.modelo.Movimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.repositorio.MovimientoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MovimientoServiceTest {

    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock VarianteRepository variantes;
    @Mock MovimientoRepository movimientos;
    @Mock ApplicationEventPublisher eventos;

    MovimientoService servicio;
    Variante variante;

    @BeforeEach
    void preparar() {
        servicio = new MovimientoService(variantes, movimientos, eventos, RELOJ);
        Producto p = new Producto("LIV001", "Camiseta", new Categoria("Camisetas"), new BigDecimal("20000"),
                new BigDecimal("59900"), null, null);
        variante = new Variante(p, "LIV001-M-NEG", Talla.M, "NEGRO", 1);
        ReflectionTestUtils.setField(variante, "id", 5L);
        variante.aplicarMovimiento(10, 4); // físico 10, reservado 4, disponible 6
    }

    private void existeVariante() {
        when(variantes.findById(5L)).thenReturn(Optional.of(variante));
    }

    private SolicitudMovimiento sol(TipoMovimiento tipo, int q) {
        BigDecimal costo = tipo == TipoMovimiento.ENTRADA ? new BigDecimal("15000") : null;
        String obs = tipo.name().startsWith("AJUSTE") ? "conteo" : null;
        return new SolicitudMovimiento(5L, tipo, q, costo, 9L, null, null, obs);
    }

    private void verificar(TipoMovimiento tipo, int q, int fisico, int reservado) {
        existeVariante();
        // cuando
        Movimiento m = servicio.registrar(sol(tipo, q));
        // entonces: snapshot antes/después, stock de la variante, kardex y evento
        assertThat(m.getFisicoAntes()).isEqualTo(10);
        assertThat(m.getReservadoAntes()).isEqualTo(4);
        assertThat(m.getFisicoDespues()).isEqualTo(fisico);
        assertThat(m.getReservadoDespues()).isEqualTo(reservado);
        assertThat(variante.getStockFisico()).isEqualTo(fisico);
        assertThat(variante.getStockReservado()).isEqualTo(reservado);
        assertThat(m.getFecha()).isEqualTo(LocalDateTime.of(2026, 10, 2, 10, 0));
        verify(movimientos).save(m);
        verify(eventos, times(1)).publishEvent(new StockCambiado(5L));
    }

    @Test
    void registrar_entrada_sumaFisico() { verificar(TipoMovimiento.ENTRADA, 3, 13, 4); }

    @Test
    void registrar_venta_restaFisico() { verificar(TipoMovimiento.VENTA, 3, 7, 4); }

    @Test
    void registrar_ventaReserva_restaFisicoYReservado() { verificar(TipoMovimiento.VENTA_RESERVA, 3, 7, 1); }

    @Test
    void registrar_devolucion_sumaFisico() { verificar(TipoMovimiento.DEVOLUCION, 3, 13, 4); }

    @Test
    void registrar_ajustePositivo_sumaFisico() { verificar(TipoMovimiento.AJUSTE_POSITIVO, 3, 13, 4); }

    @Test
    void registrar_ajusteNegativo_restaFisico() { verificar(TipoMovimiento.AJUSTE_NEGATIVO, 3, 7, 4); }

    @Test
    void registrar_reserva_sumaReservado() { verificar(TipoMovimiento.RESERVA, 3, 10, 7); }

    @Test
    void registrar_liberacion_restaReservado() { verificar(TipoMovimiento.LIBERACION, 3, 10, 1); }

    @Test
    void registrar_anulacionVenta_sumaFisico() { verificar(TipoMovimiento.ANULACION_VENTA, 3, 13, 4); }

    @Test
    void registrar_ventaReservaYAjusteNegativoMasQueDisponible_lanza422SinGuardar() {
        existeVariante();
        for (TipoMovimiento t : new TipoMovimiento[] {TipoMovimiento.VENTA, TipoMovimiento.RESERVA,
                TipoMovimiento.AJUSTE_NEGATIVO}) {
            assertThatThrownBy(() -> servicio.registrar(sol(t, 7))).as(t.name())
                    .isInstanceOf(ExcepcionNegocio.class)
                    .hasMessage("Stock insuficiente para LIV001-M-NEG: disponible 6, solicitado 7.");
        }
        verifyNadaGuardado();
    }

    @Test
    void registrar_liberacionYVentaReservaMasQueReservado_lanza422SinGuardar() {
        existeVariante();
        assertThatThrownBy(() -> servicio.registrar(sol(TipoMovimiento.LIBERACION, 5)))
                .isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> servicio.registrar(sol(TipoMovimiento.VENTA_RESERVA, 5)))
                .isInstanceOf(ExcepcionNegocio.class);
        verifyNadaGuardado();
    }

    @Test
    void registrar_ajusteSinObservacion_lanza422() {
        existeVariante();
        var s = new SolicitudMovimiento(5L, TipoMovimiento.AJUSTE_POSITIVO, 1, null, 9L, null, null, "  ");
        assertThatThrownBy(() -> servicio.registrar(s)).isInstanceOf(ExcepcionNegocio.class);
        verifyNadaGuardado();
    }

    @Test
    void registrar_entradaSinCostoOCostoEnOtroTipo_lanza422() {
        existeVariante();
        var sinCosto = new SolicitudMovimiento(5L, TipoMovimiento.ENTRADA, 1, null, 9L, null, null, null);
        var conCosto = new SolicitudMovimiento(5L, TipoMovimiento.VENTA, 1, BigDecimal.ONE, 9L, null, null, null);
        assertThatThrownBy(() -> servicio.registrar(sinCosto)).isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> servicio.registrar(conCosto)).isInstanceOf(ExcepcionNegocio.class);
        verifyNadaGuardado();
    }

    @Test
    void registrar_cantidadNoPositiva_lanza422() {
        assertThatThrownBy(() -> servicio.registrar(sol(TipoMovimiento.VENTA, 0)))
                .isInstanceOf(ExcepcionNegocio.class);
        verifyNadaGuardado();
    }

    @Test
    void registrar_varianteInexistente_lanza404() {
        var s = new SolicitudMovimiento(99L, TipoMovimiento.VENTA, 1, null, 9L, null, null, null);
        when(variantes.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.registrar(s)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void registrar_usuarioNuloYReferencia_seAceptaYSeGuarda() {
        existeVariante();
        var s = new SolicitudMovimiento(5L, TipoMovimiento.LIBERACION, 1, null, null, "RESERVA", 7L, null);
        Movimiento m = servicio.registrar(s);
        assertThat(m.getUsuarioId()).isNull();
        assertThat(m.getReferenciaTipo()).isEqualTo("RESERVA");
        assertThat(m.getReferenciaId()).isEqualTo(7L);
    }

    private void verifyNadaGuardado() {
        verify(movimientos, never()).save(any());
        verify(eventos, never()).publishEvent(any(Object.class));
        assertThat(variante.getStockFisico()).isEqualTo(10);
        assertThat(variante.getStockReservado()).isEqualTo(4);
    }
}
