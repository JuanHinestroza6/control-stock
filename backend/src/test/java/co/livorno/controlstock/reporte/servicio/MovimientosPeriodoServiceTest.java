package co.livorno.controlstock.reporte.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.comun.config.ConfiguracionTiempo;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.exportacion.ArchivoExportado;
import co.livorno.controlstock.comun.exportacion.DocumentoTabular;
import co.livorno.controlstock.comun.exportacion.ExportadorDocumentos;
import co.livorno.controlstock.comun.exportacion.FormatoExportacion;
import co.livorno.controlstock.comun.exportacion.SeccionTabular;
import co.livorno.controlstock.reporte.dto.AgrupacionReporte;
import co.livorno.controlstock.reporte.dto.FilaMovimientos;
import co.livorno.controlstock.reporte.dto.FiltroMovimientos;
import co.livorno.controlstock.reporte.dto.MovimientosResponse;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte.AcumuladoMovimientos;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte.MovimientoDetalle;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** B9, fase 7: valores por defecto, orden, totales, mensaje sin movimientos y límite del detalle en Excel. */
@ExtendWith(MockitoExtension.class)
class MovimientosPeriodoServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-14T15:00:00Z"), ConfiguracionTiempo.ZONA);
    private static final LocalDate DIA_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 14);

    @Mock ConsultasReporte consultas;
    @Mock ExportadorDocumentos exportador;

    MovimientosPeriodoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new MovimientosPeriodoService(consultas, new ValidadorRangoReporte(RELOJ), exportador, RELOJ, 100);
    }

    /** Producto con saldo inicial 10, entrada 5 a 20.000, venta 4, anulación 1, devolución 1, ajustes +2 −1 → 14. */
    private static AcumuladoMovimientos producto(long id, String codigo, String nombre) {
        return new AcumuladoMovimientos(id, codigo, nombre, "Camisetas", 10, 5, new BigDecimal("100000"), 4, 1, 1, 2,
                1, 14);
    }

    @Test
    void consultar_sinFiltros_usaElMesEnCursoYProductoYOrdenaPorCodigo() {
        when(consultas.movimientos(DIA_1, HOY, null, AgrupacionReporte.PRODUCTO))
                .thenReturn(List.of(producto(2, "LIV002", "Jogger"), producto(1, "LIV001", "Camiseta")));
        when(consultas.contarMovimientos(DIA_1, HOY, null)).thenReturn(28L);

        MovimientosResponse r = servicio.consultar(new FiltroMovimientos(null, null, null, null));

        assertThat(r.desde()).isEqualTo(DIA_1);
        assertThat(r.hasta()).isEqualTo(HOY);
        assertThat(r.agrupacion()).isEqualTo(AgrupacionReporte.PRODUCTO);
        assertThat(r.filas()).extracting(FilaMovimientos::codigo).containsExactly("LIV001", "LIV002");
        assertThat(r.filas().get(0).valorEntradas().scale()).isEqualTo(2);
        FilaMovimientos t = r.totales();
        assertThat(t.nombre()).isEqualTo("Total");
        assertThat(t.saldoInicial()).isEqualTo(20);
        assertThat(t.entradas()).isEqualTo(10);
        assertThat(t.valorEntradas()).isEqualByComparingTo("200000.00");
        assertThat(t.ventas()).isEqualTo(8);
        assertThat(t.saldoFinal()).isEqualTo(28);
        // el saldo cuadra con los flujos
        assertThat(t.saldoInicial() + t.entradas() - t.ventas() + t.anulaciones() + t.devoluciones()
                + t.ajustesPositivos() - t.ajustesNegativos()).isEqualTo(t.saldoFinal());
        assertThat(r.movimientosDetalle()).isEqualTo(28);
        assertThat(r.sinMovimientos()).isFalse();
        assertThat(r.mensaje()).isNull();
        assertThat(r.notas()).anyMatch(n -> n.contains("reservas y liberaciones"));
        assertThat(r.generadoEn()).isEqualTo(LocalDateTime.now(RELOJ));
    }

    @Test
    void consultar_sinMovimientos_devuelveElMensajeYConservaLosSaldos() {
        LocalDate desde = LocalDate.of(2026, 9, 1);
        LocalDate hasta = LocalDate.of(2026, 9, 30);
        when(consultas.nombreCategoria(3L)).thenReturn("Jeans");
        when(consultas.movimientos(desde, hasta, 3L, AgrupacionReporte.CATEGORIA)).thenReturn(List.of(
                new AcumuladoMovimientos(3L, null, "Jeans", null, 7, 0, BigDecimal.ZERO, 0, 0, 0, 0, 0, 7)));
        when(consultas.contarMovimientos(desde, hasta, 3L)).thenReturn(0L);

        MovimientosResponse r = servicio.consultar(new FiltroMovimientos(desde, hasta, 3L, AgrupacionReporte.CATEGORIA));

        assertThat(r.sinMovimientos()).isTrue();
        assertThat(r.mensaje()).isEqualTo(MovimientosPeriodoService.MENSAJE_SIN_MOVIMIENTOS);
        assertThat(r.categoria()).isEqualTo("Jeans");
        assertThat(r.filas()).singleElement().satisfies(f -> {
            assertThat(f.saldoInicial()).isEqualTo(7);
            assertThat(f.saldoFinal()).isEqualTo(7);
        });
    }

    @Test
    void consultar_rangoFuturo_lanza400SinConsultar() {
        assertThatThrownBy(() -> servicio.consultar(new FiltroMovimientos(null, HOY.plusDays(1), null, null)))
                .isInstanceOf(co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException.class);
        verify(consultas, never()).movimientos(any(), any(), any(), any());
    }

    @Test
    void exportarXlsx_llevaElDetalleConTipoYReferenciaLegibles() {
        when(consultas.movimientos(DIA_1, HOY, null, AgrupacionReporte.VARIANTE))
                .thenReturn(List.of(producto(9, "LIV001-M-NEG", "Camiseta (M NEGRO)")));
        when(consultas.contarMovimientos(DIA_1, HOY, null)).thenReturn(2L);
        when(consultas.detalleMovimientos(DIA_1, HOY, null)).thenReturn(List.of(
                new MovimientoDetalle(LocalDateTime.of(2026, 10, 2, 9, 0), "LIV001-M-NEG", "Camiseta", "ENTRADA", 5,
                        10, 15, new BigDecimal("20000.00"), "PLAN_COMPRA", 3674L, "Ana Admin"),
                new MovimientoDetalle(LocalDateTime.of(2026, 10, 3, 9, 0), "LIV001-M-NEG", "Camiseta", "VENTA_RESERVA",
                        1, 15, 14, null, null, null, "Vera Vendedora")));
        when(exportador.exportar(any(), eq(FormatoExportacion.XLSX), eq("movimientos_2026-10-01_2026-10-14")))
                .thenReturn(new ArchivoExportado(new byte[] {1}, ExportadorDocumentos.TIPO_XLSX, "m.xlsx"));

        servicio.exportar(new FiltroMovimientos(null, null, null, AgrupacionReporte.VARIANTE), FormatoExportacion.XLSX,
                "Ana Admin");

        ArgumentCaptor<DocumentoTabular> doc = ArgumentCaptor.forClass(DocumentoTabular.class);
        verify(exportador).exportar(doc.capture(), eq(FormatoExportacion.XLSX), any());
        assertThat(doc.getValue().titulo()).isEqualTo("Movimientos del periodo");
        SeccionTabular detalle = doc.getValue().secciones().get(1);
        assertThat(detalle.filas()).hasSize(2);
        assertThat(detalle.filas().get(0)).contains("Entrada", "PLAN_COMPRA/3674");
        assertThat(detalle.filas().get(1)).contains("Venta de apartado").containsNull();
        // resumen por variante: SKU, variante, categoría y 9 cifras
        assertThat(doc.getValue().secciones().get(0).columnas()).hasSize(12);
    }

    @Test
    void exportarPdf_noConsultaElDetalleNiAplicaElLimite() {
        when(consultas.movimientos(DIA_1, HOY, null, AgrupacionReporte.PRODUCTO)).thenReturn(List.of());
        when(consultas.contarMovimientos(DIA_1, HOY, null)).thenReturn(500L);
        when(exportador.exportar(any(), eq(FormatoExportacion.PDF), any()))
                .thenReturn(new ArchivoExportado(new byte[] {1}, ExportadorDocumentos.TIPO_PDF, "m.pdf"));

        servicio.exportar(new FiltroMovimientos(null, null, null, null), FormatoExportacion.PDF, "Ana Admin");

        verify(consultas, never()).detalleMovimientos(any(), any(), any());
    }

    @Test
    void exportarXlsx_sobreElLimite_lanza422SinGenerar() {
        when(consultas.movimientos(DIA_1, HOY, null, AgrupacionReporte.PRODUCTO)).thenReturn(List.of());
        when(consultas.contarMovimientos(DIA_1, HOY, null)).thenReturn(101L);

        assertThatThrownBy(() -> servicio.exportar(new FiltroMovimientos(null, null, null, null),
                FormatoExportacion.XLSX, "Ana Admin"))
                .isInstanceOf(ExcepcionNegocio.class)
                .hasMessageContaining("101 movimientos")
                .hasMessageContaining("Acote el rango");
        verify(exportador, never()).exportar(any(), any(), any());
    }
}
