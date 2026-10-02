package co.livorno.controlstock.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import co.livorno.controlstock.catalogo.modelo.Variante;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Regla de oro 1 (ADR-006): solo MovimientoService invoca Variante.aplicarMovimiento (código de producción). */
@AnalyzeClasses(packages = "co.livorno.controlstock", importOptions = ImportOption.DoNotIncludeTests.class)
class ReglasArquitecturaTest {

    @ArchTest
    static final ArchRule soloMovimientoServiceAplicaMovimientos = noClasses()
            .that().doNotHaveFullyQualifiedName("co.livorno.controlstock.inventario.servicio.MovimientoService")
            .should().callMethod(Variante.class, "aplicarMovimiento", int.class, int.class)
            .because("el stock solo cambia a través de MovimientoService (regla de oro 1)")
            .allowEmptyShould(true);
}
