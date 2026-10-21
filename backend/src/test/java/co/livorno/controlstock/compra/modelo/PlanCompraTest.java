package co.livorno.controlstock.compra.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.comun.excepcion.ConflictoException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Máquina de estados y reglas de líneas de PlanCompra, sin base de datos (ADR-010). */
class PlanCompraTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 2, 10, 0);

    private PlanCompra borrador(long id) {
        PlanCompra p = PlanCompra.manual("x", T0, 1L);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private PlanCompra aprobado(long id) {
        PlanCompra p = borrador(id);
        p.aprobar(T0, 1L);
        return p;
    }

    private PlanCompra recibido(long id) {
        PlanCompra p = aprobado(id);
        p.recibir(T0, 1L);
        return p;
    }

    private PlanCompra cancelado(long id) {
        PlanCompra p = borrador(id);
        p.cancelar("motivo valido", T0, 1L);
        return p;
    }

    private void conflicto(Runnable accion, String estado) {
        assertThatThrownBy(accion::run).isInstanceOf(ConflictoException.class)
                .hasMessage("El plan PC-000012 ya está " + estado + ".");
    }

    @Test
    void numero_conIdsDeVariosDigitos_rellenaACeroHastaSeis() {
        assertThat(borrador(12).numero()).isEqualTo("PC-000012");
        assertThat(borrador(1).numero()).isEqualTo("PC-000001");
        assertThat(borrador(123456).numero()).isEqualTo("PC-123456");
        assertThat(borrador(1234567).numero()).isEqualTo("PC-1234567");
        assertThat(PlanCompra.numero(9L)).isEqualTo("PC-000009");
    }

    @Test
    void aprobar_desdeCualquierEstadoQueNoSeaBorrador_lanzaConflicto() {
        conflicto(() -> aprobado(12).aprobar(T0, 1L), "APROBADO");
        conflicto(() -> recibido(12).aprobar(T0, 1L), "RECIBIDO");
        conflicto(() -> cancelado(12).aprobar(T0, 1L), "CANCELADO");
    }

    @Test
    void recibir_desdeCualquierEstadoQueNoSeaAprobado_lanzaConflicto() {
        conflicto(() -> borrador(12).recibir(T0, 1L), "BORRADOR");
        conflicto(() -> recibido(12).recibir(T0, 1L), "RECIBIDO");
        conflicto(() -> cancelado(12).recibir(T0, 1L), "CANCELADO");
    }

    @Test
    void cancelar_desdeEstadosFinales_lanzaConflictoYNoCambiaNada() {
        PlanCompra r = recibido(12);
        conflicto(() -> r.cancelar("motivo valido", T0, 1L), "RECIBIDO");
        assertThat(r.getEstado()).isEqualTo(EstadoPlan.RECIBIDO);
        assertThat(r.getMotivoCancelacion()).isNull();
        conflicto(() -> cancelado(12).cancelar("otro motivo", T0, 1L), "CANCELADO");
    }

    @Test
    void cancelar_desdeBorradorYAprobado_registraMotivoYUsuario() {
        PlanCompra a = aprobado(12);
        a.cancelar("Proveedor sin stock", T0.plusDays(1), 7L);

        assertThat(a.getEstado()).isEqualTo(EstadoPlan.CANCELADO);
        assertThat(a.getMotivoCancelacion()).isEqualTo("Proveedor sin stock");
        assertThat(a.getCanceladoPor()).isEqualTo(7L);
        assertThat(cancelado(13).getEstado()).isEqualTo(EstadoPlan.CANCELADO);
    }

    @Test
    void edicion_fueraDeBorrador_lanzaConflicto() {
        conflicto(() -> aprobado(12).vaciarLineas(T0), "APROBADO");
        conflicto(() -> aprobado(12).actualizarObservacion("y", T0), "APROBADO");
        conflicto(() -> aprobado(12).agregarLinea(null, null, 1, BigDecimal.ONE, null), "APROBADO");
        conflicto(() -> cancelado(12).vaciarLineas(T0), "CANCELADO");
    }

    @Test
    void agregarLinea_cantidadFueraDeRango_seRechaza() {
        PlanCompra p = borrador(12);

        assertThatThrownBy(() -> p.agregarLinea(null, null, 0, BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.agregarLinea(null, null, 100_001, BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.agregarLinea(null, null, 1, new BigDecimal("-0.01"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(p.getLineas()).isEmpty();

        p.agregarLinea(null, null, 1, BigDecimal.ZERO, null);
        p.agregarLinea(null, null, 100_000, new BigDecimal("10.005"), null);
        assertThat(p.getLineas()).hasSize(2);
        assertThat(p.getLineas().get(1).getCostoEstimado()).isEqualByComparingTo("10.01");
    }

    @Test
    void registrarRecepcion_cantidadFueraDeRango_seRechazaYAceptaLosLimites() {
        DetallePlan d = borrador(12).agregarLinea(null, null, 5, BigDecimal.ONE, null);

        assertThatThrownBy(() -> d.registrarRecepcion(-1, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> d.registrarRecepcion(100_001, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> d.registrarRecepcion(1, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> d.registrarRecepcion(1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(d.getCantidadRecibida()).isNull();

        d.registrarRecepcion(0, BigDecimal.ZERO);
        assertThat(d.getCantidadRecibida()).isZero();
        d.registrarRecepcion(100_000, new BigDecimal("1.5"));
        assertThat(d.getCostoRecibido()).isEqualByComparingTo("1.50");
    }
}
