package co.livorno.controlstock.reporte;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.compra.servicio.PlanCompraService;
import co.livorno.controlstock.inventario.servicio.OperacionInventarioService;
import co.livorno.controlstock.inventario.servicio.ReservaService;
import co.livorno.controlstock.reporte.DatosReporte.L;
import co.livorno.controlstock.reporte.dto.AgrupacionReporte;
import co.livorno.controlstock.reporte.dto.FilaMovimientos;
import co.livorno.controlstock.reporte.dto.FiltroMovimientos;
import co.livorno.controlstock.reporte.dto.MovimientosResponse;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte.MovimientoDetalle;
import co.livorno.controlstock.reporte.servicio.MovimientosPeriodoService;
import co.livorno.controlstock.venta.dto.VentaDetalleResponse;
import co.livorno.controlstock.venta.servicio.DevolucionService;
import co.livorno.controlstock.venta.servicio.VentaService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Movimientos del periodo contra MySQL real (B9, fase 7). Todo pasa por los servicios reales; solo la ENTRADA de M se
 * mueve 10 días al pasado para tener saldo inicial. Variante M: entrada 10 (hace 10 días), reserva 1 (no cambia el
 * físico), venta 3 con 1 devuelta apta y 1 no apta, venta 2 anulada, ajustes +2 y −1 (hoy) → físico 9. Variante S
 * (otro producto): entrada 4 hoy.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class MovimientosPeriodoServiceIntegracionTest {

    @Autowired MovimientosPeriodoService servicio;
    @Autowired ConsultasReporte consultas;
    @Autowired JdbcTemplate jdbc;
    @Autowired VentaService ventas;
    @Autowired DevolucionService devoluciones;
    @Autowired OperacionInventarioService operaciones;
    @Autowired ReservaService reservas;
    @Autowired PlanCompraService planes;
    @Autowired Clock clock;

    DatosReporte datos;
    Long cat;
    Long p1;
    Long m;
    Long s;
    LocalDate hoy;

    @BeforeEach
    void preparar() {
        hoy = LocalDate.now(clock);
        LocalDateTime ahora = LocalDateTime.now(clock);
        datos = new DatosReporte(jdbc, ventas, devoluciones, operaciones).conReservasYPlanes(reservas, planes);
        cat = datos.categoria("Movs");
        p1 = datos.producto("MV1", cat, "20000.00", "50000.00");
        m = datos.variante(p1, "M");
        s = datos.variante(datos.producto("MV2", cat, "10000.00", "30000.00"), "S");

        datos.entrada(m, 10, "20000.00");
        datos.fecharMovimientos(m, "ENTRADA", hoy.minusDays(10).atTime(10, 0));
        datos.reservar(m, 1, "Cliente Movimientos", "@cliente_movs");
        VentaDetalleResponse v1 = datos.vender(ahora, new L(m, 3, null));
        datos.devolver(v1, 0, 1, true, ahora);
        datos.devolver(v1, 0, 1, false, ahora);
        datos.anular(datos.vender(ahora, new L(m, 2, null)), ahora);
        datos.ajuste(m, true, 2);
        datos.ajuste(m, false, 1);
        datos.entrada(s, 4, "10000.00");
    }

    @AfterEach
    void limpiar() {
        datos.limpiar();
    }

    private MovimientosResponse consultar(LocalDate desde, LocalDate hasta, AgrupacionReporte agrupacion) {
        return servicio.consultar(new FiltroMovimientos(desde, hasta, cat, agrupacion));
    }

    private static FilaMovimientos fila(MovimientosResponse r, Long id) {
        return r.filas().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    private static long cuadre(FilaMovimientos f) {
        return f.saldoInicial() + f.entradas() - f.ventas() + f.anulaciones() + f.devoluciones()
                + f.ajustesPositivos() - f.ajustesNegativos();
    }

    @Test
    void hoy_saldoInicialDeLaEntradaAnteriorYFlujosDelDia() {
        MovimientosResponse r = consultar(hoy, hoy, AgrupacionReporte.VARIANTE);

        FilaMovimientos fm = fila(r, m);
        assertThat(fm.saldoInicial()).isEqualTo(10);
        assertThat(fm.entradas()).isZero();
        assertThat(fm.ventas()).isEqualTo(5);
        assertThat(fm.anulaciones()).isEqualTo(2);
        assertThat(fm.devoluciones()).as("solo la apta vuelve al inventario").isEqualTo(1);
        assertThat(fm.ajustesPositivos()).isEqualTo(2);
        assertThat(fm.ajustesNegativos()).isEqualTo(1);
        assertThat(fm.saldoFinal()).isEqualTo(9).isEqualTo(cuadre(fm));
        assertThat(jdbc.queryForObject("SELECT stock_fisico FROM variante WHERE id = ?", Integer.class, m))
                .isEqualTo(9);

        FilaMovimientos fs = fila(r, s);
        assertThat(fs.saldoInicial()).isZero();
        assertThat(fs.entradas()).isEqualTo(4);
        assertThat(fs.valorEntradas()).isEqualByComparingTo("40000.00");
        assertThat(fs.saldoFinal()).isEqualTo(4);
        // M: 2 ventas, devolución, anulación y 2 ajustes; S: entrada. La reserva no cuenta.
        assertThat(r.movimientosDetalle()).isEqualTo(7);
        assertThat(r.sinMovimientos()).isFalse();
        assertThat(r.categoria()).startsWith("Movs");
    }

    @Test
    void rangoAnterior_terminaAntesDeHoyYDescuentaLoPosterior() {
        MovimientosResponse r = consultar(hoy.minusDays(10), hoy.minusDays(1), AgrupacionReporte.VARIANTE);

        assertThat(r.filas()).extracting(FilaMovimientos::id).containsExactly(m);
        FilaMovimientos fm = r.filas().get(0);
        assertThat(fm.saldoInicial()).isZero();
        assertThat(fm.entradas()).isEqualTo(10);
        assertThat(fm.valorEntradas()).isEqualByComparingTo("200000.00");
        assertThat(fm.saldoFinal()).isEqualTo(10);
        assertThat(r.movimientosDetalle()).isEqualTo(1);
    }

    @Test
    void rangoSinMovimientosNiSaldo_vacioConMensaje() {
        MovimientosResponse r = consultar(hoy.minusDays(30), hoy.minusDays(20), AgrupacionReporte.PRODUCTO);

        assertThat(r.filas()).isEmpty();
        assertThat(r.sinMovimientos()).isTrue();
        assertThat(r.mensaje()).isEqualTo("No hay movimientos en el periodo");
        assertThat(r.totales().saldoFinal()).isZero();
    }

    @Test
    void tresAgrupaciones_mismosTotales() {
        for (AgrupacionReporte a : AgrupacionReporte.values()) {
            MovimientosResponse r = consultar(hoy.minusDays(10), hoy, a);
            FilaMovimientos t = r.totales();
            assertThat(t.saldoInicial()).as("saldo inicial %s", a).isZero();
            assertThat(t.entradas()).as("entradas %s", a).isEqualTo(14);
            assertThat(t.valorEntradas()).as("valor %s", a).isEqualByComparingTo("240000.00");
            assertThat(t.saldoFinal()).as("saldo final %s", a).isEqualTo(13).isEqualTo(cuadre(t));
        }
        assertThat(consultar(hoy.minusDays(10), hoy, AgrupacionReporte.PRODUCTO).filas()).hasSize(2);
        assertThat(consultar(hoy.minusDays(10), hoy, AgrupacionReporte.CATEGORIA).filas()).singleElement()
                .satisfies(f -> assertThat(f.id()).isEqualTo(cat));
    }

    @Test
    void detalle_ordenDelKardexSinReservasYConUsuario() {
        List<MovimientoDetalle> d = consultas.detalleMovimientos(hoy.minusDays(10), hoy, cat);

        assertThat(d).extracting(MovimientoDetalle::tipo).containsExactly("ENTRADA", "VENTA", "DEVOLUCION", "VENTA",
                "ANULACION_VENTA", "AJUSTE_POSITIVO", "AJUSTE_NEGATIVO", "ENTRADA");
        assertThat(d.get(0).costoUnitario()).isEqualByComparingTo("20000.00");
        assertThat(d.get(0).fisicoAntes()).isZero();
        assertThat(d.get(0).fisicoDespues()).isEqualTo(10);
        assertThat(d.get(0).usuario()).isEqualTo(datos.nombreAdmin);
        assertThat(d.get(1).usuario()).isEqualTo("Vendedor Reporte");
        assertThat(d.get(1).referenciaTipo()).isNotNull();
        assertThat(d.get(5).referenciaTipo()).isNull();
        assertThat(d.get(5).referenciaId()).isNull();
        assertThat(consultas.contarMovimientos(hoy.minusDays(10), hoy, cat)).isEqualTo(d.size());
    }
}
