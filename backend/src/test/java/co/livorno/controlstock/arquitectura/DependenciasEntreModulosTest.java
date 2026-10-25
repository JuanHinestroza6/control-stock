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
    static final ArchRule modulosBaseNoDependenDeCompra = noClasses()
            .that().resideInAnyPackage("co.livorno.controlstock.catalogo..", "co.livorno.controlstock.inventario..",
                    "co.livorno.controlstock.venta..", "co.livorno.controlstock.usuario..")
            .should().dependOnClassesThat().resideInAPackage("co.livorno.controlstock.compra..")
            .because("inventario consulta lo que viene en camino por ConsultaPedidosEnCamino (ADR-010)");

    @ArchTest
    static final ArchRule compraNoDependeDeVentaNiAnalitica = noClasses()
            .that().resideInAPackage("co.livorno.controlstock.compra..")
            .should().dependOnClassesThat().resideInAnyPackage("co.livorno.controlstock.venta..",
                    "co.livorno.controlstock.analitica..")
            .because("compra pide la sugerencia por FuenteSugerenciaCompra, implementada en analitica (ADR-010)");

    @ArchTest
    static final ArchRule nadieFueraDeSemillaDependeDeSemilla = noClasses()
            .that().resideOutsideOfPackage("co.livorno.controlstock.semilla..")
            .should().dependOnClassesThat().resideInAPackage("co.livorno.controlstock.semilla..")
            .because("el generador de datos de demostración depende de los módulos, nunca al revés (ADR-008)");

    @ArchTest
    static final ArchRule nadieFueraDeSemillaDependeDeAnalitica = noClasses()
            .that().resideOutsideOfPackages("co.livorno.controlstock.semilla..", "co.livorno.controlstock.analitica..")
            .should().dependOnClassesThat().resideInAPackage("co.livorno.controlstock.analitica..")
            .because("analitica es un modelo de lectura que está arriba de todo; solo la semilla la invoca (ADR-009)");

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

    @ArchTest
    static final ArchRule nadieDependeDeReporte = noClasses()
            .that().resideOutsideOfPackage("co.livorno.controlstock.reporte..")
            .should().dependOnClassesThat().resideInAPackage("co.livorno.controlstock.reporte..")
            .because("reporte es un modelo de lectura que está arriba de todo (ADR-011)");

    @ArchTest
    static final ArchRule reporteNoDependeDeAnaliticaNiSemilla = noClasses()
            .that().resideInAPackage("co.livorno.controlstock.reporte..")
            .should().dependOnClassesThat().resideInAnyPackage("co.livorno.controlstock.analitica..",
                    "co.livorno.controlstock.semilla..")
            .allowEmptyShould(true)
            .because("el panel reutiliza datos por SQL y puertos; la exportación de tendencias vive en analitica.web (ADR-011)");

    @ArchTest
    static final ArchRule reporteNoInvocaMovimientoService = noClasses()
            .that().resideInAPackage("co.livorno.controlstock.reporte..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "co.livorno.controlstock.inventario.servicio.MovimientoService")
            .allowEmptyShould(true)
            .because("los reportes solo leen; el stock solo cambia por MovimientoService (regla de oro 1)");

    @ArchTest
    static final ArchRule reporteNoEscribeConJdbc = noClasses()
            .that().resideInAPackage("co.livorno.controlstock.reporte..")
            .should().callMethodWhere(new DescribedPredicate<JavaCall<?>>(
                    "invoca update/batchUpdate/execute de JdbcTemplate o NamedParameterJdbcTemplate") {
                @Override
                public boolean test(JavaCall<?> llamada) {
                    return (llamada.getTargetOwner().isAssignableTo(org.springframework.jdbc.core.JdbcTemplate.class)
                            || llamada.getTargetOwner().isAssignableTo(
                                    org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class))
                            && java.util.Set.of("update", "batchUpdate", "execute").contains(llamada.getName());
                }
            })
            .allowEmptyShould(true)
            .because("los reportes son de solo lectura (ADR-011)");

    @ArchTest
    static final ArchRule exportacionNoDependeDeModulosDeNegocio = noClasses()
            .that().resideInAPackage("co.livorno.controlstock.comun.exportacion..")
            .should().dependOnClassesThat().resideInAnyPackage("co.livorno.controlstock.auth..",
                    "co.livorno.controlstock.usuario..", "co.livorno.controlstock.catalogo..",
                    "co.livorno.controlstock.inventario..", "co.livorno.controlstock.venta..",
                    "co.livorno.controlstock.analitica..", "co.livorno.controlstock.compra..",
                    "co.livorno.controlstock.reporte..", "co.livorno.controlstock.semilla..")
            .allowEmptyShould(true)
            .because("los generadores de PDF y Excel son genéricos; cada módulo arma su DocumentoTabular (ADR-011)");
}
