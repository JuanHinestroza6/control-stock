package co.livorno.controlstock.compra.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.compra.dto.AprobarPlanRequest;
import co.livorno.controlstock.compra.dto.CrearPlanRequest;
import co.livorno.controlstock.compra.dto.LineaPlanRequest;
import co.livorno.controlstock.compra.dto.LineaPlanResponse;
import co.livorno.controlstock.compra.dto.PlanCompraDetalleResponse;
import co.livorno.controlstock.compra.dto.SugerirPlanRequest;
import co.livorno.controlstock.compra.modelo.EstadoPlan;
import co.livorno.controlstock.compra.modelo.OrigenPlan;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * B9, fase 6: plan sugerido (ADR-010, decisión 6) sobre datos con ROP y pronóstico guardado. Cada prueba trabaja en su
 * propia categoría para no depender de otras variantes de la base.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class SugerenciaCompraIntegracionTest {

    @Autowired PlanCompraService servicio;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private final List<Long> productos = new ArrayList<>();
    private final List<Long> idsVariantes = new ArrayList<>();
    private final List<Long> categorias = new ArrayList<>();
    private String sufijo;
    private Long usuarioId;
    private Long ejecucionId;
    private Long cat1;
    private Long cat2;

    @BeforeEach
    void preparar() {
        sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "sg" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Compradora', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        cat1 = categoria("C1");
        cat2 = categoria("C2");
        // Ejecución exitosa más reciente que cualquier otra de la base.
        jdbc.update("INSERT INTO ejecucion_analitica (origen, iniciada_en, terminada_en, estado, fecha_corte)"
                + " VALUES ('NOCTURNA', ?, ?, 'EXITOSA', ?)", LocalDateTime.now(clock).plusYears(1),
                LocalDateTime.now(clock).plusYears(1).plusMinutes(1), LocalDate.now(clock));
        ejecucionId = jdbc.queryForObject("SELECT MAX(id) FROM ejecucion_analitica", Long.class);
        como();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM detalle_plan WHERE plan_id IN (SELECT id FROM plan_compra WHERE creado_por = ?)",
                usuarioId);
        jdbc.update("DELETE FROM plan_compra WHERE creado_por = ?", usuarioId);
        for (Long v : idsVariantes) {
            jdbc.update("DELETE FROM pronostico WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM alerta WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM variante WHERE id = ?", v);
        }
        for (Long p : productos) {
            jdbc.update("DELETE FROM pronostico WHERE producto_id = ?", p);
            jdbc.update("DELETE FROM producto WHERE id = ?", p);
        }
        jdbc.update("DELETE FROM ejecucion_analitica WHERE id = ?", ejecucionId);
        for (Long c : categorias) {
            jdbc.update("DELETE FROM categoria WHERE id = ?", c);
        }
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private void como() {
        var principal = new UsuarioAutenticado(usuarioId, "sg@livorno.test", "Compradora", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private Long categoria(String marca) {
        String nombre = marca + sufijo;
        jdbc.update("INSERT INTO categoria (nombre, creado_en, actualizado_en) VALUES (?, NOW(6), NOW(6))", nombre);
        Long id = jdbc.queryForObject("SELECT id FROM categoria WHERE nombre = ?", Long.class, nombre);
        categorias.add(id);
        return id;
    }

    private Long producto(String marca, Long categoriaId, boolean activo, int lote) {
        String cod = marca + sufijo;
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, lead_time_dias,"
                + " lote_minimo, activo, creado_en, actualizado_en) VALUES (?, 'Prueba', ?, 20000.00, 100000.00, 10, ?,"
                + " ?, NOW(6), NOW(6))", cod, categoriaId, lote, activo);
        Long id = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, cod);
        productos.add(id);
        return id;
    }

    private Long variante(Long productoId, String talla, boolean activa, int disponible, Integer rop) {
        String sku = "X" + sufijo + "-" + productoId + "-" + talla;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, stock_minimo,"
                + " punto_reorden, activo, creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', ?, 0, 2, ?, ?, NOW(6),"
                + " NOW(6))", productoId, sku, talla, disponible, rop, activa);
        Long id = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
        idsVariantes.add(id);
        return id;
    }

    /** Variante activa talla M agotada con ROP 10 y un color propio (para crear muchas del mismo producto). */
    private Long varianteConColor(Long productoId, String color) {
        String sku = "X" + sufijo + "-" + productoId + "-" + color;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, stock_minimo,"
                + " punto_reorden, activo, creado_en, actualizado_en) VALUES (?, ?, 'M', ?, 0, 0, 2, 10, TRUE, NOW(6),"
                + " NOW(6))", productoId, sku, color);
        Long id = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
        idsVariantes.add(id);
        return id;
    }

    /** Pronóstico de 2 unidades por día en el mes en curso y los dos siguientes (cantidad = 2 × días del mes). */
    private void pronosticoDosPorDia(Long productoId, Long varianteId) {
        YearMonth mes = YearMonth.now(clock);
        for (int i = 0; i < 3; i++) {
            YearMonth m = mes.plusMonths(i);
            jdbc.update("INSERT INTO pronostico (producto_id, variante_id, periodo, cantidad_proyectada, metodo,"
                    + " calculado_en, ejecucion_id) VALUES (?, ?, ?, ?, 'SES', NOW(6), ?)", productoId, varianteId,
                    m.atDay(1), new BigDecimal(2 * m.lengthOfMonth()), ejecucionId);
        }
    }

    private PlanCompraDetalleResponse sugerir(Long categoriaId) {
        return servicio.crearSugerido(new SugerirPlanRequest(categoriaId, null));
    }

    private String sku(Long varianteId) {
        return jdbc.queryForObject("SELECT sku FROM variante WHERE id = ?", String.class, varianteId);
    }

    private static LineaPlanResponse linea(PlanCompraDetalleResponse plan, Long varianteId) {
        return plan.lineas().stream().filter(l -> l.varianteId().equals(varianteId)).findFirst().orElseThrow();
    }

    @Test
    void crearSugerido_conRopYPronostico_calculaCantidadConLoteYGuardaLaEjecucion() {
        // dado: A con pronóstico de 2/día, ROP 10, disponible 4, lote 4; B sin pronóstico, ROP 10, disponible 4, lote 5;
        // C con stock de sobra; D inactiva
        Long pA = producto("A", cat1, true, 4);
        Long pB = producto("B", cat1, true, 5);
        Long a = variante(pA, "S", true, 4, 10);
        Long b = variante(pB, "S", true, 4, 10);
        Long c = variante(pB, "M", true, 50, 10);
        Long d = variante(pB, "L", false, 0, 10);
        pronosticoDosPorDia(pA, a);

        // cuando
        PlanCompraDetalleResponse plan = sugerir(cat1);

        // entonces: A → S = 10 + 60 = 70; 70 − 4 = 66 → lote 4: 68. B → S = 10; 10 − 4 = 6 → lote 5: 10
        assertThat(plan.estado()).isEqualTo(EstadoPlan.BORRADOR);
        assertThat(plan.origen()).isEqualTo(OrigenPlan.SUGERIDO);
        assertThat(plan.omitidas()).isEmpty();
        assertThat(plan.lineas()).extracting(LineaPlanResponse::varianteId).containsExactlyInAnyOrder(a, b);
        LineaPlanResponse lineaA = linea(plan, a);
        assertThat(lineaA.cantidadSugerida()).isEqualTo(68);
        assertThat(lineaA.cantidadAprobada()).isEqualTo(68);
        assertThat(lineaA.costoEstimado()).isEqualByComparingTo("20000.00");
        assertThat(lineaA.justificacion()).startsWith("ROP 10 + demanda 30 d 60").contains("lote 4: 68");
        assertThat(linea(plan, b).cantidadAprobada()).isEqualTo(10);
        assertThat(linea(plan, b).justificacion()).contains("lote 5: 10");
        assertThat(plan.lineas()).extracting(LineaPlanResponse::varianteId).doesNotContain(c, d);
        Long guardada = jdbc.queryForObject("SELECT ejecucion_id FROM plan_compra WHERE id = ?", Long.class,
                plan.id());
        assertThat(guardada).isEqualTo(ejecucionId);
    }

    @Test
    void crearSugerido_conVarianteEnOtroBorrador_laOmiteYLaInforma() {
        // dado: dos candidatas y un primer plan sugerido que las toma
        Long p = producto("A", cat1, true, 1);
        Long a = variante(p, "S", true, 0, 10);
        Long b = variante(p, "M", true, 0, 10);
        sugerir(cat1);
        Long c = variante(p, "L", true, 0, 10);

        // cuando
        PlanCompraDetalleResponse segundo = sugerir(cat1);

        // entonces: solo entra la nueva; las dos primeras se informan por SKU
        assertThat(segundo.lineas()).extracting(LineaPlanResponse::varianteId).containsExactly(c);
        assertThat(segundo.omitidas()).containsExactlyInAnyOrder(sku(a), sku(b));
    }

    @Test
    void crearSugerido_conTodasLasCandidatasEnBorrador_lanza422ConLosSku() {
        // dado
        Long p = producto("A", cat1, true, 1);
        Long a = variante(p, "S", true, 0, 10);
        sugerir(cat1);

        // cuando / entonces
        assertThatThrownBy(() -> sugerir(cat1)).isInstanceOf(ExcepcionNegocio.class)
                .hasMessageContaining("No hay variantes por reponer").hasMessageContaining(sku(a));
    }

    @Test
    void crearSugerido_conCategoria_soloIncluyeVariantesDeEsaCategoria() {
        // dado: una candidata en cada categoría
        Long p1 = producto("A", cat1, true, 1);
        Long p2 = producto("B", cat2, true, 1);
        Long a = variante(p1, "S", true, 0, 10);
        Long b = variante(p2, "S", true, 0, 10);

        // cuando
        PlanCompraDetalleResponse plan = sugerir(cat2);

        // entonces
        assertThat(plan.lineas()).extracting(LineaPlanResponse::varianteId).containsExactly(b).doesNotContain(a);
    }

    @Test
    void crearSugerido_sinCandidatas_lanza422() {
        // dado: la única variante tiene stock de sobra
        Long p = producto("A", cat1, true, 1);
        variante(p, "S", true, 50, 10);

        // cuando / entonces
        assertThatThrownBy(() -> sugerir(cat1)).isInstanceOf(ExcepcionNegocio.class)
                .hasMessage("No hay variantes por reponer.");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_compra WHERE creado_por = ?", Integer.class,
                usuarioId)).isZero();
    }

    @Test
    void crearSugerido_conPedidoAprobadoEnCamino_yaNoEntra() {
        // dado: variante agotada con ROP 10 y un plan APROBADO de 100 unidades en camino
        Long p = producto("A", cat1, true, 1);
        Long a = variante(p, "S", true, 0, 10);
        Long b = variante(p, "M", true, 0, 10);
        PlanCompraDetalleResponse manual = servicio.crear(new CrearPlanRequest(null,
                List.of(new LineaPlanRequest(a, 100, null))));
        servicio.aprobar(manual.id(), new AprobarPlanRequest(manual.version()));

        // cuando
        PlanCompraDetalleResponse plan = sugerir(cat1);

        // entonces: posición de A = 0 + 100 > 10; solo B se sugiere
        assertThat(plan.lineas()).extracting(LineaPlanResponse::varianteId).containsExactly(b);
        assertThat(plan.omitidas()).isEmpty();
    }

    @Test
    void crearSugerido_conCantidadCalculadaMayorAlTope_laLimitaA100000() {
        // dado: lote de 200.000 → la calculadora sugiere 200.000
        Long p = producto("A", cat1, true, 200_000);
        Long a = variante(p, "S", true, 0, 10);

        // cuando
        PlanCompraDetalleResponse plan = sugerir(cat1);

        // entonces
        assertThat(linea(plan, a).cantidadAprobada()).isEqualTo(100_000);
        assertThat(linea(plan, a).cantidadSugerida()).isEqualTo(100_000);
    }

    /** Variante activa talla M con ROP 10, disponible y clase ABC dados (null = sin clase). */
    private Long varianteConClase(Long productoId, String color, int disponible, String clase) {
        String sku = "X" + sufijo + "-" + productoId + "-" + color;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, stock_minimo,"
                + " punto_reorden, clase_abc, activo, creado_en, actualizado_en) VALUES (?, ?, 'M', ?, ?, 0, 2, 10, ?,"
                + " TRUE, NOW(6), NOW(6))", productoId, sku, color, disponible, clase);
        Long id = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
        idsVariantes.add(id);
        return id;
    }

    @Test
    void crearSugerido_con301Candidatas_dejaFueraLaDeMenorPrioridadNoLaDeMayorId() {
        // dado: 301 candidatas; la de menor id es la única con disponible (no agotada), todas las demás están agotadas
        Long p = producto("A", cat1, true, 1);
        Long menorPrioridad = varianteConClase(p, "K0", 5, null);
        List<Long> agotadas = new ArrayList<>();
        for (int i = 1; i <= 300; i++) {
            agotadas.add(varianteConClase(p, "K" + i, 0, null));
        }

        // cuando
        PlanCompraDetalleResponse plan = sugerir(cat1);

        // entonces: queda fuera la de menor prioridad (id más bajo), no la de mayor id; se informa el exceso
        assertThat(plan.excluidasPorTope()).isEqualTo(1);
        assertThat(plan.lineas()).extracting(LineaPlanResponse::varianteId)
                .containsExactlyElementsOf(agotadas);
        assertThat(plan.lineas()).extracting(LineaPlanResponse::varianteId).doesNotContain(menorPrioridad);
    }

    @Test
    void crearSugerido_conMezclaDeClases_priorizaAgotadasYLuegoClaseAbc() {
        // dado: 303 candidatas: agotadas 100 A, 100 B, 50 C y 52 sin clase; más una A no agotada (disponible 5)
        Long p = producto("A", cat1, true, 1);
        List<Long> a = new ArrayList<>();
        List<Long> b = new ArrayList<>();
        List<Long> c = new ArrayList<>();
        List<Long> sin = new ArrayList<>();
        for (int i = 0; i < 52; i++) {
            sin.add(varianteConClase(p, "S" + i, 0, null));
        }
        for (int i = 0; i < 50; i++) {
            c.add(varianteConClase(p, "C" + i, 0, "C"));
        }
        for (int i = 0; i < 100; i++) {
            b.add(varianteConClase(p, "B" + i, 0, "B"));
        }
        for (int i = 0; i < 100; i++) {
            a.add(varianteConClase(p, "A" + i, 0, "A"));
        }
        Long aNoAgotada = varianteConClase(p, "ANA", 5, "A");

        // cuando
        PlanCompraDetalleResponse plan = sugerir(cat1);

        // entonces: entran las 250 con clase y las 50 primeras (por id) sin clase; quedan fuera las 2 últimas sin
        // clase y la A no agotada (agotadas primero); las líneas salen en orden de id de variante
        List<Long> esperadas = new ArrayList<>(sin.subList(0, 50));
        esperadas.addAll(c);
        esperadas.addAll(b);
        esperadas.addAll(a);
        assertThat(plan.excluidasPorTope()).isEqualTo(3);
        assertThat(plan.lineas()).extracting(LineaPlanResponse::varianteId).containsExactlyElementsOf(esperadas);
        assertThat(plan.lineas()).extracting(LineaPlanResponse::varianteId).doesNotContain(aNoAgotada,
                sin.get(50), sin.get(51));
    }

    @Test
    void crearSugerido_conMasDe300Candidatas_creaSolo300LineasConIgualPrioridadLasDeMenorId() {
        // dado: 305 candidatas
        Long p = producto("A", cat1, true, 1);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 305; i++) {
            ids.add(varianteConColor(p, "COL" + i));
        }

        // cuando
        PlanCompraDetalleResponse plan = sugerir(cat1);

        // entonces: con la misma prioridad se toman las 300 de menor id; las 5 restantes siguen siendo candidatas
        assertThat(plan.excluidasPorTope()).isEqualTo(5);
        assertThat(plan.lineas()).extracting(LineaPlanResponse::varianteId)
                .containsExactlyInAnyOrderElementsOf(ids.subList(0, 300));
        PlanCompraDetalleResponse segundo = sugerir(cat1);
        assertThat(segundo.lineas()).extracting(LineaPlanResponse::varianteId)
                .containsExactlyInAnyOrderElementsOf(ids.subList(300, 305));
    }

    @Test
    void crearSugerido_sinEjecucionExitosa_usaStockMinimoYGuardaEjecucionNula() {
        // dado: ninguna ejecución EXITOSA (se restauran al terminar); variante sin ROP, stockMinimo 2, disponible 1
        List<Long> exitosas = jdbc.queryForList("SELECT id FROM ejecucion_analitica WHERE estado = 'EXITOSA'",
                Long.class);
        jdbc.update("UPDATE ejecucion_analitica SET estado = 'FALLIDA' WHERE estado = 'EXITOSA'");
        try {
            Long p = producto("A", cat1, true, 1);
            Long a = variante(p, "S", true, 1, null);

            // cuando
            PlanCompraDetalleResponse plan = sugerir(cat1);

            // entonces: sin ROP ni demanda → S = 2 × 2 = 4; 4 − 1 = 3
            assertThat(linea(plan, a).cantidadAprobada()).isEqualTo(3);
            assertThat(linea(plan, a).justificacion()).isEqualTo(
                    "Sin ROP ni demanda: 2 × stock mínimo 2 − disponible 1 − en camino 0 = 3");
            assertThat(jdbc.queryForObject("SELECT ejecucion_id FROM plan_compra WHERE id = ?", Long.class,
                    plan.id())).isNull();
        } finally {
            for (Long id : exitosas) {
                jdbc.update("UPDATE ejecucion_analitica SET estado = 'EXITOSA' WHERE id = ?", id);
            }
        }
    }

    @Test
    void crearSugerido_justificacionGuardada_coincideConLaDeLaCalculadora() {
        // dado: pronóstico de 2/día, ROP 10, disponible 4, lote 4
        Long p = producto("A", cat1, true, 4);
        Long a = variante(p, "S", true, 4, 10);
        pronosticoDosPorDia(p, a);

        // cuando
        PlanCompraDetalleResponse plan = sugerir(cat1);

        // entonces: texto exacto en la respuesta y en la base
        String esperado = "ROP 10 + demanda 30 d 60 − disponible 4 − en camino 0 = 66 → lote 4: 68";
        assertThat(linea(plan, a).justificacion()).isEqualTo(esperado);
        assertThat(jdbc.queryForObject("SELECT justificacion FROM detalle_plan WHERE plan_id = ? AND variante_id = ?",
                String.class, plan.id(), a)).isEqualTo(esperado);
    }

    @Test
    void crearSugerido_conEjecucionFallidaPosterior_usaLaUltimaExitosa() {
        // dado: una ejecución FALLIDA más reciente que la EXITOSA
        jdbc.update("INSERT INTO ejecucion_analitica (origen, iniciada_en, terminada_en, estado, fecha_corte)"
                + " VALUES ('NOCTURNA', ?, ?, 'FALLIDA', ?)", LocalDateTime.now(clock).plusYears(2),
                LocalDateTime.now(clock).plusYears(2).plusMinutes(1), LocalDate.now(clock));
        Long fallida = jdbc.queryForObject("SELECT MAX(id) FROM ejecucion_analitica", Long.class);
        try {
            Long p = producto("A", cat1, true, 1);
            variante(p, "S", true, 0, 10);

            // cuando
            PlanCompraDetalleResponse plan = sugerir(cat1);

            // entonces
            assertThat(jdbc.queryForObject("SELECT ejecucion_id FROM plan_compra WHERE id = ?", Long.class,
                    plan.id())).isEqualTo(ejecucionId).isNotEqualTo(fallida);
        } finally {
            jdbc.update("DELETE FROM detalle_plan WHERE plan_id IN (SELECT id FROM plan_compra WHERE creado_por = ?)",
                    usuarioId);
            jdbc.update("DELETE FROM plan_compra WHERE creado_por = ?", usuarioId);
            jdbc.update("DELETE FROM ejecucion_analitica WHERE id = ?", fallida);
        }
    }

    @Test
    void crearSugerido_conProductoInactivoYVarianteActiva_noLaIncluye() {
        // dado
        Long inactivo = producto("A", cat1, false, 1);
        Long a = variante(inactivo, "S", true, 0, 10);
        Long activo = producto("B", cat1, true, 1);
        Long b = variante(activo, "S", true, 0, 10);

        // cuando
        PlanCompraDetalleResponse plan = sugerir(cat1);

        // entonces
        assertThat(plan.lineas()).extracting(LineaPlanResponse::varianteId).containsExactly(b).doesNotContain(a);
    }

    @Test
    void crearSugerido_conObservacion_laGuardaYNoTocaStock() {
        // dado
        Long p = producto("A", cat1, true, 1);
        Long a = variante(p, "S", true, 3, 10);

        // cuando
        PlanCompraDetalleResponse plan = servicio.crearSugerido(new SugerirPlanRequest(cat1, "  Pedido de prueba  "));

        // entonces: S = 10, 10 − 3 = 7; stock intacto
        assertThat(plan.observacion()).isEqualTo("Pedido de prueba");
        assertThat(linea(plan, a).cantidadAprobada()).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT stock_fisico FROM variante WHERE id = ?", Integer.class, a))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE variante_id = ?", Integer.class, a))
                .isZero();
    }
}
