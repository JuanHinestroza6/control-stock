package co.livorno.controlstock.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Dirección de dependencias entre módulos (ADR-007): venta depende de inventario, inventario de catalogo. */
@AnalyzeClasses(packages = "co.livorno.controlstock", importOptions = ImportOption.DoNotIncludeTests.class)
class DependenciasEntreModulosTest {

    @ArchTest
    static final ArchRule catalogoNoDependeDeInventarioNiVenta = noClasses()
            .that().resideInAPackage("co.livorno.controlstock.catalogo..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "co.livorno.controlstock.inventario..", "co.livorno.controlstock.venta..")
            .because("catalogo consulta apartados por la interfaz ConsultaReservasActivas (ADR-007)");

    @ArchTest
    static final ArchRule inventarioNoDependeDeVenta = noClasses()
            .that().resideInAPackage("co.livorno.controlstock.inventario..")
            .should().dependOnClassesThat().resideInAPackage("co.livorno.controlstock.venta..")
            .because("venta ya depende de inventario; Reserva.ventaId es un Long (ADR-007)");

    @ArchTest
    static final ArchRule nadieFueraDeSemillaDependeDeSemilla = noClasses()
            .that().resideOutsideOfPackage("co.livorno.controlstock.semilla..")
            .should().dependOnClassesThat().resideInAPackage("co.livorno.controlstock.semilla..")
            .because("el generador de datos de demostración depende de los módulos, nunca al revés (ADR-008)");
}
