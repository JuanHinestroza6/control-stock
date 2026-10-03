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
import co.livorno.controlstock.inventario.modelo.Alerta;
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

    AlertaService servicio;

    @BeforeEach
    void preparar() {
        servicio = new AlertaService(variantes, alertas, RELOJ, usuarios);
    }

    /** Variante con stock mínimo 5, físico y reservado dados. */
    private Variante variante(int fisico, int reservado) {
        Variante v = new Variante(null, "LIV001-M-NEG", Talla.M, "NEGRO", 5);
        v.aplicarMovimiento(fisico, reservado);
        when(variantes.findById(ID)).thenReturn(Optional.of(v));
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

        verify(alertas).cerrarActiva(ID, TipoAlerta.STOCK_BAJO, AHORA);
        verify(alertas, never()).abrirOActualizar(any(), any(), any());
    }

    @Test
    void evaluar_varianteInexistente_noHaceNada() {
        when(variantes.findById(ID)).thenReturn(Optional.empty());

        servicio.evaluar(ID);

        verifyNoInteractions(alertas);
    }

    @Test
    void tipoVigente_sinUmbralYSinStock_esAgotado() {
        Variante v = new Variante(null, "X", Talla.M, "NEGRO", 0);

        assertThat(AlertaService.tipoVigente(v)).isEqualTo(TipoAlerta.AGOTADO);
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
                new Object[] {TipoAlerta.AGOTADO, 2L}, new Object[] {TipoAlerta.STOCK_BAJO, 5L}));

        var r = servicio.resumen();

        assertThat(r.activas()).isEqualTo(7);
        assertThat(r.agotado()).isEqualTo(2);
        assertThat(r.stockBajo()).isEqualTo(5);
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
}
