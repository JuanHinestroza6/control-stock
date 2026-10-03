package co.livorno.controlstock.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import co.livorno.controlstock.catalogo.modelo.Variante;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.hibernate.Session;
import org.springframework.data.jpa.repository.Modifying;

/**
 * Reglas de oro 1 y 2 (ADR-006) sobre código de producción, incluidas las migraciones Java ({@code db.migration}):
 * solo MovimientoService cambia stock y el kardex nunca se actualiza ni se borra.
 */
@AnalyzeClasses(packages = {"co.livorno.controlstock", "db.migration"},
        importOptions = ImportOption.DoNotIncludeTests.class)
class ReglasArquitecturaTest {

    private static final String MOVIMIENTO_SERVICE = "co.livorno.controlstock.inventario.servicio.MovimientoService";

    /** Cualquier acceso (llamada, sobrecarga o referencia de método) a Variante.aplicarMovimiento. */
    private static final DescribedPredicate<JavaAccess<?>> APLICAR_MOVIMIENTO =
            DescribedPredicate.describe("Variante.aplicarMovimiento (cualquier sobrecarga)",
                    a -> a.getTarget().getName().equals("aplicarMovimiento")
                            && a.getTarget().getOwner().isAssignableTo(Variante.class));

    @ArchTest
    static final ArchRule soloMovimientoServiceAplicaMovimientos = noClasses()
            .that().doNotHaveFullyQualifiedName(MOVIMIENTO_SERVICE)
            .should().accessTargetWhere(APLICAR_MOVIMIENTO)
            .because("el stock solo cambia a través de MovimientoService (regla de oro 1)");

    /** Si se renombra aplicarMovimiento, la regla anterior pasaría en vacío: esta lo detecta. */
    @ArchTest
    static final ArchRule movimientoServiceSiAplicaMovimientos = classes()
            .that().haveFullyQualifiedName(MOVIMIENTO_SERVICE)
            .should().accessTargetWhere(APLICAR_MOVIMIENTO)
            .because("MovimientoService debe seguir siendo el único que invoca Variante.aplicarMovimiento");

    /** Los campos de stock solo se escriben en el constructor de Variante y en aplicarMovimiento. */
    @ArchTest
    static final ArchRule camposDeStockSoloSeEscribenEnVariante = codeUnits()
            .should(new ArchCondition<JavaCodeUnit>("no escribir stockFisico/stockReservado de Variante salvo en"
                    + " su constructor y aplicarMovimiento") {
                private static final Set<String> CAMPOS = Set.of("stockFisico", "stockReservado");

                @Override
                public void check(JavaCodeUnit unidad, ConditionEvents eventos) {
                    for (JavaFieldAccess acceso : unidad.getFieldAccesses()) {
                        boolean esCampoDeStock = acceso.getAccessType() == JavaFieldAccess.AccessType.SET
                                && CAMPOS.contains(acceso.getName())
                                && acceso.getTarget().getOwner().isAssignableTo(Variante.class);
                        if (esCampoDeStock && !permitido(unidad)) {
                            eventos.add(SimpleConditionEvent.violated(acceso, acceso.getDescription()));
                        }
                    }
                }

                private boolean permitido(JavaCodeUnit unidad) {
                    JavaClass duenio = unidad.getOwner();
                    return duenio.isEquivalentTo(Variante.class)
                            && (unidad.isConstructor() || unidad.getName().equals("aplicarMovimiento"));
                }
            })
            .because("el stock solo cambia a través de MovimientoService (regla de oro 1)");

    @ArchTest
    static final ArchRule repositorioDelKardexNoActualizaEnMasa = noMethods()
            .that().areDeclaredIn(co.livorno.controlstock.inventario.repositorio.MovimientoRepository.class)
            .should().beAnnotatedWith(Modifying.class)
            .because("el kardex es inmutable: sin UPDATE/DELETE masivos (regla de oro 2)");

    @ArchTest
    static final ArchRule nadieBorraEntidadesConEntityManager = noClasses()
            .should().callMethodWhere(DescribedPredicate.describe("EntityManager.remove o Session.remove/delete",
                    c -> (c.getTargetOwner().isAssignableTo(EntityManager.class) && c.getName().equals("remove"))
                            || (c.getTargetOwner().isAssignableTo(Session.class)
                                    && Set.of("remove", "delete").contains(c.getName()))))
            .because("ninguna entidad se borra por EntityManager; el kardex (Movimiento) nunca se borra (regla de oro 2)");
}
