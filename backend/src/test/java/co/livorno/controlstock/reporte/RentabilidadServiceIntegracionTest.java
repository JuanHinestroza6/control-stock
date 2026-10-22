package co.livorno.controlstock.reporte;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.analitica.dto.AgrupacionRanking;
import co.livorno.controlstock.analitica.dto.CriterioRanking;
import co.livorno.controlstock.analitica.servicio.MasVendidosService;
import co.livorno.controlstock.comun.exportacion.ArchivoExportado;
import co.livorno.controlstock.comun.exportacion.FormatoExportacion;
import co.livorno.controlstock.inventario.servicio.OperacionInventarioService;
import co.livorno.controlstock.reporte.DatosReporte.L;
import co.livorno.controlstock.reporte.dto.AgrupacionReporte;
import co.livorno.controlstock.reporte.dto.CriterioRentabilidad;
import co.livorno.controlstock.reporte.dto.FilaRentabilidad;
import co.livorno.controlstock.reporte.dto.FiltroRentabilidad;
import co.livorno.controlstock.reporte.dto.RentabilidadResponse;
import co.livorno.controlstock.reporte.servicio.RentabilidadService;
import co.livorno.controlstock.venta.dto.VentaDetalleResponse;
import co.livorno.controlstock.venta.servicio.DevolucionService;
import co.livorno.controlstock.venta.servicio.VentaService;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Rentabilidad contra MySQL real (B3, fase 7). Entradas, ventas, devoluciones y anulaciones pasan por los servicios
 * reales; las fechas se llevan a octubre y noviembre de 2017 y cada prueba filtra por su propia categoría.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class RentabilidadServiceIntegracionTest {

    static final LocalDate DESDE = LocalDate.of(2017, 10, 1);
    static final LocalDate HASTA = LocalDate.of(2017, 10, 31);
    static final LocalDateTime VENTA = LocalDateTime.of(2017, 10, 10, 10, 0);
    static final LocalDateTime MES_SIGUIENTE = LocalDateTime.of(2017, 11, 5, 10, 0);

    @Autowired RentabilidadService servicio;
    @Autowired MasVendidosService masVendidos;
    @Autowired JdbcTemplate jdbc;
    @Autowired VentaService ventas;
    @Autowired DevolucionService devoluciones;
    @Autowired OperacionInventarioService operaciones;

    DatosReporte datos;

    @BeforeEach
    void preparar() {
        datos = new DatosReporte(jdbc, ventas, devoluciones, operaciones);
    }

    @AfterEach
    void limpiar() {
        datos.limpiar();
    }

    private FiltroRentabilidad filtro(Long categoriaId, AgrupacionReporte agrupacion, CriterioRentabilidad orden) {
        return new FiltroRentabilidad(DESDE, HASTA, categoriaId, agrupacion, orden);
    }

    /** Categoría con el ejemplo a mano del plan: 3 vendidas, lista 50.000, unitario 45.000, costo 20.000. */
    private Long ejemploAMano() {
        Long cat = datos.categoria("Mano");
        Long producto = datos.producto("MANO", cat, "20000.00", "50000.00");
        Long m = datos.variante(producto, "M");
        datos.entrada(m, 100, "20000.00");
        VentaDetalleResponse v = datos.vender(VENTA, new L(m, 3, "45000.00"));
        datos.devolver(v, 0, 1, true, MES_SIGUIENTE);
        return cat;
    }

    @Test
    void consultar_ejemploAMano_devolucionDelMesSiguienteRestaEnLaFechaDeLaVenta() {
        Long cat = ejemploAMano();

        RentabilidadResponse r = servicio.consultar(filtro(cat, AgrupacionReporte.CATEGORIA, null));

        assertThat(r.sinVentas()).isFalse();
        assertThat(r.mensaje()).isNull();
        assertThat(r.filas()).hasSize(1);
        FilaRentabilidad f = r.filas().get(0);
        assertThat(f.categoria()).isNull();
        assertThat(f.codigo()).isNull();
        assertThat(f.ventasLista()).isEqualByComparingTo("150000.00");
        assertThat(f.descuentos()).isEqualByComparingTo("15000.00");
        assertThat(f.facturado()).isEqualByComparingTo("135000.00");
        assertThat(f.devoluciones()).isEqualByComparingTo("45000.00");
        assertThat(f.ingresoNeto()).isEqualByComparingTo("90000.00");
        assertThat(f.costoVentas()).isEqualByComparingTo("40000.00");
        assertThat(f.margenBruto()).isEqualByComparingTo("50000.00");
        assertThat(f.margenPorcentaje()).isEqualByComparingTo("55.6");
        assertThat(f.margenPorcentaje().scale()).isEqualTo(1);
        assertThat(f.mermaNoApta()).isEqualByComparingTo("0.00");
        assertThat(f.resultadoDespuesMermas()).isEqualByComparingTo("50000.00");
        assertThat(f.unidadesVendidas()).isEqualTo(3);
        assertThat(f.unidadesDevueltas()).isEqualTo(1);
        assertThat(f.unidadesNetas()).isEqualTo(2);
        assertThat(f.participacionMargen()).isEqualByComparingTo("100.0");
        assertThat(r.totales().margenBruto()).isEqualByComparingTo("50000.00");
        assertThat(r.totales().margenPorcentaje()).isEqualByComparingTo("55.6");
        assertThat(r.totales().ingresoNeto().scale()).isEqualTo(2);
        assertThat(r.categoria()).startsWith("Mano");
        assertThat(r.desde()).isEqualTo(DESDE);
        assertThat(r.notas()).anyMatch(n -> n.contains("aproximado"));
    }

    @Test
    void consultar_ventaAnulada_quedaFueraDeTodo() {
        Long cat = ejemploAMano();
        Long producto = jdbc.queryForObject("SELECT id FROM producto WHERE categoria_id = ?", Long.class, cat);
        Long m = jdbc.queryForObject("SELECT id FROM variante WHERE producto_id = ?", Long.class, producto);
        VentaDetalleResponse anulada = datos.vender(VENTA.plusDays(1), new L(m, 5, null));
        datos.anular(anulada, VENTA.plusDays(1));

        RentabilidadResponse r = servicio.consultar(filtro(cat, AgrupacionReporte.PRODUCTO, null));

        assertThat(r.filas()).hasSize(1);
        assertThat(r.totales().facturado()).isEqualByComparingTo("135000.00");
        assertThat(r.totales().unidadesVendidas()).isEqualTo(3);
        assertThat(r.totales().margenBruto()).isEqualByComparingTo("50000.00");
    }

    @Test
    void consultar_devolucionNoApta_vaComoMermaYElCostoSeRebaja() {
        Long cat = datos.categoria("Merma");
        Long producto = datos.producto("MERMA", cat, "20000.00", "50000.00");
        Long m = datos.variante(producto, "M");
        datos.entrada(m, 10, "20000.00");
        VentaDetalleResponse v = datos.vender(VENTA, new L(m, 1, null));
        datos.devolver(v, 0, 1, false, VENTA.plusDays(2));

        RentabilidadResponse r = servicio.consultar(filtro(cat, AgrupacionReporte.PRODUCTO, null));

        FilaRentabilidad f = r.filas().get(0);
        assertThat(f.ingresoNeto()).isEqualByComparingTo("0.00");
        assertThat(f.costoVentas()).isEqualByComparingTo("0.00");
        assertThat(f.margenBruto()).isEqualByComparingTo("0.00");
        assertThat(f.margenPorcentaje()).isNull();
        assertThat(f.mermaNoApta()).isEqualByComparingTo("20000.00");
        assertThat(f.resultadoDespuesMermas()).isEqualByComparingTo("-20000.00");
        assertThat(f.participacionMargen()).isNull();
        assertThat(r.totales().resultadoDespuesMermas()).isEqualByComparingTo("-20000.00");
    }

    @Test
    void consultar_tresAgrupacionesYOrdenes_filasYDesempateCorrectos() {
        Long cat = datos.categoria("Agrup");
        Long pA = datos.producto("AGA", cat, "10000.00", "30000.00");
        Long pB = datos.producto("AGB", cat, "10000.00", "30000.00");
        Long aM = datos.variante(pA, "M");
        Long aL = datos.variante(pA, "L");
        Long bM = datos.variante(pB, "M");
        for (Long v : new Long[] {aM, aL, bM}) {
            datos.entrada(v, 50, "10000.00");
        }
        // aM: 2 u (margen 40.000); aL: 1 u (margen 20.000); bM: 5 u (margen 100.000, el mayor)
        datos.vender(VENTA, new L(aM, 2, null), new L(aL, 1, null), new L(bM, 5, null));

        RentabilidadResponse porVariante = servicio.consultar(filtro(cat, AgrupacionReporte.VARIANTE,
                CriterioRentabilidad.MARGEN));
        RentabilidadResponse porProducto = servicio.consultar(filtro(cat, AgrupacionReporte.PRODUCTO,
                CriterioRentabilidad.UNIDADES));
        RentabilidadResponse porCategoria = servicio.consultar(filtro(cat, AgrupacionReporte.CATEGORIA,
                CriterioRentabilidad.INGRESO));

        assertThat(porVariante.filas()).extracting(FilaRentabilidad::id).containsExactly(bM, aM, aL);
        assertThat(porVariante.filas().get(0).codigo()).startsWith("S" + pB + "-M-");
        assertThat(porVariante.filas().get(0).nombre()).contains("Prenda AGB").contains("NEGRO");
        assertThat(porVariante.filas().get(0).participacionMargen()).isEqualByComparingTo("62.5");
        // por producto y unidades netas: B (5) antes que A (3)
        assertThat(porProducto.filas()).extracting(FilaRentabilidad::id).containsExactly(pB, pA);
        assertThat(porProducto.filas().get(1).unidadesNetas()).isEqualTo(3);
        assertThat(porProducto.filas().get(0).categoria()).startsWith("Agrup");
        assertThat(porCategoria.filas()).hasSize(1);
        assertThat(porCategoria.filas().get(0).id()).isEqualTo(cat);
        assertThat(porCategoria.filas().get(0).ingresoNeto()).isEqualByComparingTo("240000.00");
        assertThat(porCategoria.filas().get(0).nombre()).startsWith("Agrup");
        // el total es el mismo en cualquier agrupación
        assertThat(porVariante.totales().margenBruto()).isEqualByComparingTo("160000.00");
        assertThat(porProducto.totales().margenBruto()).isEqualByComparingTo("160000.00");
    }

    @Test
    void consultar_empateEnElCriterio_desempataPorIdAscendente() {
        Long cat = datos.categoria("Empate");
        Long p = datos.producto("EMP", cat, "10000.00", "30000.00");
        Long m = datos.variante(p, "M");
        Long l = datos.variante(p, "L");
        datos.entrada(m, 10, "10000.00");
        datos.entrada(l, 10, "10000.00");
        datos.vender(VENTA, new L(l, 1, null), new L(m, 1, null));

        RentabilidadResponse r = servicio.consultar(filtro(cat, AgrupacionReporte.VARIANTE,
                CriterioRentabilidad.UNIDADES));

        assertThat(r.filas()).extracting(FilaRentabilidad::id).containsExactly(m, l);
    }

    @Test
    void consultar_sinVentasEnElPeriodo_200ConMensajeYTotalesEnCero() {
        Long cat = datos.categoria("Vacia");

        RentabilidadResponse r = servicio.consultar(filtro(cat, AgrupacionReporte.PRODUCTO, null));

        assertThat(r.sinVentas()).isTrue();
        assertThat(r.mensaje()).isEqualTo("No hay ventas en el periodo");
        assertThat(r.filas()).isEmpty();
        assertThat(r.totales().ingresoNeto()).isEqualByComparingTo("0.00");
        assertThat(r.totales().margenPorcentaje()).isNull();
    }

    @Test
    void consultar_lineaConCostoCero_agregaNota() {
        Long cat = datos.categoria("Regalo");
        Long p = datos.producto("REG", cat, "0.00", "30000.00");
        Long m = datos.variante(p, "M");
        datos.entrada(m, 10, "0.00");
        datos.vender(VENTA, new L(m, 2, null));

        RentabilidadResponse r = servicio.consultar(filtro(cat, AgrupacionReporte.PRODUCTO, null));

        assertThat(r.notas()).contains("1 líneas con costo 0: su margen es igual al ingreso.");
        assertThat(r.totales().margenBruto()).isEqualByComparingTo("60000.00");
        assertThat(r.totales().margenPorcentaje()).isEqualByComparingTo("100.0");
    }

    @Test
    void consultar_ingresoNetoTotal_coincideConMasVendidosDelMismoRango() {
        Long cat = ejemploAMano();
        Long p2 = datos.producto("OTRO", cat, "8000.00", "25000.00");
        Long v2 = datos.variante(p2, "S");
        datos.entrada(v2, 20, "8000.00");
        VentaDetalleResponse v = datos.vender(VENTA.plusDays(3), new L(v2, 4, "20000.00"));
        datos.devolver(v, 0, 2, false, VENTA.plusDays(40));

        RentabilidadResponse r = servicio.consultar(filtro(cat, AgrupacionReporte.PRODUCTO, null));
        var ranking = masVendidos.consultar(DESDE, HASTA, cat, CriterioRanking.INGRESO, AgrupacionRanking.PRODUCTO, 50);

        assertThat(r.totales().ingresoNeto()).isEqualByComparingTo(ranking.totalIngreso());
        assertThat(r.totales().unidadesNetas()).isEqualTo(ranking.totalUnidades());
        assertThat(r.totales().ingresoNeto()).isEqualByComparingTo("130000.00");
    }

    @Test
    void exportar_pdfYExcel_mismaRespuestaConNombreBase() {
        Long cat = ejemploAMano();
        FiltroRentabilidad f = filtro(cat, AgrupacionReporte.PRODUCTO, null);

        ArchivoExportado pdf = servicio.exportar(f, FormatoExportacion.PDF, "Contadora");
        ArchivoExportado xlsx = servicio.exportar(f, FormatoExportacion.XLSX, "Contadora");

        assertThat(pdf.nombre()).isEqualTo("rentabilidad_2017-10-01_2017-10-31.pdf");
        assertThat(new String(pdf.contenido(), 0, 4)).isEqualTo("%PDF");
        assertThat(xlsx.nombre()).isEqualTo("rentabilidad_2017-10-01_2017-10-31.xlsx");
        assertThat(new String(xlsx.contenido(), 0, 2)).isEqualTo("PK");
    }

    @Test
    void exportar_sinVentas_generaElArchivoVacioConMensaje() {
        Long cat = datos.categoria("Vacia");

        ArchivoExportado pdf = servicio.exportar(filtro(cat, AgrupacionReporte.CATEGORIA, null),
                FormatoExportacion.PDF, "Contadora");

        assertThat(pdf.contenido()).isNotEmpty();
    }
}
