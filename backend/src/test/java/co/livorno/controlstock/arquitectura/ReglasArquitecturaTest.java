package co.livorno.controlstock.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import co.livorno.controlstock.catalogo.modelo.Variante;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
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

    /** Cierra el hueco de un UPDATE/DELETE masivo que toque stock o kardex desde cualquier repositorio. */
    @ArchTest
    static final ArchRule ningunModifyingTocaStockNiKardex = methods()
            .that().areAnnotatedWith(Modifying.class)
            .should(new ArchCondition<com.tngtech.archunit.core.domain.JavaMethod>("no tener un @Query que mencione"
                    + " stock_fisico, stock_reservado, stockFisico, stockReservado o movimiento") {
                @Override
                public void check(com.tngtech.archunit.core.domain.JavaMethod metodo, ConditionEvents eventos) {
                    if (!metodo.isAnnotatedWith(org.springframework.data.jpa.repository.Query.class)) {
                        return;
                    }
                    String consulta = metodo.getAnnotationOfType(org.springframework.data.jpa.repository.Query.class)
                            .value().toLowerCase(java.util.Locale.ROOT);
                    for (String prohibido : Set.of("stock_fisico", "stock_reservado", "stockfisico",
                            "stockreservado", "movimiento")) {
                        if (consulta.contains(prohibido)) {
                            eventos.add(SimpleConditionEvent.violated(metodo,
                                    metodo.getFullName() + " es @Modifying y menciona '" + prohibido + "'"));
                            return;
                        }
                    }
                }
            })
            .because("el stock y el kardex solo cambian vía MovimientoService (reglas de oro 1 y 2)");

    /** ROP y clase ABC solo se escriben con la sentencia dirigida de analitica.repositorio (ADR-009). */
    @ArchTest
    static final ArchRule modifyingDeRopYClaseSoloEnAnaliticaRepositorio = methods()
            .that().areAnnotatedWith(Modifying.class)
            .and(new DescribedPredicate<com.tngtech.archunit.core.domain.JavaMethod>(
                    "mencionan punto_reorden, clase_abc, puntoReorden o claseAbc") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaMethod metodo) {
                    if (!metodo.isAnnotatedWith(org.springframework.data.jpa.repository.Query.class)) {
                        return false;
                    }
                    String consulta = metodo.getAnnotationOfType(org.springframework.data.jpa.repository.Query.class)
                            .value().toLowerCase(java.util.Locale.ROOT);
                    return Set.of("punto_reorden", "clase_abc", "puntoreorden", "claseabc").stream()
                            .anyMatch(consulta::contains);
                }
            })
            .should().beDeclaredInClassesThat().resideInAPackage("co.livorno.controlstock.analitica.repositorio")
            .allowEmptyShould(true)
            .because("el ROP y la clase ABC solo los escribe la analítica, sin pasar por @Version (ADR-009)");

    /** Si se renombra el método, la regla anterior pasaría en vacío: esta exige que el UPDATE siga existiendo. */
    @ArchTest
    static final ArchRule analiticaRepositorioSiEscribeRopYClase = methods()
            .that().areDeclaredIn(co.livorno.controlstock.analitica.repositorio.AnaliticaVarianteRepository.class)
            .and().areAnnotatedWith(Modifying.class)
            .should().haveName("aplicarAVariante")
            .because("AnaliticaVarianteRepository es el único escritor de punto_reorden y clase_abc (ADR-009)");

    /** Clases de solo lectura autorizadas a usar JDBC directo (el tipo JDBC no se inyecta en ninguna otra). */
    private static final String[] CLASES_JDBC_DE_LECTURA = {
        "co.livorno.controlstock.analitica.repositorio.ConsultasVentaAnalitica",
        "co.livorno.controlstock.analitica.repositorio.ConsultasEstadoAlertas",
        "co.livorno.controlstock.analitica.repositorio.ConsultasDemandaMensual",
        "co.livorno.controlstock.analitica.repositorio.ConsultasDisponibilidad",
        "co.livorno.controlstock.analitica.repositorio.ConsultasTendencias",
        "co.livorno.controlstock.analitica.repositorio.ConsultasPronostico"};

    private static final Set<String> TIPOS_JDBC = Set.of("org.springframework.jdbc.core.JdbcTemplate",
            "org.springframework.jdbc.core.JdbcOperations",
            "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
            "org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations",
            "org.springframework.jdbc.core.simple.JdbcClient");

    /**
     * JDBC directo evita JPA y por tanto cualquier regla sobre entidades: solo lo usan las consultas de lectura de la
     * analítica, el repositorio de reporte (ADR-011) y el módulo semilla (verifica y consulta, nunca escribe; ver la regla siguiente).
     */
    @ArchTest
    static final ArchRule jdbcDirectoSoloEnClasesDeLectura = noClasses()
            .that().doNotHaveFullyQualifiedName(CLASES_JDBC_DE_LECTURA[0])
            .and().doNotHaveFullyQualifiedName(CLASES_JDBC_DE_LECTURA[1])
            .and().doNotHaveFullyQualifiedName(CLASES_JDBC_DE_LECTURA[2])
            .and().doNotHaveFullyQualifiedName(CLASES_JDBC_DE_LECTURA[3])
            .and().doNotHaveFullyQualifiedName(CLASES_JDBC_DE_LECTURA[4])
            .and().doNotHaveFullyQualifiedName(CLASES_JDBC_DE_LECTURA[5])
            .and().resideOutsideOfPackage("co.livorno.controlstock.semilla..")
            .and().resideOutsideOfPackage("co.livorno.controlstock.reporte.repositorio..")
            .should().dependOnClassesThat(DescribedPredicate.describe("son JdbcTemplate, NamedParameterJdbcTemplate"
                    + " o JdbcClient", (JavaClass c) -> TIPOS_JDBC.contains(c.getName())))
            .because("un UPDATE por JDBC saltaría las reglas de stock, kardex y ROP/clase (reglas de oro 1 y 2, ADR-009)");

    /** Ni siquiera las clases autorizadas escriben: sin update, batchUpdate ni execute sobre JDBC. */
    @ArchTest
    static final ArchRule jdbcDirectoNuncaEscribeEnProduccion = noClasses()
            .should().callMethodWhere(DescribedPredicate.describe("JdbcTemplate.update/batchUpdate/execute",
                    (JavaMethodCall c) -> TIPOS_JDBC.stream().anyMatch(t -> c.getTarget().getOwner().getName().equals(t))
                            && Set.of("update", "batchUpdate", "execute").contains(c.getName())))
            .because("el JDBC directo es solo de lectura; las escrituras pasan por servicios y repositorios JPA");

    @ArchTest
    static final ArchRule nadieBorraEntidadesConEntityManager = noClasses()
            .should().callMethodWhere(DescribedPredicate.describe("EntityManager.remove o Session.remove/delete",
                    c -> (c.getTargetOwner().isAssignableTo(EntityManager.class) && c.getName().equals("remove"))
                            || (c.getTargetOwner().isAssignableTo(Session.class)
                                    && Set.of("remove", "delete").contains(c.getName()))))
            .because("ninguna entidad se borra por EntityManager; el kardex (Movimiento) nunca se borra (regla de oro 2)");
}
