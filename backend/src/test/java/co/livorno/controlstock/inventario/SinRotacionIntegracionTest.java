package co.livorno.controlstock.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.catalogo.servicio.ProductoService;
import co.livorno.controlstock.catalogo.servicio.VarianteService;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.modelo.TipoAlerta;
import co.livorno.controlstock.inventario.servicio.AlertaService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * SIN_ROTACION por producto (ADR-009) contra MySQL real. El tiempo se mueve con fechas pasadas en el producto, el
 * kardex y las alertas (el reloj de la aplicación es el del sistema).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class SinRotacionIntegracionTest {

    @Autowired AlertaService alertas;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductoService productos;
    @Autowired VarianteService variantesServicio;

    private final List<Long> productosCreados = new ArrayList<>();
    private int contador;

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        for (Long p : productosCreados) {
            String hijas = "variante_id IN (SELECT id FROM variante WHERE producto_id = ?)";
            jdbc.update("DELETE FROM movimiento WHERE " + hijas, p);
            jdbc.update("DELETE FROM alerta WHERE " + hijas, p);
            jdbc.update("DELETE FROM analitica_variante WHERE " + hijas, p);
            jdbc.update("DELETE FROM reserva WHERE " + hijas, p);
            jdbc.update("DELETE FROM variante WHERE producto_id = ?", p);
            jdbc.update("DELETE FROM producto WHERE id = ?", p);
        }
        productosCreados.clear();
    }

    private static LocalDateTime hace(int dias) {
        return LocalDateTime.now(ZoneId.of("America/Bogota")).minusDays(dias);
    }

    private Long producto(int diasDeCreado) {
        String codigo = "SR" + Long.toString(System.nanoTime(), 36).toUpperCase() + (contador++);
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, creado_en,"
                + " actualizado_en) VALUES (?, 'Prueba', ?, 10.00, 20.00, ?, ?)", codigo, categoria,
                hace(diasDeCreado), hace(diasDeCreado));
        Long id = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, codigo);
        productosCreados.add(id);
        return id;
    }

    private Long variante(Long producto, String talla, int fisico, int reservado, int stockMinimo) {
        String sku = "S" + Long.toString(System.nanoTime(), 36).toUpperCase() + talla;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado,"
                + " stock_minimo, creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', ?, ?, ?, NOW(6), NOW(6))",
                producto, sku, talla, fisico, reservado, stockMinimo);
        return jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
    }

    /** Inserta un movimiento de kardex con la fecha dada (los saldos no importan para estas consultas). */
    private void movimiento(Long variante, String tipo, int diasAtras, long ventaId) {
        boolean entra = tipo.equals("ANULACION_VENTA");
        jdbc.update("INSERT INTO movimiento (variante_id, tipo, cantidad, fisico_antes, fisico_despues,"
                + " reservado_antes, reservado_despues, fecha, referencia_tipo, referencia_id)"
                + " VALUES (?, ?, 1, ?, ?, 0, 0, ?, 'VENTA', ?)", variante, tipo, entra ? 5 : 6, entra ? 6 : 5,
                hace(diasAtras), ventaId);
    }

    private void atenderAMano(Long variante, int diasAtras) {
        Long usuario = jdbc.queryForObject("SELECT id FROM usuario ORDER BY id LIMIT 1", Long.class);
        jdbc.update("UPDATE alerta SET estado = 'ATENDIDA', atendida_en = ?, atendida_por = ?"
                + " WHERE variante_id = ? AND tipo = 'SIN_ROTACION' AND estado = 'ACTIVA'", hace(diasAtras), usuario,
                variante);
    }

    private List<String> activas(Long variante) {
        return jdbc.queryForList("SELECT tipo FROM alerta WHERE variante_id = ? AND estado = 'ACTIVA' ORDER BY tipo",
                String.class, variante);
    }

    @Test
    void evaluar_productoSinVentasEn60Dias_abreSinRotacionSoloEnLasQueTienenDisponible() {
        Long p = producto(100);
        Long conStock = variante(p, "M", 10, 0, 2);
        Long agotada = variante(p, "L", 0, 0, 2);
        movimiento(conStock, "VENTA", 90, 1);

        alertas.evaluar(conStock);
        alertas.evaluar(agotada);

        assertThat(activas(conStock)).containsExactly("SIN_ROTACION");
        assertThat(activas(agotada)).containsExactly("AGOTADO");
        assertThat(alertas.tipoEsperado(conStock)).isEqualTo(TipoAlerta.SIN_ROTACION);
        assertThat(alertas.tipoEsperado(agotada)).isEqualTo(TipoAlerta.AGOTADO);
    }

    @Test
    void evaluar_ventaDeUnaVariante_cierraLaSinRotacionDeTodasLasDelProducto() {
        Long p = producto(100);
        Long a = variante(p, "M", 10, 0, 2);
        Long b = variante(p, "L", 10, 0, 2);
        alertas.evaluar(a);
        alertas.evaluar(b);
        assertThat(activas(b)).containsExactly("SIN_ROTACION");

        movimiento(a, "VENTA", 1, 7); // venta reciente de la variante A
        alertas.evaluar(a);

        assertThat(activas(a)).isEmpty();
        assertThat(activas(b)).isEmpty();
        assertThat(alertas.tipoEsperado(b)).isNull();
    }

    @Test
    void evaluar_sinRotacionGanaSobreStockBajo() {
        Long p = producto(100);
        Long v = variante(p, "M", 2, 0, 5); // disponible 2 <= 5: sería STOCK_BAJO

        alertas.evaluar(v);

        assertThat(activas(v)).containsExactly("SIN_ROTACION");
    }

    @Test
    void evaluar_cambioDeAgotadoASinRotacion_cierraAgotadaYAbreLaNueva() {
        Long p = producto(100);
        Long v = variante(p, "M", 0, 0, 2);
        alertas.evaluar(v);
        assertThat(activas(v)).containsExactly("AGOTADO");

        jdbc.update("UPDATE variante SET stock_fisico = 10 WHERE id = ?", v);
        alertas.evaluar(v);

        assertThat(activas(v)).containsExactly("SIN_ROTACION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alerta WHERE variante_id = ? AND tipo = 'AGOTADO'"
                + " AND estado = 'ATENDIDA' AND atendida_por IS NULL", Integer.class, v)).isEqualTo(1);
    }

    @Test
    void evaluar_ventaAnuladaNoCuentaComoRotacion() {
        Long p = producto(100);
        Long v = variante(p, "M", 10, 0, 2);
        movimiento(v, "VENTA", 5, 9);
        movimiento(v, "ANULACION_VENTA", 4, 9);

        alertas.evaluar(v);

        assertThat(activas(v)).containsExactly("SIN_ROTACION");
    }

    @Test
    void evaluar_ventaReservaVigenteNoAnulada_cuentaComoRotacion() {
        Long p = producto(100);
        Long v = variante(p, "M", 10, 0, 2);
        movimiento(v, "VENTA_RESERVA", 5, 11);

        alertas.evaluar(v);

        assertThat(activas(v)).isEmpty();
    }

    @Test
    void evaluar_productoDeMenosDe60Dias_nuncaSinRotacion() {
        Long p = producto(30);
        Long v = variante(p, "M", 10, 0, 2);

        alertas.evaluar(v);

        assertThat(activas(v)).isEmpty();
        assertThat(alertas.tipoEsperado(v)).isNull();
    }

    @Test
    void evaluar_atendidaAManoEnElMismoEpisodio_noSeReabre() {
        Long p = producto(100);
        Long v = variante(p, "M", 10, 0, 2);
        movimiento(v, "VENTA", 70, 1); // el episodio empezó hace 10 días
        alertas.evaluar(v);
        assertThat(activas(v)).containsExactly("SIN_ROTACION");

        atenderAMano(v, 5); // atendida dentro del episodio
        alertas.evaluar(v);

        assertThat(activas(v)).isEmpty();
        assertThat(alertas.tipoEsperado(v)).isNull();
    }

    @Test
    void evaluar_atendidaAManoEnUnEpisodioAnterior_seReabreTrasOtraVentaY60Dias() {
        Long p = producto(200);
        Long v = variante(p, "M", 10, 0, 2);
        movimiento(v, "VENTA", 120, 1);
        alertas.evaluar(v);
        atenderAMano(v, 100); // atendida en el episodio anterior (el actual empieza con la venta siguiente)
        movimiento(v, "VENTA", 70, 2); // nueva venta: el episodio actual empezó hace 10 días

        alertas.evaluar(v);

        assertThat(activas(v)).containsExactly("SIN_ROTACION");
    }

    @Test
    void evaluar_varianteInactivaConAlertasActivas_cierraTodas() {
        Long p = producto(100);
        Long v = variante(p, "M", 10, 0, 2);
        alertas.evaluar(v);
        assertThat(activas(v)).containsExactly("SIN_ROTACION");

        jdbc.update("UPDATE variante SET activo = 0 WHERE id = ?", v);
        alertas.evaluar(v);

        assertThat(activas(v)).isEmpty();
        assertThat(alertas.tipoEsperado(v)).isNull();
    }

    @Test
    void evaluar_productoInactivo_cierraLasAlertasDeTodasSusVariantes() {
        Long p = producto(100);
        Long a = variante(p, "M", 10, 0, 2);
        Long b = variante(p, "L", 0, 0, 2);
        alertas.evaluar(a);
        alertas.evaluar(b);

        jdbc.update("UPDATE producto SET activo = 0 WHERE id = ?", p);
        alertas.evaluar(a);
        alertas.evaluar(b);

        assertThat(activas(a)).isEmpty();
        assertThat(activas(b)).isEmpty();
    }

    @Test
    void evaluar_ventaAnuladaConOtraVentaRegistradaEnOtraVariante_cuentaComoRotacion() {
        Long p = producto(100);
        Long a = variante(p, "M", 10, 0, 2);
        Long b = variante(p, "L", 10, 0, 2);
        movimiento(a, "VENTA", 5, 9);
        movimiento(a, "ANULACION_VENTA", 4, 9); // la venta 9 se anuló
        movimiento(b, "VENTA", 3, 10); // la venta 10 sigue registrada, en otra variante

        alertas.evaluar(a);
        alertas.evaluar(b);

        assertThat(activas(a)).isEmpty();
        assertThat(activas(b)).isEmpty();
    }

    @Test
    void evaluar_ventaAnuladaYOtraVentaRegistradaDeLaMismaVariante_cuentaComoRotacion() {
        Long p = producto(100);
        Long v = variante(p, "M", 10, 0, 2);
        movimiento(v, "VENTA", 5, 9);
        movimiento(v, "ANULACION_VENTA", 4, 9);
        movimiento(v, "VENTA", 3, 10); // otra referencia, no anulada

        alertas.evaluar(v);

        assertThat(activas(v)).isEmpty();
    }

    @Test
    void evaluar_productoConExactamente60Dias_yaPuedeAbrirSinRotacion() {
        Long p = producto(60);
        Long v = variante(p, "M", 10, 0, 2);

        alertas.evaluar(v);

        assertThat(activas(v)).containsExactly("SIN_ROTACION");
    }

    @Test
    void evaluar_productoConMenosDe60DiasPorUnDia_noAbreSinRotacion() {
        Long p = producto(59);
        Long v = variante(p, "M", 10, 0, 2);

        alertas.evaluar(v);

        assertThat(activas(v)).isEmpty();
    }

    @Test
    void evaluar_ultimaVentaHace59Dias_cuentaComoRotacionYHace61NoCuenta() {
        Long reciente = producto(200);
        Long vReciente = variante(reciente, "M", 10, 0, 2);
        movimiento(vReciente, "VENTA", 59, 1);
        Long antigua = producto(200);
        Long vAntigua = variante(antigua, "M", 10, 0, 2);
        movimiento(vAntigua, "VENTA", 61, 2);

        alertas.evaluar(vReciente);
        alertas.evaluar(vAntigua);

        assertThat(activas(vReciente)).isEmpty();
        assertThat(activas(vAntigua)).containsExactly("SIN_ROTACION");
    }

    @Test
    void evaluar_todoElStockReservado_esAgotadoYNoSinRotacion() {
        Long p = producto(100);
        Long v = variante(p, "M", 5, 5, 2); // disponible 0

        alertas.evaluar(v);

        assertThat(activas(v)).containsExactly("AGOTADO");
        assertThat(alertas.tipoEsperado(v)).isEqualTo(TipoAlerta.AGOTADO);
    }

    @Test
    void evaluar_sinRotacionAtendidaAManoEnElEpisodio_noImpideStockBajoNiAgotado() {
        Long p = producto(100);
        Long v = variante(p, "M", 10, 0, 5);
        alertas.evaluar(v);
        atenderAMano(v, 1);
        alertas.evaluar(v);
        assertThat(activas(v)).isEmpty();

        jdbc.update("UPDATE variante SET stock_fisico = 3 WHERE id = ?", v); // disponible 3 <= 5
        alertas.evaluar(v);
        assertThat(activas(v)).containsExactly("STOCK_BAJO");

        jdbc.update("UPDATE variante SET stock_fisico = 0 WHERE id = ?", v);
        alertas.evaluar(v);
        assertThat(activas(v)).containsExactly("AGOTADO");
    }

    @Test
    void evaluar_varianteInactivaSinAlertas_noLanzaNiCreaNada() {
        Long p = producto(100);
        Long v = variante(p, "M", 10, 0, 2);
        jdbc.update("UPDATE variante SET activo = 0 WHERE id = ?", v);

        alertas.evaluar(v);
        alertas.evaluar(v); // el listener de desactivación puede llegar dos veces

        assertThat(activas(v)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alerta WHERE variante_id = ?", Integer.class, v))
                .isZero();
    }

    @Test
    void consultaUltimaVenta_usaElIndiceDeKardexPorVarianteTipoFecha() {
        Long p = producto(100);
        Long v = variante(p, "M", 10, 0, 2);
        movimiento(v, "VENTA", 5, 1);

        List<java.util.Map<String, Object>> plan = jdbc.queryForList(
                "EXPLAIN SELECT MAX(fecha) FROM movimiento WHERE variante_id IN (?) AND tipo IN ('VENTA',"
                        + " 'VENTA_RESERVA') AND fecha >= ?", v, hace(60));

        assertThat(String.valueOf(plan.get(0).get("possible_keys"))).contains("idx_movimiento_variante_tipo_fecha");
    }

    @Test
    void resumen_incluyeSinRotacion() {
        Long p = producto(100);
        Long v = variante(p, "M", 10, 0, 2);
        long antes = alertas.resumen().sinRotacion();

        alertas.evaluar(v);

        assertThat(alertas.resumen().sinRotacion()).isEqualTo(antes + 1);
    }

    @Test
    void desactivarVarianteYProducto_cierraAlertasTrasElCommit() throws Exception {
        Long p = producto(100);
        Long a = variante(p, "M", 10, 0, 2);
        Long b = variante(p, "L", 10, 0, 2);
        alertas.evaluar(a);
        alertas.evaluar(b);
        Long usuario = jdbc.queryForObject("SELECT id FROM usuario ORDER BY id LIMIT 1", Long.class);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new UsuarioAutenticado(usuario, "admin@test.co", "Admin", "ADMIN"), "n/a", List.of()));

        variantesServicio.cambiarEstado(a, false);
        assertThat(esperarSinActivas(a)).isTrue();
        assertThat(activas(b)).containsExactly("SIN_ROTACION");

        productos.cambiarEstado(p, false);
        assertThat(esperarSinActivas(b)).isTrue();
    }

    private boolean esperarSinActivas(Long variante) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (activas(variante).isEmpty()) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }
}
