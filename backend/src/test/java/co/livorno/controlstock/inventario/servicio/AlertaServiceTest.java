package co.livorno.controlstock.inventario.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.catalogo.evento.UmbralCambiado;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.inventario.evento.StockCambiado;
import co.livorno.controlstock.inventario.dto.AlertaResponse;
import co.livorno.controlstock.inventario.modelo.Alerta;
import co.livorno.controlstock.inventario.modelo.EstadoAlerta;
import co.livorno.controlstock.inventario.modelo.TipoAlerta;
import co.livorno.controlstock.inventario.repositorio.AlertaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertaServiceTest {

    private static final Long ID = 7L;
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"),
            co.livorno.controlstock.comun.config.ConfiguracionTiempo.ZONA);
    private static final LocalDateTime AHORA = LocalDateTime.now(RELOJ);

    @Mock VarianteRepository variantes;
    @Mock AlertaRepository alertas;
    @Mock co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarios;
    @Mock co.livorno.controlstock.inventario.repositorio.MovimientoRepository movimientos;

    @Mock co.livorno.controlstock.inventario.servicio.ConsultaPedidosEnCamino pedidos;

    AlertaService servicio;

    @BeforeEach
    void preparar() {
        servicio = new AlertaService(variantes, alertas, RELOJ, usuarios, movimientos, pedidos, 60);
    }

    /** Producto creado ayer (menos de 60 días: nunca SIN_ROTACION). */
    private static co.livorno.controlstock.catalogo.modelo.Producto producto() {
        var p = new co.livorno.controlstock.catalogo.modelo.Producto("LIV001", "Camiseta", null,
                java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "creadoEn", AHORA.minusDays(1));
        return p;
    }

    /** Variante con stock mínimo 5, físico y reservado dados. */
    private Variante variante(int fisico, int reservado) {
        Variante v = new Variante(producto(), "LIV001-M-NEG", Talla.M, "NEGRO", 5);
        v.aplicarMovimiento(fisico, reservado);
        when(variantes.bloquearPorId(ID)).thenReturn(Optional.of(v));
        return v;
    }

    @Test
    void evaluar_stockOk_cierraAmbasYNoAbreNinguna() {
        variante(20, 0);

        servicio.evaluar(ID);

        verify(alertas).cerrarActiva(ID, TipoAlerta.AGOTADO, AHORA);
        verify(alertas).cerrarActiva(ID, TipoAlerta.STOCK_BAJO, AHORA);
        verify(alertas, never()).abrirOActualizar(any(), any(), any());
    }

    @Test
    void evaluar_disponibleIgualAlUmbral_abreStockBajoYCierraAgotado() {
        variante(8, 3); // disponible 5 = stockMinimo

        servicio.evaluar(ID);

        verify(alertas).cerrarActiva(ID, TipoAlerta.AGOTADO, AHORA);
        verify(alertas, never()).cerrarActiva(ID, TipoAlerta.STOCK_BAJO, AHORA);
        verify(alertas).abrirOActualizar(ID, "STOCK_BAJO", AHORA);
    }

    @Test
    void evaluar_posicionSobreElUmbral_cierraStockBajoYNoAbreNinguna() {
        variante(8, 3); // disponible 5 = stockMinimo, pero hay 4 en camino: posición 9 > 5
        when(pedidos.enCamino(ID)).thenReturn(4L);

        servicio.evaluar(ID);

        verify(alertas).cerrarActiva(ID, TipoAlerta.STOCK_BAJO, AHORA);
        verify(alertas, never()).abrirOActualizar(any(), any(), any());
    }

    @Test
    void evaluar_posicionIgualAlUmbral_mantieneStockBajo() {
        variante(5, 2); // disponible 3, 2 en camino: posición 5 = stockMinimo
        when(pedidos.enCamino(ID)).thenReturn(2L);

        servicio.evaluar(ID);

        verify(alertas).abrirOActualizar(ID, "STOCK_BAJO", AHORA);
    }

    @Test
    void evaluar_agotadoConPedidoEnCamino_sigueAgotado() {
        variante(4, 4);
        when(pedidos.enCamino(ID)).thenReturn(100L);

        servicio.evaluar(ID);

        verify(alertas).abrirOActualizar(ID, "AGOTADO", AHORA);
        verify(alertas, never()).abrirOActualizar(ID, "STOCK_BAJO", AHORA);
    }

    @Test
    void evaluar_conEnCaminoConocido_noConsultaElPuerto() {
        variante(8, 3);

        servicio.evaluar(ID, 4L);

        verify(pedidos, never()).enCamino(any());
        verify(alertas, never()).abrirOActualizar(any(), any(), any());
    }

    @Test
    void tipoStock_conEnCamino_usaLaPosicionSoloParaStockBajo() {
        Variante v = new Variante(producto(), "LIV001-M-NEG", Talla.M, "NEGRO", 5);
        v.aplicarMovimiento(3, 0);
        assertThat(AlertaService.tipoStock(v, 0)).isEqualTo(TipoAlerta.STOCK_BAJO);
        assertThat(AlertaService.tipoStock(v, 3)).isNull();
        Variante sin = new Variante(producto(), "LIV001-M-AZU", Talla.M, "AZUL", 5);
        assertThat(AlertaService.tipoStock(sin, 50)).isEqualTo(TipoAlerta.AGOTADO);
    }

    @Test
    void listener_posicionCambiada_evaluaYElFalloNoSePropaga() {
        AlertaService roto = org.mockito.Mockito.mock(AlertaService.class);
        doThrow(new IllegalStateException("fallo simulado")).when(roto).evaluar(ID);
        AlertaListener listener = new AlertaListener(roto);

        listener.alCambiarPosicion(new co.livorno.controlstock.inventario.evento.PosicionCambiada(ID));

        verify(roto).evaluar(ID);
    }

    @Test
    void evaluar_disponibleCero_abreAgotadoYCierraStockBajo() {
        variante(4, 4); // físico > 0 pero todo reservado

        servicio.evaluar(ID);

        verify(alertas).cerrarActiva(ID, TipoAlerta.STOCK_BAJO, AHORA);
        verify(alertas, never()).cerrarActiva(ID, TipoAlerta.AGOTADO, AHORA);
        verify(alertas).abrirOActualizar(ID, "AGOTADO", AHORA);
        verify(alertas, never()).abrirOActualizar(eq(ID), eq("STOCK_BAJO"), any());
    }

    @Test
    void evaluar_puntoDeReordenTienePrioridadSobreElStockMinimo() {
        Variante v = variante(10, 0);
        org.springframework.test.util.ReflectionTestUtils.setField(v, "puntoReorden", 12);

        servicio.evaluar(ID);

        verify(alertas).abrirOActualizar(ID, "STOCK_BAJO", AHORA);
    }

    @Test
    void evaluar_varianteInactiva_noAbreAlertaPeroCierraLasVigentes() {
        Variante v = variante(0, 0);
        v.desactivar();

        servicio.evaluar(ID);

        for (TipoAlerta tipo : TipoAlerta.values()) {
            verify(alertas).cerrarActiva(ID, tipo, AHORA);
        }
        verify(alertas, never()).abrirOActualizar(any(), any(), any());
    }

    @Test
    void evaluar_productoInactivo_cierraTodasLasAlertasYNoAbreNinguna() {
        Variante v = variante(0, 0);
        v.getProducto().desactivar();

        servicio.evaluar(ID);

        for (TipoAlerta tipo : TipoAlerta.values()) {
            verify(alertas).cerrarActiva(ID, tipo, AHORA);
        }
        verify(alertas, never()).abrirOActualizar(any(), any(), any());
    }

    @Test
    void evaluar_ventaReciente_cierraSinRotacionDeTodoElProducto() {
        Variante v = variante(20, 0);
        when(movimientos.ultimaVentaNetaDesde(any(), any())).thenReturn(AHORA.minusDays(3));
        when(alertas.idsSinRotacionActivas(v.getProducto().getId())).thenReturn(java.util.List.of(5L, 9L));

        servicio.evaluar(ID);

        verify(alertas).cerrarPorIds(java.util.List.of(5L, 9L), AHORA);
    }

    @Test
    void evaluar_ventaRecienteSinSinRotacionActivas_noEjecutaNingunUpdateSobreElProducto() {
        Variante v = variante(20, 0);
        when(movimientos.ultimaVentaNetaDesde(any(), any())).thenReturn(AHORA.minusDays(3));
        when(alertas.idsSinRotacionActivas(v.getProducto().getId())).thenReturn(java.util.List.of());

        servicio.evaluar(ID);

        verify(alertas, never()).cerrarPorIds(any(), any());
    }

    @Test
    void evaluar_ventaConfirmadaTrasLaPrimeraLectura_corrigeLaSinRotacionRecienAbierta() {
        // dado: la primera lectura no ve venta (producto viejo), la relectura tras abrir sí la ve
        Variante v = variante(8, 3);
        org.springframework.test.util.ReflectionTestUtils.setField(v.getProducto(), "creadoEn", AHORA.minusDays(90));
        when(movimientos.ultimaVentaNetaDesde(any(), any())).thenReturn(null, AHORA.minusDays(1));
        when(alertas.idsSinRotacionActivas(v.getProducto().getId())).thenReturn(java.util.List.of(7L));

        servicio.evaluar(ID);

        // entonces: se abrió y se cerró la SIN_ROTACION, y el tipo final es STOCK_BAJO
        var orden = org.mockito.Mockito.inOrder(alertas);
        orden.verify(alertas).abrirOActualizar(ID, "SIN_ROTACION", AHORA);
        orden.verify(alertas).cerrarPorIds(java.util.List.of(7L), AHORA);
        orden.verify(alertas).cerrarActiva(ID, TipoAlerta.SIN_ROTACION, AHORA);
        orden.verify(alertas).abrirOActualizar(ID, "STOCK_BAJO", AHORA);
    }

    @Test
    void evaluar_productoViejoSinVentas_abreSinRotacionYGanaSobreStockBajo() {
        Variante v = variante(8, 3); // disponible 5 = stockMinimo: sería STOCK_BAJO
        org.springframework.test.util.ReflectionTestUtils.setField(v.getProducto(), "creadoEn", AHORA.minusDays(90));

        servicio.evaluar(ID);

        verify(alertas).abrirOActualizar(ID, "SIN_ROTACION", AHORA);
        verify(alertas).cerrarActiva(ID, TipoAlerta.STOCK_BAJO, AHORA);
    }

    @Test
    void evaluar_productoViejoSinVentasYAgotado_agotadoGana() {
        Variante v = variante(0, 0);
        org.springframework.test.util.ReflectionTestUtils.setField(v.getProducto(), "creadoEn", AHORA.minusDays(90));

        servicio.evaluar(ID);

        verify(alertas).abrirOActualizar(ID, "AGOTADO", AHORA);
        verify(alertas, never()).abrirOActualizar(eq(ID), eq("SIN_ROTACION"), any());
    }

    @Test
    void evaluar_sinRotacionAtendidaAManoEnElEpisodio_noSeReabre() {
        Variante v = variante(20, 0);
        org.springframework.test.util.ReflectionTestUtils.setField(v.getProducto(), "creadoEn", AHORA.minusDays(90));
        when(alertas.existeSinRotacionAtendidaAMano(any(), any())).thenReturn(true);

        servicio.evaluar(ID);

        verify(alertas, never()).abrirOActualizar(any(), any(), any());
        // el inicio del episodio es creadoEn + 60 días cuando no hay ventas
        verify(alertas).existeSinRotacionAtendidaAMano(v.getId(), AHORA.minusDays(30));
    }

    @Test
    void evaluar_varianteInexistente_noHaceNada() {
        when(variantes.bloquearPorId(ID)).thenReturn(Optional.empty());

        servicio.evaluar(ID);

        verifyNoInteractions(alertas);
    }

    @Test
    void tipoStock_sinUmbralYSinStock_esAgotado() {
        Variante v = new Variante(null, "X", Talla.M, "NEGRO", 0);

        assertThat(AlertaService.tipoStock(v)).isEqualTo(TipoAlerta.AGOTADO);
    }

    @Test
    void alerta_atenderYaAtendida_lanzaConflicto() {
        Alerta a = new Alerta(null, TipoAlerta.AGOTADO, AHORA);

        a.atender(3L, AHORA);

        assertThat(a.getAtendidaPor()).isEqualTo(3L);
        assertThat(a.getAtendidaEn()).isEqualTo(AHORA);
        assertThatThrownBy(() -> a.atender(3L, AHORA)).isInstanceOf(ConflictoException.class)
                .hasMessage("La alerta ya fue atendida.");
    }

    @Test
    void atender_activa_cierraConUsuarioYFecha() {
        Variante v = new Variante(new co.livorno.controlstock.catalogo.modelo.Producto("LIV001", "Camiseta",
                null, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, null, null), "X", Talla.M, "NEGRO", 5);
        Alerta a = new Alerta(v, TipoAlerta.AGOTADO, AHORA.minusHours(1));
        when(alertas.bloquear(4L)).thenReturn(Optional.of(a));

        var r = servicio.atender(4L, 3L);

        assertThat(r.estado()).isEqualTo(co.livorno.controlstock.inventario.modelo.EstadoAlerta.ATENDIDA);
        assertThat(a.getAtendidaPor()).isEqualTo(3L);
        assertThat(a.getAtendidaEn()).isEqualTo(AHORA);
    }

    @Test
    void atender_inexistente_lanza404() {
        when(alertas.bloquear(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.atender(4L, 3L))
                .isInstanceOf(co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException.class);
    }

    @Test
    void resumen_sumaActivasYSeparaPorTipo() {
        when(alertas.contarActivasPorTipo()).thenReturn(java.util.List.of(
                new Object[] {TipoAlerta.AGOTADO, 2L}, new Object[] {TipoAlerta.STOCK_BAJO, 5L},
                new Object[] {TipoAlerta.SIN_ROTACION, 3L}));

        var r = servicio.resumen();

        assertThat(r.activas()).isEqualTo(10);
        assertThat(r.sinRotacion()).isEqualTo(3);
        assertThat(r.agotado()).isEqualTo(2);
        assertThat(r.stockBajo()).isEqualTo(5);
    }

    // --- urgentes (bloque del panel) ---

    /** Alerta ACTIVA con id, antigüedad en días y variante con disponible y umbral (stock mínimo) dados. */
    private static Alerta alertaUrgente(long id, TipoAlerta tipo, int disponible, int umbral, int diasAntiguedad) {
        Variante v = new Variante(producto(), "LIV001-" + id, Talla.M, "NEGRO", umbral);
        v.aplicarMovimiento(disponible, 0);
        org.springframework.test.util.ReflectionTestUtils.setField(v, "id", 100 + id);
        Alerta a = new Alerta(v, tipo, AHORA.minusDays(diasAntiguedad));
        org.springframework.test.util.ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Test
    void urgentes_stockBajoOrdenaPorPosicionConLoEnCamino() {
        // 9: disponible 1 + 8 en camino = 9 de 10 (90 %); 10: disponible 3 sin pedido = 3 de 10 (30 %)
        simularActivas(java.util.List.of(), java.util.List.of(alertaUrgente(9, TipoAlerta.STOCK_BAJO, 1, 10, 5),
                alertaUrgente(10, TipoAlerta.STOCK_BAJO, 3, 10, 1)));
        when(pedidos.enCaminoPorVariante()).thenReturn(java.util.Map.of(109L, 8L));

        assertThat(servicio.urgentes(5)).extracting(AlertaResponse::id).containsExactly(10L, 9L);
    }

    private void simularActivas(java.util.List<Alerta> agotadas, java.util.List<Alerta> bajas) {
        when(alertas.buscar(eq(EstadoAlerta.ACTIVA), eq(TipoAlerta.AGOTADO), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(agotadas));
        when(alertas.buscar(eq(EstadoAlerta.ACTIVA), eq(TipoAlerta.STOCK_BAJO), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(bajas));
    }

    @Test
    void urgentes_agotadasPorUmbralYAntiguedadYLuegoBajasPorCobertura() {
        simularActivas(
                java.util.List.of(alertaUrgente(6, TipoAlerta.AGOTADO, 0, 0, 40), // sin demanda: última agotada
                        alertaUrgente(1, TipoAlerta.AGOTADO, 0, 5, 1),
                        alertaUrgente(2, TipoAlerta.AGOTADO, 0, 5, 10)),
                java.util.List.of(alertaUrgente(3, TipoAlerta.STOCK_BAJO, 4, 5, 30), // cobertura 80 %
                        alertaUrgente(4, TipoAlerta.STOCK_BAJO, 1, 10, 2), // 10 %
                        alertaUrgente(5, TipoAlerta.STOCK_BAJO, 2, 4, 5))); // 50 %

        var r = servicio.urgentes(10);

        assertThat(r).extracting(AlertaResponse::id).containsExactly(2L, 1L, 6L, 4L, 5L, 3L);
        assertThat(servicio.urgentes(null)).hasSize(5);
        assertThat(r).allSatisfy(a -> assertThat(a.atendidaPor()).isNull());
    }

    @Test
    void urgentes_respetaElLimiteYEmpataPorAntiguedad() {
        simularActivas(java.util.List.of(),
                java.util.List.of(alertaUrgente(7, TipoAlerta.STOCK_BAJO, 1, 2, 1),
                        alertaUrgente(8, TipoAlerta.STOCK_BAJO, 2, 4, 9)));

        assertThat(servicio.urgentes(1)).extracting(AlertaResponse::id).containsExactly(8L);
    }

    @Test
    void urgentes_limiteFueraDeRango_lanza400() {
        assertThatThrownBy(() -> servicio.urgentes(0))
                .isInstanceOf(co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.urgentes(21))
                .isInstanceOf(co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException.class);
        verifyNoInteractions(alertas);
    }

    @Test
    void listener_falloDeAlertas_noSePropaga() {
        AlertaService roto = org.mockito.Mockito.mock(AlertaService.class);
        doThrow(new IllegalStateException("fallo simulado")).when(roto).evaluar(ID);
        AlertaListener listener = new AlertaListener(roto);

        // no debe lanzar: la operación que originó el evento ya está confirmada
        listener.alCambiarStock(new StockCambiado(ID));
        listener.alCambiarUmbral(new UmbralCambiado(ID));

        verify(roto, org.mockito.Mockito.times(2)).evaluar(ID);
        verifyNoMoreInteractions(roto);
    }

    @Test
    void evaluar_sinRotacionConPedidoEnCamino_sigueSinRotacion() {
        // dado: producto viejo sin ventas, disponible 8 y 100 en camino (posicion muy sobre el umbral)
        Variante v = variante(8, 0);
        org.springframework.test.util.ReflectionTestUtils.setField(v.getProducto(), "creadoEn", AHORA.minusDays(90));

        servicio.evaluar(ID, 100L);

        // entonces: SIN_ROTACION no depende de lo que viene en camino
        verify(alertas).abrirOActualizar(ID, "SIN_ROTACION", AHORA);
    }

    @Test
    void evaluar_conPuntoDeReorden_laPosicionSeComparaContraElRop() {
        Variante v = variante(10, 0);
        org.springframework.test.util.ReflectionTestUtils.setField(v, "puntoReorden", 12);
        when(pedidos.enCamino(ID)).thenReturn(2L); // posicion 12 = ROP (stockMinimo 5 no cuenta)

        servicio.evaluar(ID);

        verify(alertas).abrirOActualizar(ID, "STOCK_BAJO", AHORA);
    }

    @Test
    void evaluar_conPuntoDeReordenYPosicionSobreElRop_noAbreStockBajo() {
        Variante v = variante(10, 0);
        org.springframework.test.util.ReflectionTestUtils.setField(v, "puntoReorden", 12);

        servicio.evaluar(ID, 3L); // posicion 13 > 12

        verify(alertas, never()).abrirOActualizar(any(), any(), any());
    }

    @Test
    void evaluar_varianteInactivaConPedidoEnCamino_noAbreAlertaNiConsultaElPuerto() {
        Variante v = variante(0, 0);
        v.desactivar();

        servicio.evaluar(ID, 50L);

        verify(alertas, never()).abrirOActualizar(any(), any(), any());
        verify(pedidos, never()).enCamino(any());
    }
}
