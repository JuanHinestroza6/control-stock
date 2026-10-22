package co.livorno.controlstock.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.exportacion.ArchivoExportado;
import co.livorno.controlstock.comun.exportacion.ExportadorDocumentos;
import co.livorno.controlstock.comun.exportacion.FormatoExportacion;
import co.livorno.controlstock.inventario.servicio.OperacionInventarioService;
import co.livorno.controlstock.reporte.DatosReporte.L;
import co.livorno.controlstock.reporte.dto.AgrupacionReporte;
import co.livorno.controlstock.reporte.dto.FilaLibro;
import co.livorno.controlstock.reporte.dto.FiltroLibroVentas;
import co.livorno.controlstock.reporte.dto.FiltroRentabilidad;
import co.livorno.controlstock.reporte.dto.LibroVentasResponse;
import co.livorno.controlstock.reporte.dto.PeriodoLibro;
import co.livorno.controlstock.reporte.dto.RentabilidadResponse;
import co.livorno.controlstock.reporte.repositorio.ConsultasReporte;
import co.livorno.controlstock.reporte.servicio.LibroVentasService;
import co.livorno.controlstock.reporte.servicio.RentabilidadService;
import co.livorno.controlstock.reporte.servicio.ValidadorRangoReporte;
import co.livorno.controlstock.venta.dto.VentaDetalleResponse;
import co.livorno.controlstock.venta.servicio.DevolucionService;
import co.livorno.controlstock.venta.servicio.VentaService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
 * Libro de ventas contra MySQL real (B4, fase 7). Entradas, ventas, devoluciones y anulaciones pasan por los servicios
 * reales; las fechas se llevan a octubre y noviembre de 2016 (el libro no filtra por categoría, así que ese año queda
 * solo para esta prueba).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class LibroVentasServiceIntegracionTest {

    static final LocalDate DESDE = LocalDate.of(2016, 10, 1);
    static final LocalDate HASTA = LocalDate.of(2016, 11, 30);
    static final LocalDateTime OCT_10 = LocalDateTime.of(2016, 10, 10, 10, 0);
    static final LocalDateTime NOV_05 = LocalDateTime.of(2016, 11, 5, 10, 0);

    @Autowired LibroVentasService libro;
    @Autowired RentabilidadService rentabilidad;
    @Autowired JdbcTemplate jdbc;
    @Autowired VentaService ventas;
    @Autowired DevolucionService devoluciones;
    @Autowired OperacionInventarioService operaciones;
    @Autowired ConsultasReporte consultas;
    @Autowired ValidadorRangoReporte validador;
    @Autowired ExportadorDocumentos exportador;
    @Autowired Clock reloj;

    DatosReporte datos;
    Long cat;
    Long p1;
    Long m1;
    Long p2;
    Long s2;

    @BeforeEach
    void preparar() {
        datos = new DatosReporte(jdbc, ventas, devoluciones, operaciones);
        cat = datos.categoria("Libro");
        p1 = datos.producto("LB1", cat, "20000.00", "50000.00");
        m1 = datos.variante(p1, "M");
        p2 = datos.producto("LB2", cat, "8000.00", "25000.00");
        s2 = datos.variante(p2, "S");
        datos.entrada(m1, 100, "20000.00");
        datos.entrada(s2, 100, "8000.00");
    }

    @AfterEach
    void limpiar() {
        datos.limpiar();
    }

    private FiltroLibroVentas filtro(LocalDate desde, LocalDate hasta, PeriodoLibro agrupacion) {
        return new FiltroLibroVentas(desde, hasta, agrupacion);
    }

    /** Ejemplo a mano: 3 u a 45.000 (lista 50.000, costo 20.000) el 10 de octubre y 1 devuelta apta el 5 de noviembre. */
    private VentaDetalleResponse ejemploAMano() {
        VentaDetalleResponse v = datos.vender(OCT_10, new L(m1, 3, "45000.00"));
        datos.devolver(v, 0, 1, true, NOV_05);
        return v;
    }

    /** Ejemplo + venta de P2 con devolución no apta de noviembre + una venta anulada. */
    private VentaDetalleResponse escenarioCompleto() {
        VentaDetalleResponse v1 = ejemploAMano();
        VentaDetalleResponse v2 = datos.vender(OCT_10.plusDays(10), new L(s2, 4, "20000.00"));
        datos.devolver(v2, 0, 2, false, NOV_05.plusDays(15));
        VentaDetalleResponse anulada = datos.vender(OCT_10.plusDays(15), new L(m1, 2, null));
        datos.anular(anulada, OCT_10.plusDays(15));
        return v1;
    }

    private static FilaLibro fila(LibroVentasResponse r, String periodo) {
        return r.filas().stream().filter(f -> f.periodo().equals(periodo)).findFirst().orElseThrow();
    }

    @Test
    void consultar_ejemploAManoPorMes_cadaMesConSuCriterio() {
        ejemploAMano();

        LibroVentasResponse r = libro.consultar(filtro(DESDE, HASTA, PeriodoLibro.MES));

        assertThat(r.filas()).extracting(FilaLibro::periodo).containsExactly("2016-10", "2016-11");
        FilaLibro oct = fila(r, "2016-10");
        assertThat(oct.numeroVentas()).isEqualTo(1);
        assertThat(oct.ventasLista()).isEqualByComparingTo("150000.00");
        assertThat(oct.descuentos()).isEqualByComparingTo("15000.00");
        assertThat(oct.facturado()).isEqualByComparingTo("135000.00");
        assertThat(oct.devoluciones()).isEqualByComparingTo("0.00");
        assertThat(oct.ingresoNeto()).isEqualByComparingTo("135000.00");
        assertThat(oct.costoVentas()).isEqualByComparingTo("60000.00");
        assertThat(oct.margenBruto()).isEqualByComparingTo("75000.00");
        assertThat(oct.unidadesVendidas()).isEqualTo(3);
        FilaLibro nov = fila(r, "2016-11");
        assertThat(nov.numeroVentas()).isZero();
        assertThat(nov.devoluciones()).isEqualByComparingTo("45000.00");
        assertThat(nov.ingresoNeto()).isEqualByComparingTo("-45000.00");
        assertThat(nov.costoVentas()).isEqualByComparingTo("-20000.00");
        assertThat(nov.margenBruto()).isEqualByComparingTo("-25000.00");
        assertThat(nov.unidadesDevueltas()).isEqualTo(1);
        assertThat(r.totales().periodo()).isNull();
        assertThat(r.totales().ingresoNeto()).isEqualByComparingTo("90000.00");
        assertThat(r.totales().costoVentas()).isEqualByComparingTo("40000.00");
        assertThat(r.totales().margenBruto()).isEqualByComparingTo("50000.00");
        assertThat(r.sinVentas()).isFalse();
        assertThat(r.mensaje()).isNull();
        assertThat(r.lineasDetalle()).isEqualTo(1);
    }

    @Test
    void consultar_conciliacionConRentabilidad_mismoNetoCostoYMargenSobreElRangoCompleto() {
        escenarioCompleto();

        LibroVentasResponse l = libro.consultar(filtro(DESDE, HASTA, PeriodoLibro.MES));
        RentabilidadResponse r = rentabilidad.consultar(new FiltroRentabilidad(DESDE, HASTA, null,
                AgrupacionReporte.CATEGORIA, null));

        // neto 90.000 + 40.000; costo 40.000 + 16.000; margen 74.000; merma 2 x 8.000
        assertThat(l.totales().ingresoNeto()).isEqualByComparingTo("130000.00");
        assertThat(l.totales().costoVentas()).isEqualByComparingTo("56000.00");
        assertThat(l.totales().margenBruto()).isEqualByComparingTo("74000.00");
        assertThat(l.totales().ingresoNeto()).isEqualByComparingTo(r.totales().ingresoNeto());
        assertThat(l.totales().costoVentas()).isEqualByComparingTo(r.totales().costoVentas());
        assertThat(l.totales().margenBruto()).isEqualByComparingTo(r.totales().margenBruto());
        assertThat(l.totales().mermaNoApta()).isEqualByComparingTo(r.totales().mermaNoApta());
        assertThat(l.totales().mermaNoApta()).isEqualByComparingTo("16000.00");
        assertThat(l.totales().facturado()).isEqualByComparingTo(r.totales().facturado());
        assertThat(l.totales().unidadesVendidas()).isEqualTo(r.totales().unidadesVendidas());
        assertThat(l.totales().unidadesDevueltas()).isEqualTo(r.totales().unidadesDevueltas());
    }

    @Test
    void consultar_ventasAnuladas_seListanAparteYNoEntranEnLasCifras() {
        escenarioCompleto();

        LibroVentasResponse r = libro.consultar(filtro(DESDE, HASTA, PeriodoLibro.MES));

        assertThat(r.totalAnuladas()).isEqualTo(1);
        assertThat(r.montoAnuladas()).isEqualByComparingTo("100000.00");
        assertThat(r.anuladas()).hasSize(1);
        var a = r.anuladas().get(0);
        assertThat(a.numero()).matches("V-\\d{6}");
        assertThat(a.total()).isEqualByComparingTo("100000.00");
        assertThat(a.anuladaPor()).isEqualTo(datos.nombreAdmin);
        assertThat(a.motivoAnulacion()).isEqualTo("Error de digitación");
        assertThat(a.fecha()).isEqualTo(OCT_10.plusDays(15));
        assertThat(a.anuladaEn()).isEqualTo(OCT_10.plusDays(15).plusHours(1));
        // octubre: solo las dos ventas registradas (135.000 + 80.000), no las 100.000 de la anulada
        assertThat(fila(r, "2016-10").facturado()).isEqualByComparingTo("215000.00");
        assertThat(fila(r, "2016-10").numeroVentas()).isEqualTo(2);
        assertThat(r.lineasDetalle()).isEqualTo(2);
    }

    @Test
    void consultar_diaVersusMes_mismaSumaConMasFilasPorDia() {
        datos.vender(OCT_10, new L(m1, 1, null));
        datos.vender(OCT_10.plusDays(1), new L(m1, 2, null));
        datos.vender(OCT_10.plusDays(1).plusHours(3), new L(s2, 1, null));

        LibroVentasResponse porDia = libro.consultar(filtro(DESDE, HASTA, PeriodoLibro.DIA));
        LibroVentasResponse porMes = libro.consultar(filtro(DESDE, HASTA, PeriodoLibro.MES));

        assertThat(porDia.filas()).extracting(FilaLibro::periodo).containsExactly("2016-10-10", "2016-10-11");
        assertThat(porDia.filas().get(1).numeroVentas()).isEqualTo(2);
        assertThat(porMes.filas()).extracting(FilaLibro::periodo).containsExactly("2016-10");
        assertThat(porMes.filas().get(0).numeroVentas()).isEqualTo(3);
        assertThat(porDia.totales().facturado()).isEqualByComparingTo(porMes.totales().facturado());
        assertThat(porDia.totales().facturado()).isEqualByComparingTo("175000.00");
        assertThat(porDia.totales().numeroVentas()).isEqualTo(3);
    }

    @Test
    void consultar_devolucionDeUnaVentaAnteriorAlRango_cuentaEnSuFechaConNetoNegativo() {
        ejemploAMano();

        LibroVentasResponse r = libro.consultar(filtro(LocalDate.of(2016, 11, 1), HASTA, PeriodoLibro.MES));

        assertThat(r.sinVentas()).isFalse();
        assertThat(r.filas()).hasSize(1);
        assertThat(r.filas().get(0).numeroVentas()).isZero();
        assertThat(r.filas().get(0).ingresoNeto()).isEqualByComparingTo("-45000.00");
        assertThat(r.filas().get(0).margenBruto()).isEqualByComparingTo("-25000.00");
        assertThat(r.lineasDetalle()).isZero();
    }

    @Test
    void consultar_periodosSinMovimiento_seOmitenYSeAnotaEnLasNotas() {
        datos.vender(OCT_10, new L(m1, 1, null));
        datos.vender(OCT_10.plusDays(20), new L(m1, 1, null));

        LibroVentasResponse r = libro.consultar(filtro(DESDE, HASTA, PeriodoLibro.DIA));

        assertThat(r.filas()).hasSize(2);
        assertThat(r.notas()).anyMatch(n -> n.contains("periodos con movimiento"));
    }

    @Test
    void consultar_sinMovimiento_200ConMensajeYTotalesEnCero() {
        LibroVentasResponse r = libro.consultar(filtro(LocalDate.of(2016, 1, 1), LocalDate.of(2016, 1, 31),
                PeriodoLibro.MES));

        assertThat(r.sinVentas()).isTrue();
        assertThat(r.mensaje()).isEqualTo("No hay ventas en el periodo");
        assertThat(r.filas()).isEmpty();
        assertThat(r.totales().ingresoNeto()).isEqualByComparingTo("0.00");
        assertThat(r.totalAnuladas()).isZero();
        assertThat(r.montoAnuladas()).isEqualByComparingTo("0.00");
    }

    @Test
    void exportar_excel_incluyeHojasDeDetalleYDevolucionesYElPdfNo() throws IOException {
        escenarioCompleto();
        FiltroLibroVentas f = filtro(DESDE, HASTA, PeriodoLibro.MES);

        ArchivoExportado xlsx = libro.exportar(f, FormatoExportacion.XLSX, "Contadora");
        ArchivoExportado pdf = libro.exportar(f, FormatoExportacion.PDF, "Contadora");

        assertThat(xlsx.nombre()).isEqualTo("libro-ventas_2016-10-01_2016-11-30.xlsx");
        assertThat(pdf.nombre()).isEqualTo("libro-ventas_2016-10-01_2016-11-30.pdf");
        assertThat(new String(pdf.contenido(), 0, 4)).isEqualTo("%PDF");
        try (XSSFWorkbook libroXlsx = new XSSFWorkbook(new ByteArrayInputStream(xlsx.contenido()))) {
            List<String> hojas = new ArrayList<>();
            libroXlsx.forEach(h -> hojas.add(h.getSheetName()));
            assertThat(hojas).contains("Resumen", "Ventas anuladas", "Detalle de ventas", "Devoluciones", "Notas");
            // encabezado + 2 líneas de ventas registradas (la anulada no va) / encabezado + 2 devoluciones
            assertThat(libroXlsx.getSheet("Detalle de ventas").getLastRowNum()).isEqualTo(2);
            assertThat(libroXlsx.getSheet("Devoluciones").getLastRowNum()).isEqualTo(2);
        }
    }

    @Test
    void exportar_detalleSobreElLimite_422AcoteElRangoSoloEnExcel() {
        escenarioCompleto(); // 2 líneas registradas
        LibroVentasService conLimite = new LibroVentasService(consultas, validador, exportador, reloj, 1);
        FiltroLibroVentas f = filtro(DESDE, HASTA, PeriodoLibro.MES);

        assertThatThrownBy(() -> conLimite.exportar(f, FormatoExportacion.XLSX, "Contadora"))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("Acote el rango");
        // el PDF no lleva detalle: no se bloquea
        assertThat(conLimite.exportar(f, FormatoExportacion.PDF, "Contadora").contenido()).isNotEmpty();
        // y en el límite exacto el Excel sale
        LibroVentasService exacto = new LibroVentasService(consultas, validador, exportador, reloj, 2);
        assertThat(exacto.exportar(f, FormatoExportacion.XLSX, "Contadora").contenido()).isNotEmpty();
    }

    @Test
    void exportar_sinMovimiento_generaArchivosConMensaje() {
        FiltroLibroVentas f = filtro(LocalDate.of(2016, 1, 1), LocalDate.of(2016, 1, 31), PeriodoLibro.MES);

        assertThat(libro.exportar(f, FormatoExportacion.PDF, "Contadora").contenido()).isNotEmpty();
        assertThat(libro.exportar(f, FormatoExportacion.XLSX, "Contadora").contenido()).isNotEmpty();
    }
}
