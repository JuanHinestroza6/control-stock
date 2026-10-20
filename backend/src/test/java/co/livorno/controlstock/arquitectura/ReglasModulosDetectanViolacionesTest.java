package co.livorno.controlstock.arquitectura;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.compra.fixture.CompraQueUsaAnalitica;
import co.livorno.controlstock.compra.fixture.CompraQueUsaVenta;
import co.livorno.controlstock.compra.modelo.PlanCompra;
import co.livorno.controlstock.inventario.fixture.InventarioQueUsaCompra;
import co.livorno.controlstock.venta.modelo.Venta;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** Comprueba que las reglas de compra (ADR-010) fallan de verdad ante una dependencia prohibida. */
class ReglasModulosDetectanViolacionesTest {

    @Test
    void compraNoDependeDeVentaNiAnalitica_compraUsaVenta_reportaViolacion() {
        var clases = new ClassFileImporter().importClasses(CompraQueUsaVenta.class, Venta.class);

        assertThat(DependenciasEntreModulosTest.compraNoDependeDeVentaNiAnalitica.evaluate(clases).hasViolation())
                .isTrue();
    }

    @Test
    void compraNoDependeDeVentaNiAnalitica_compraUsaAnalitica_reportaViolacion() {
        var clases = new ClassFileImporter().importClasses(CompraQueUsaAnalitica.class, EjecucionAnalitica.class);

        assertThat(DependenciasEntreModulosTest.compraNoDependeDeVentaNiAnalitica.evaluate(clases).hasViolation())
                .isTrue();
    }

    @Test
    void modulosBaseNoDependenDeCompra_inventarioUsaCompra_reportaViolacion() {
        var clases = new ClassFileImporter().importClasses(InventarioQueUsaCompra.class, PlanCompra.class);

        assertThat(DependenciasEntreModulosTest.modulosBaseNoDependenDeCompra.evaluate(clases).hasViolation())
                .isTrue();
    }

    @Test
    void reglasDeCompra_sinDependenciasProhibidas_noReportanViolacion() {
        var clases = new ClassFileImporter().importClasses(PlanCompra.class,
                co.livorno.controlstock.inventario.servicio.ConsultaPedidosEnCamino.class);

        assertThat(DependenciasEntreModulosTest.compraNoDependeDeVentaNiAnalitica.evaluate(clases).hasViolation())
                .isFalse();
        assertThat(DependenciasEntreModulosTest.modulosBaseNoDependenDeCompra.evaluate(clases).hasViolation())
                .isFalse();
    }
}
