package co.livorno.controlstock.reporte;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.compra.servicio.PlanCompraService;
import co.livorno.controlstock.comun.exportacion.ArchivoExportado;
import co.livorno.controlstock.comun.exportacion.FormatoExportacion;
import co.livorno.controlstock.inventario.servicio.OperacionInventarioService;
import co.livorno.controlstock.inventario.servicio.ReservaService;
import co.livorno.controlstock.reporte.dto.AgrupacionReporte;
import co.livorno.controlstock.reporte.dto.FilaValorizacion;
import co.livorno.controlstock.reporte.dto.FiltroValorizacion;
import co.livorno.controlstock.reporte.dto.ValorizacionResponse;
import co.livorno.controlstock.reporte.servicio.ValorizacionService;
import co.livorno.controlstock.venta.servicio.DevolucionService;
import co.livorno.controlstock.venta.servicio.VentaService;
import java.math.BigDecimal;
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
 * Valorización contra MySQL real (B5, fase 7). Entradas, reservas y planes de compra pasan por los servicios reales;
 * cada prueba filtra por su propia categoría.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class ValorizacionServiceIntegracionTest {

    @Autowired ValorizacionService servicio;
    @Autowired JdbcTemplate jdbc;
    @Autowired VentaService ventas;
    @Autowired DevolucionService devoluciones;
    @Autowired OperacionInventarioService operaciones;
    @Autowired ReservaService reservas;
    @Autowired PlanCompraService planes;

    DatosReporte datos;
    Long cat;
    Long p1;
    Long m;
    Long l;
    Long p2;
    Long s;

    @BeforeEach
    void preparar() {
        datos = new DatosReporte(jdbc, ventas, devoluciones, operaciones).conReservasYPlanes(reservas, planes);
        cat = datos.categoria("Valor");
        p1 = datos.producto("VL1", cat, "20000.00", "50000.00");
        m = datos.variante(p1, "M");
        l = datos.variante(p1, "L");
        p2 = datos.producto("VL2", cat, "10000.00", "30000.00");
        s = datos.variante(p2, "S");
        datos.entrada(m, 10, "20000.00");
        datos.entrada(l, 5, "20000.00");
        datos.entrada(s, 4, "10000.00");
    }

    @AfterEach
    void limpiar() {
        datos.limpiar();
    }

    private FiltroValorizacion filtro(AgrupacionReporte agrupacion, boolean incluirInactivas) {
        return new FiltroValorizacion(cat, agrupacion, incluirInactivas);
    }

    @Test
    void consultar_incluyeLoReservadoYValoraACostoYAPrecioDeVenta() {
        datos.reservar(m, 3, "Cliente Reservado", "@cliente_reservado");

        ValorizacionResponse r = servicio.consultar(filtro(AgrupacionReporte.VARIANTE, true));

        FilaValorizacion fm = r.filas().stream().filter(f -> f.id().equals(m)).findFirst().orElseThrow();
        assertThat(fm.unidadesFisicas()).isEqualTo(10);
        assertThat(fm.unidadesReservadas()).isEqualTo(3);
        assertThat(fm.valorCosto()).isEqualByComparingTo("200000.00");
        assertThat(fm.valorVenta()).isEqualByComparingTo("500000.00");
        assertThat(fm.margenPotencial()).isEqualByComparingTo("300000.00");
        assertThat(fm.margenPotencialPorcentaje()).isEqualByComparingTo("60.0");
        assertThat(fm.codigo()).startsWith("S" + p1 + "-M-");
        // 10x20.000 + 5x20.000 + 4x10.000 = 340.000; a venta 750.000 + 120.000 = 870.000
        assertThat(r.totales().valorCosto()).isEqualByComparingTo("340000.00");
        assertThat(r.totales().valorVenta()).isEqualByComparingTo("870000.00");
        assertThat(r.totales().unidadesFisicas()).isEqualTo(19);
        assertThat(r.totales().unidadesReservadas()).isEqualTo(3);
        assertThat(r.totales().nombre()).isEqualTo("Total");
        assertThat(r.sinExistencias()).isFalse();
        assertThat(r.mensaje()).isNull();
        assertThat(r.categoria()).startsWith("Valor");
        // ordenado por valor a costo descendente
        assertThat(r.filas()).extracting(FilaValorizacion::id).containsExactly(m, l, s);
    }

    @Test
    void consultar_tresAgrupaciones_mismoTotal() {
        ValorizacionResponse porVariante = servicio.consultar(filtro(AgrupacionReporte.VARIANTE, true));
        ValorizacionResponse porProducto = servicio.consultar(filtro(AgrupacionReporte.PRODUCTO, true));
        ValorizacionResponse porCategoria = servicio.consultar(filtro(AgrupacionReporte.CATEGORIA, true));

        assertThat(porVariante.filas()).hasSize(3);
        assertThat(porProducto.filas()).extracting(FilaValorizacion::id).containsExactly(p1, p2);
        assertThat(porProducto.filas().get(0).valorCosto()).isEqualByComparingTo("300000.00");
        assertThat(porProducto.filas().get(0).categoria()).startsWith("Valor");
        assertThat(porCategoria.filas()).hasSize(1);
        assertThat(porCategoria.filas().get(0).id()).isEqualTo(cat);
        assertThat(porCategoria.filas().get(0).codigo()).isNull();
        assertThat(porCategoria.filas().get(0).categoria()).isNull();
        for (ValorizacionResponse r : new ValorizacionResponse[] {porVariante, porProducto, porCategoria}) {
            assertThat(r.totales().valorCosto()).isEqualByComparingTo("340000.00");
        }
    }

    @Test
    void consultar_variantesInactivasConStock_entranSoloSiSePide() {
        datos.desactivarVariante(s);

        ValorizacionResponse con = servicio.consultar(filtro(AgrupacionReporte.VARIANTE, true));
        ValorizacionResponse sin = servicio.consultar(filtro(AgrupacionReporte.VARIANTE, false));

        assertThat(con.filas()).extracting(FilaValorizacion::id).contains(s);
        assertThat(con.totales().valorCosto()).isEqualByComparingTo("340000.00");
        assertThat(con.notas()).anyMatch(n -> n.contains("inactivas que aún tienen existencias"));
        assertThat(sin.filas()).extracting(FilaValorizacion::id).doesNotContain(s);
        assertThat(sin.totales().valorCosto()).isEqualByComparingTo("300000.00");
        assertThat(sin.incluirInactivas()).isFalse();
    }

    @Test
    void consultar_loEnCamino_esInformativoYNoSumaAlTotal() {
        datos.pedidoEnCamino(m, 5, "18000.00");

        ValorizacionResponse r = servicio.consultar(filtro(AgrupacionReporte.VARIANTE, true));

        FilaValorizacion fm = r.filas().stream().filter(f -> f.id().equals(m)).findFirst().orElseThrow();
        assertThat(fm.unidadesEnCamino()).isEqualTo(5);
        assertThat(fm.valorEnCamino()).isEqualByComparingTo("90000.00");
        assertThat(fm.valorCosto()).isEqualByComparingTo("200000.00");
        assertThat(r.totales().unidadesEnCamino()).isEqualTo(5);
        assertThat(r.totales().valorEnCamino()).isEqualByComparingTo("90000.00");
        assertThat(r.totales().valorCosto()).isEqualByComparingTo("340000.00");
        assertThat(r.totales().unidadesFisicas()).isEqualTo(19);
    }

    @Test
    void consultar_totalCoincideConLaSumaSqlDeFisicoPorPrecioCompra() {
        datos.reservar(l, 2, "Otra Cliente", "3001234567");
        datos.pedidoEnCamino(s, 3, "9000.00");

        ValorizacionResponse r = servicio.consultar(filtro(AgrupacionReporte.PRODUCTO, true));

        BigDecimal sql = jdbc.queryForObject("SELECT SUM(v.stock_fisico * p.precio_compra) FROM variante v"
                + " JOIN producto p ON p.id = v.producto_id WHERE p.categoria_id = ?", BigDecimal.class, cat);
        assertThat(r.totales().valorCosto()).isEqualByComparingTo(sql);
        BigDecimal sqlVenta = jdbc.queryForObject("SELECT SUM(v.stock_fisico * p.precio_venta) FROM variante v"
                + " JOIN producto p ON p.id = v.producto_id WHERE p.categoria_id = ?", BigDecimal.class, cat);
        assertThat(r.totales().valorVenta()).isEqualByComparingTo(sqlVenta);
    }

    @Test
    void consultar_sinExistencias_marcaSinExistenciasConMensaje() {
        Long vacia = datos.categoria("Vacia");
        Long p = datos.producto("VCA", vacia, "1000.00", "2000.00");
        datos.variante(p, "M");

        ValorizacionResponse r = servicio.consultar(new FiltroValorizacion(vacia, AgrupacionReporte.PRODUCTO, true));

        assertThat(r.sinExistencias()).isTrue();
        assertThat(r.mensaje()).isEqualTo("No hay existencias en el inventario");
        assertThat(r.filas()).isEmpty();
        assertThat(r.totales().valorCosto()).isEqualByComparingTo("0.00");
        assertThat(r.totales().margenPotencialPorcentaje()).isNull();
    }

    @Test
    void consultar_productoConPrecioCompraCero_agregaNota() {
        Long regalo = datos.categoria("Regalo");
        Long p = datos.producto("RGL", regalo, "0.00", "20000.00");
        Long v = datos.variante(p, "M");
        datos.entrada(v, 3, "0.00");

        ValorizacionResponse r = servicio.consultar(new FiltroValorizacion(regalo, AgrupacionReporte.PRODUCTO, true));

        assertThat(r.notas()).contains("1 productos con precio de compra 0: su valor a costo es 0.");
        assertThat(r.totales().valorCosto()).isEqualByComparingTo("0.00");
        assertThat(r.totales().valorVenta()).isEqualByComparingTo("60000.00");
    }

    @Test
    void exportar_pdfYExcel_nombreConLaFechaDeHoy() {
        ArchivoExportado pdf = servicio.exportar(filtro(AgrupacionReporte.PRODUCTO, true), FormatoExportacion.PDF,
                "Contadora");
        ArchivoExportado xlsx = servicio.exportar(filtro(AgrupacionReporte.CATEGORIA, false),
                FormatoExportacion.XLSX, "Contadora");

        assertThat(pdf.nombre()).matches("valorizacion_\\d{4}-\\d{2}-\\d{2}\\.pdf");
        assertThat(new String(pdf.contenido(), 0, 4)).isEqualTo("%PDF");
        assertThat(xlsx.nombre()).endsWith(".xlsx");
        assertThat(new String(xlsx.contenido(), 0, 2)).isEqualTo("PK");
    }
}
