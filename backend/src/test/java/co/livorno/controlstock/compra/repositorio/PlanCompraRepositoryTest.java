package co.livorno.controlstock.compra.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.compra.modelo.EstadoPlan;
import co.livorno.controlstock.compra.modelo.OrigenPlan;
import co.livorno.controlstock.compra.modelo.PlanCompra;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.compra.servicio.PedidosEnCaminoJpa;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V1 a V16 con validate, CHECKs, unicidad, versión y consulta de en camino (ADR-010). */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class, PedidosEnCaminoJpa.class})
@ActiveProfiles("test")
class PlanCompraRepositoryTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 2, 10, 0);
    private static final String F = "2026-10-02 10:00:00";

    @Autowired PlanCompraRepository planes;
    @Autowired DetallePlanRepository detalles;
    @Autowired PedidosEnCaminoJpa enCamino;
    @Autowired VarianteRepository variantes;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    Variante v1;
    Variante v2;
    Usuario usuario;

    @BeforeEach
    void preparar() {
        Producto p = productos.saveAndFlush(new Producto("LIV001", "Camiseta",
                categorias.findAllByOrderByNombreAsc().get(0), new BigDecimal("20000"), new BigDecimal("59900"), null,
                null));
        v1 = variantes.saveAndFlush(new Variante(p, "LIV001-M-NEG", Talla.M, "NEGRO", 1));
        v2 = variantes.saveAndFlush(new Variante(p, "LIV001-L-NEG", Talla.L, "NEGRO", 1));
        usuario = usuarios.saveAndFlush(new Usuario("Admin", "adm@livorno.co", "hash", Rol.ADMIN));
    }

    private PlanCompra plan(Variante v, int cantidad) {
        PlanCompra p = PlanCompra.manual("Pedido", T0, usuario.getId());
        p.agregarLinea(v, null, cantidad, new BigDecimal("20000"), null);
        return planes.saveAndFlush(p);
    }

    private PlanCompra aprobado(Variante v, int cantidad) {
        PlanCompra p = plan(v, cantidad);
        p.aprobar(T0.plusHours(1), usuario.getId());
        return planes.saveAndFlush(p);
    }

    private int insertarPlan(String estado, String origen, String aprobadoEn, String recibidoEn, String canceladoEn,
            String motivo) {
        return jdbc.update("""
                insert into plan_compra (estado, origen, creado_en, creado_por, aprobado_en, recibido_en,
                    cancelado_en, motivo_cancelacion, version, actualizado_en)
                values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?)""", estado, origen, F, usuario.getId(), aprobadoEn, recibidoEn,
                canceladoEn, motivo, F);
    }

    private int insertarLinea(Long planId, Long varianteId, int aprobada, String costo, Integer recibida,
            String costoRecibido) {
        return jdbc.update("""
                insert into detalle_plan (plan_id, variante_id, cantidad_aprobada, costo_estimado,
                    cantidad_recibida, costo_recibido)
                values (?, ?, ?, ?, ?, ?)""", planId, varianteId, aprobada, costo, recibida, costoRecibido);
    }

    @Test
    void save_planConLineas_persisteYSeLeeConValidate() {
        PlanCompra guardado = plan(v1, 10);
        em.clear();

        PlanCompra leido = planes.findById(guardado.getId()).orElseThrow();

        assertThat(leido.getEstado()).isEqualTo(EstadoPlan.BORRADOR);
        assertThat(leido.getOrigen()).isEqualTo(OrigenPlan.MANUAL);
        assertThat(leido.getLineas()).hasSize(1);
        assertThat(leido.getLineas().get(0).getCantidadAprobada()).isEqualTo(10);
        assertThat(leido.getLineas().get(0).getCostoEstimado()).isEqualByComparingTo("20000.00");
        assertThat(leido.numero()).matches("PC-\\d{6}");
    }

    @Test
    void version_dosInstanciasDelMismoPlan_laSegundaEscrituraFalla() {
        PlanCompra guardado = plan(v1, 10);
        em.clear();
        PlanCompra copiaA = planes.findById(guardado.getId()).orElseThrow();
        em.detach(copiaA);
        PlanCompra copiaB = planes.findById(guardado.getId()).orElseThrow();
        copiaB.actualizarObservacion("B", T0.plusHours(1));
        planes.saveAndFlush(copiaB);

        copiaA.actualizarObservacion("A", T0.plusHours(2));

        assertThatThrownBy(() -> planes.saveAndFlush(copiaA)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void transiciones_estadoIncorrecto_lanzaConflicto() {
        PlanCompra p = aprobado(v1, 5);

        assertThatThrownBy(() -> p.aprobar(T0, usuario.getId()))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("ya está APROBADO");
        p.recibir(T0.plusDays(1), usuario.getId());
        assertThatThrownBy(() -> p.cancelar("motivo valido", T0, usuario.getId()))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void check_planesValidos_seAceptan() {
        assertThat(insertarPlan("BORRADOR", "MANUAL", null, null, null, null)).isEqualTo(1);
        assertThat(insertarPlan("APROBADO", "SUGERIDO", F, null, null, null)).isEqualTo(1);
        assertThat(insertarPlan("RECIBIDO", "MANUAL", F, F, null, null)).isEqualTo(1);
        assertThat(insertarPlan("CANCELADO", "MANUAL", null, null, F, "No llegó")).isEqualTo(1);
    }

    @Test
    void check_planesInvalidos_seRechazan() {
        assertThatThrownBy(() -> insertarPlan("OTRO", "MANUAL", null, null, null, null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertarPlan("BORRADOR", "AUTO", null, null, null, null))
                .isInstanceOf(DataAccessException.class);
        // APROBADO sin aprobado_en
        assertThatThrownBy(() -> insertarPlan("APROBADO", "MANUAL", null, null, null, null))
                .isInstanceOf(DataAccessException.class);
        // RECIBIDO sin recibido_en
        assertThatThrownBy(() -> insertarPlan("RECIBIDO", "MANUAL", F, null, null, null))
                .isInstanceOf(DataAccessException.class);
        // recibido_en en un APROBADO
        assertThatThrownBy(() -> insertarPlan("APROBADO", "MANUAL", F, F, null, null))
                .isInstanceOf(DataAccessException.class);
        // CANCELADO sin motivo
        assertThatThrownBy(() -> insertarPlan("CANCELADO", "MANUAL", null, null, F, null))
                .isInstanceOf(DataAccessException.class);
        // cancelado_en y motivo en un BORRADOR
        assertThatThrownBy(() -> insertarPlan("BORRADOR", "MANUAL", null, null, F, "x"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void check_lineas_cantidadesYCostosFueraDeRangoSeRechazan() {
        Long planId = planes.saveAndFlush(PlanCompra.manual(null, T0, usuario.getId())).getId();

        assertThat(insertarLinea(planId, v1.getId(), 100_000, "0", null, null)).isEqualTo(1);
        assertThatThrownBy(() -> insertarLinea(planId, v2.getId(), 0, "10", null, null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertarLinea(planId, v2.getId(), 100_001, "10", null, null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertarLinea(planId, v2.getId(), 1, "-1", null, null))
                .isInstanceOf(DataAccessException.class);
        // cantidad recibida sin costo recibido
        assertThatThrownBy(() -> insertarLinea(planId, v2.getId(), 1, "10", 1, null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertarLinea(planId, v2.getId(), 1, "10", -1, "10"))
                .isInstanceOf(DataAccessException.class);
        assertThat(insertarLinea(planId, v2.getId(), 1, "10", 0, "10")).isEqualTo(1);
    }

    @Test
    void unique_mismaVarianteDosVecesEnElPlan_seRechaza() {
        Long planId = plan(v1, 5).getId();

        assertThatThrownBy(() -> insertarLinea(planId, v1.getId(), 3, "10", null, null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void enCamino_soloSumaPlanesAprobados() {
        aprobado(v1, 10);
        aprobado(v1, 5);
        plan(v1, 100); // BORRADOR: no cuenta
        PlanCompra recibido = aprobado(v2, 7);
        recibido.recibir(T0.plusDays(2), usuario.getId());
        planes.saveAndFlush(recibido);
        PlanCompra cancelado = aprobado(v2, 9);
        cancelado.cancelar("Proveedor sin stock", T0.plusDays(1), usuario.getId());
        planes.saveAndFlush(cancelado);
        em.clear();

        assertThat(detalles.enCamino(v1.getId())).isEqualTo(15);
        assertThat(detalles.enCamino(v2.getId())).isZero();
        assertThat(enCamino.enCamino(v1.getId())).isEqualTo(15);
        assertThat(enCamino.enCaminoPorVariante()).containsOnlyKeys(v1.getId()).containsEntry(v1.getId(), 15L);
    }

    @Test
    void varianteIdsEnBorrador_devuelveSoloLasQueEstanEnBorrador() {
        plan(v1, 4);
        aprobado(v2, 4);

        assertThat(detalles.varianteIdsEnBorrador(List.of(v1.getId(), v2.getId()))).containsExactly(v1.getId());
    }

    @Test
    void bloquear_planExistenteEInexistente() {
        Long id = plan(v1, 1).getId();

        assertThat(planes.bloquear(id)).isPresent();
        assertThat(planes.bloquear(-1L)).isEmpty();
    }
}
