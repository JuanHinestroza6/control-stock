package co.livorno.controlstock.reporte;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.compra.servicio.PlanCompraService;
import co.livorno.controlstock.inventario.servicio.OperacionInventarioService;
import co.livorno.controlstock.inventario.servicio.ReservaService;
import co.livorno.controlstock.reporte.DatosReporte.L;
import co.livorno.controlstock.reporte.dto.PanelResponse;
import co.livorno.controlstock.reporte.dto.PuntoDia;
import co.livorno.controlstock.reporte.servicio.PanelService;
import co.livorno.controlstock.venta.dto.VentaDetalleResponse;
import co.livorno.controlstock.venta.servicio.DevolucionService;
import co.livorno.controlstock.venta.servicio.VentaService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * Panel de inicio contra MySQL real (B8, fase 7). La base de pruebas es compartida, así que las cifras se comprueban
 * como diferencias frente a una lectura previa; ventas, devoluciones, anulaciones, reservas y planes pasan por los
 * servicios reales.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class PanelServiceIntegracionTest {

    @Autowired PanelService panel;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired VentaService ventas;
    @Autowired DevolucionService devoluciones;
    @Autowired OperacionInventarioService operaciones;
    @Autowired ReservaService reservas;
    @Autowired PlanCompraService planes;

    DatosReporte datos;
    PanelResponse base;
    Long m;
    Long l;
    Long s;
    final List<Long> borradores = new ArrayList<>();

    @BeforeEach
    void preparar() {
        base = panel.obtener("ADMIN");
        datos = new DatosReporte(jdbc, ventas, devoluciones, operaciones).conReservasYPlanes(reservas, planes);
        Long cat = datos.categoria("Panel");
        Long p1 = datos.producto("PN1", cat, "20000.00", "50000.00");
        m = datos.variante(p1, "M");
        l = datos.variante(p1, "L");
        s = datos.variante(p1, "S");
        datos.entrada(m, 10, "20000.00");
        datos.entrada(l, 10, "20000.00");
    }

    @AfterEach
    void limpiar() {
        for (Long plan : borradores) {
            jdbc.update("DELETE FROM detalle_plan WHERE plan_id = ?", plan);
            jdbc.update("DELETE FROM plan_compra WHERE id = ?", plan);
        }
        datos.limpiar();
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }

    // ---------- visibilidad por rol ----------

    @Test
    void obtener_admin_veTodasLasSeccionesConCostos() {
        PanelResponse p = panel.obtener("ADMIN");

        assertThat(p.ventas()).isNotNull();
        assertThat(p.ventas().margenMes()).isNotNull();
        assertThat(p.inventario().valorCosto()).isNotNull();
        assertThat(p.inventario().valorVenta()).isNotNull();
        assertThat(p.reservas()).isNotNull();
        assertThat(p.compras()).isNotNull();
        assertThat(p.compras().valorEnCamino()).isNotNull();
        assertThat(p.generadoEn()).isNotNull();
    }

    @Test
    void obtener_inventario_noVeVentasNiCostos() {
        PanelResponse p = panel.obtener("INVENTARIO");

        assertThat(p.ventas()).isNull();
        assertThat(p.inventario()).isNotNull();
        assertThat(p.inventario().valorCosto()).isNull();
        assertThat(p.inventario().valorVenta()).isNull();
        assertThat(p.reservas()).isNotNull();
        assertThat(p.compras()).isNotNull();
        assertThat(p.compras().valorEnCamino()).isNull();
    }

    @Test
    void obtener_vendedor_veVentasSinMargenYNoVeCompras() {
        PanelResponse p = panel.obtener("VENDEDOR");

        assertThat(p.ventas()).isNotNull();
        assertThat(p.ventas().margenMes()).isNull();
        assertThat(p.ventas().margenMesPorcentaje()).isNull();
        assertThat(p.inventario()).isNotNull();
        assertThat(p.inventario().valorCosto()).isNull();
        assertThat(p.inventario().valorVenta()).isNull();
        assertThat(p.reservas()).isNotNull();
        assertThat(p.compras()).isNull();
    }

    @Test
    void obtener_contabilidad_veCostosPeroNoApartados() {
        PanelResponse p = panel.obtener("CONTABILIDAD");

        assertThat(p.ventas()).isNotNull();
        assertThat(p.ventas().margenMes()).isNotNull();
        assertThat(p.inventario().valorCosto()).isNotNull();
        assertThat(p.inventario().valorVenta()).isNotNull();
        assertThat(p.reservas()).isNull();
        assertThat(p.compras()).isNotNull();
        assertThat(p.compras().valorEnCamino()).isNotNull();
    }

    @Test
    void obtener_rolDesconocido_soloVeInventarioSinCostos() {
        PanelResponse p = panel.obtener("OTRO");

        assertThat(p.ventas()).isNull();
        assertThat(p.reservas()).isNull();
        assertThat(p.compras()).isNull();
        assertThat(p.inventario()).isNotNull();
        assertThat(p.inventario().valorCosto()).isNull();
    }

    // ---------- cifras ----------

    @Test
    void obtener_ventas_netoDeDevolucionesPorFechaDeVentaYSinAnuladas() {
        LocalDateTime ahora = ahora();
        LocalDate hoy = ahora.toLocalDate();
        // hoy: 3 u a 45.000 (neto 135.000, costo 60.000) con 1 devuelta apta -> neto 90.000, costo 40.000
        VentaDetalleResponse deHoy = datos.vender(ahora, new L(m, 3, "45000.00"));
        datos.devolver(deHoy, 0, 1, true, ahora);
        // hace 3 días: 2 u a lista (neto 100.000, costo 40.000)
        datos.vender(ahora.minusDays(3), new L(l, 2, null));
        // hace 40 días: fuera del mes y de la serie
        datos.vender(ahora.minusDays(40), new L(l, 1, null));
        // anulada hoy: no cuenta
        VentaDetalleResponse anulada = datos.vender(ahora, new L(l, 1, null));
        datos.anular(anulada, ahora);

        PanelResponse p = panel.obtener("ADMIN");

        boolean haceTresDiasEnElMes = hoy.minusDays(3).getMonth() == hoy.getMonth();
        BigDecimal netoMes = haceTresDiasEnElMes ? new BigDecimal("190000.00") : new BigDecimal("90000.00");
        BigDecimal margenMes = haceTresDiasEnElMes ? new BigDecimal("110000.00") : new BigDecimal("50000.00");
        assertThat(p.ventas().ventasHoy().subtract(base.ventas().ventasHoy())).isEqualByComparingTo("90000.00");
        assertThat(p.ventas().numeroVentasHoy() - base.ventas().numeroVentasHoy()).isEqualTo(1);
        assertThat(p.ventas().ventasMes().subtract(base.ventas().ventasMes())).isEqualByComparingTo(netoMes);
        assertThat(p.ventas().numeroVentasMes() - base.ventas().numeroVentasMes())
                .isEqualTo(haceTresDiasEnElMes ? 2 : 1);
        assertThat(p.ventas().margenMes().subtract(base.ventas().margenMes())).isEqualByComparingTo(margenMes);
        assertThat(p.ventas().margenMesPorcentaje()).isNotNull();

        List<PuntoDia> serie = p.ventas().ultimos7Dias();
        assertThat(serie).hasSize(7);
        assertThat(serie.get(6).fecha()).isEqualTo(hoy);
        assertThat(serie.get(0).fecha()).isEqualTo(hoy.minusDays(6));
        for (int i = 1; i < serie.size(); i++) {
            assertThat(serie.get(i).fecha()).isEqualTo(serie.get(i - 1).fecha().plusDays(1));
        }
        List<PuntoDia> serieBase = base.ventas().ultimos7Dias();
        assertThat(serie.get(6).total().subtract(serieBase.get(6).total())).isEqualByComparingTo("90000.00");
        assertThat(serie.get(6).numeroVentas() - serieBase.get(6).numeroVentas()).isEqualTo(1);
        assertThat(serie.get(3).total().subtract(serieBase.get(3).total())).isEqualByComparingTo("100000.00");
        assertThat(serie.get(3).numeroVentas() - serieBase.get(3).numeroVentas()).isEqualTo(1);
    }

    @Test
    void obtener_vendedor_noRecibeMargenAunqueHayaVentas() {
        datos.vender(ahora(), new L(m, 1, null));

        PanelResponse p = panel.obtener("VENDEDOR");

        assertThat(p.ventas().ventasHoy()).isGreaterThanOrEqualTo(new BigDecimal("50000.00"));
        assertThat(p.ventas().margenMes()).isNull();
        assertThat(p.ventas().margenMesPorcentaje()).isNull();
    }

    @Test
    void obtener_inventarioReservasYAlertas_sumanLoCorrectoYNoExponenClientes() {
        PanelResponse antes = panel.obtener("ADMIN");
        // existencias: 20 u a 20.000 de costo y 50.000 de venta
        assertThat(antes.inventario().valorCosto().subtract(base.inventario().valorCosto()))
                .isEqualByComparingTo("400000.00");
        assertThat(antes.inventario().valorVenta().subtract(base.inventario().valorVenta()))
                .isEqualByComparingTo("1000000.00");
        assertThat(antes.inventario().variantesActivas() - base.inventario().variantesActivas()).isEqualTo(3);
        assertThat(antes.inventario().unidadesFisicas() - base.inventario().unidadesFisicas()).isEqualTo(20);

        datos.reservar(l, 4, "Cliente Panel Secreto", "@secreto_panel");
        jdbc.update("UPDATE reserva SET vence_en = ? WHERE variante_id = ?", ahora().plusHours(2), l);
        for (String tipo : new String[] {"AGOTADO", "STOCK_BAJO", "SIN_ROTACION"}) {
            jdbc.update("INSERT INTO alerta (variante_id, tipo, estado, creada_en, revisada_en) VALUES (?, ?, 'ACTIVA',"
                    + " NOW(6), NOW(6))", s, tipo);
        }

        PanelResponse p = panel.obtener("ADMIN");

        assertThat(p.reservas().activas() - antes.reservas().activas()).isEqualTo(1);
        assertThat(p.reservas().unidadesReservadas() - antes.reservas().unidadesReservadas()).isEqualTo(4);
        assertThat(p.reservas().porVencer24h() - antes.reservas().porVencer24h()).isEqualTo(1);
        assertThat(p.inventario().unidadesDisponibles() - antes.inventario().unidadesDisponibles()).isEqualTo(-4);
        assertThat(p.inventario().unidadesFisicas()).isEqualTo(antes.inventario().unidadesFisicas());
        assertThat(p.inventario().alertasAgotado() - antes.inventario().alertasAgotado()).isEqualTo(1);
        assertThat(p.inventario().alertasStockBajo() - antes.inventario().alertasStockBajo()).isEqualTo(1);
        assertThat(p.inventario().alertasSinRotacion() - antes.inventario().alertasSinRotacion()).isEqualTo(1);
        assertThat(p.toString()).doesNotContain("Secreto").doesNotContain("secreto_panel");
    }

    @Test
    void obtener_variantesInactivas_salenDelConteoPeroSigueValorandoseSuStock() {
        PanelResponse antes = panel.obtener("ADMIN");

        datos.desactivarVariante(m);
        PanelResponse p = panel.obtener("ADMIN");

        assertThat(p.inventario().variantesActivas() - antes.inventario().variantesActivas()).isEqualTo(-1);
        assertThat(p.inventario().unidadesFisicas() - antes.inventario().unidadesFisicas()).isEqualTo(-10);
        // el valor concilia con la valorización: incluye las inactivas con existencias
        assertThat(p.inventario().valorCosto()).isEqualByComparingTo(antes.inventario().valorCosto());
        assertThat(p.inventario().valorVenta()).isEqualByComparingTo(antes.inventario().valorVenta());
    }

    @Test
    void obtener_compras_cuentaBorradoresEnCaminoYAtrasados() {
        // un borrador, un aprobado a tiempo y un aprobado hace 60 días (lead time 15) -> atrasado
        Long borrador = borrador(m, 7);
        borradores.add(borrador);
        datos.pedidoEnCamino(m, 5, "20000.00");
        datos.pedidoEnCamino(l, 3, "10000.00");
        jdbc.update("UPDATE plan_compra SET aprobado_en = ? WHERE id IN (SELECT plan_id FROM detalle_plan"
                + " WHERE variante_id = ?)", ahora().minusDays(60), l);

        PanelResponse p = panel.obtener("ADMIN");

        assertThat(p.compras().planesBorrador() - base.compras().planesBorrador()).isEqualTo(1);
        assertThat(p.compras().planesEnCamino() - base.compras().planesEnCamino()).isEqualTo(2);
        assertThat(p.compras().unidadesEnCamino() - base.compras().unidadesEnCamino()).isEqualTo(8);
        assertThat(p.compras().planesAtrasados() - base.compras().planesAtrasados()).isEqualTo(1);
        assertThat(p.compras().valorEnCamino().subtract(base.compras().valorEnCamino()))
                .isEqualByComparingTo("130000.00");
        // INVENTARIO ve los conteos pero no el valor
        PanelResponse inv = panel.obtener("INVENTARIO");
        assertThat(inv.compras().planesEnCamino()).isEqualTo(p.compras().planesEnCamino());
        assertThat(inv.compras().valorEnCamino()).isNull();
    }

    private Long borrador(Long varianteId, int cantidad) {
        Long usuario = datos.adminId;
        jdbc.update("INSERT INTO plan_compra (estado, origen, creado_en, creado_por, version, actualizado_en)"
                + " VALUES ('BORRADOR', 'MANUAL', NOW(6), ?, 0, NOW(6))", usuario);
        Long plan = jdbc.queryForObject("SELECT MAX(id) FROM plan_compra", Long.class);
        jdbc.update("INSERT INTO detalle_plan (plan_id, variante_id, cantidad_aprobada, costo_estimado)"
                + " VALUES (?, ?, ?, 10.00)", plan, varianteId, cantidad);
        return plan;
    }
}
