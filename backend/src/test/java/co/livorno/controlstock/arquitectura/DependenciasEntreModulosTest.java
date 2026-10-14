package co.livorno.controlstock.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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

    @ArchTest
    static final ArchRule componentesDeSemillaLlevanPerfilSeed = classes()
            .that(new DescribedPredicate<JavaClass>("de semilla anotadas con @Component, @Configuration o @Service") {
                @Override
                public boolean test(JavaClass c) {
                    return c.getPackageName().startsWith("co.livorno.controlstock.semilla")
                            && (c.isAnnotatedWith(Component.class) || c.isAnnotatedWith(Configuration.class)
                            || c.isAnnotatedWith(Service.class));
                }
            })
            .should(new ArchCondition<JavaClass>("llevar @Profile(\"seed\")") {
                @Override
                public void check(JavaClass clase, ConditionEvents eventos) {
                    boolean ok = clase.tryGetAnnotationOfType(Profile.class)
                            .map(a -> java.util.Arrays.equals(a.value(), new String[] {"seed"})).orElse(false);
                    if (!ok) {
                        eventos.add(SimpleConditionEvent.violated(clase,
                                clase.getName() + " no lleva @Profile(\"seed\")"));
                    }
                }
            })
            .because("ningún bean de la semilla puede cargarse fuera del perfil seed (ADR-008)");

    @ArchTest
    static final ArchRule semillaNoEscribeConJdbcTemplate = noClasses()
            .that().resideInAPackage("co.livorno.controlstock.semilla..")
            .should().callMethodWhere(new DescribedPredicate<JavaCall<?>>("invoca JdbcTemplate.update/batchUpdate/execute") {
                @Override
                public boolean test(JavaCall<?> llamada) {
                    return llamada.getTargetOwner().isAssignableTo(org.springframework.jdbc.core.JdbcTemplate.class)
                            && java.util.Set.of("update", "batchUpdate", "execute").contains(llamada.getName());
                }
            })
            .because("el stock y el kardex solo cambian por los servicios; la semilla solo lee con JDBC (ADR-008)");
}
