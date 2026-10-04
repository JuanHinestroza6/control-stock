package co.livorno.controlstock.semilla;

import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.usuario.modelo.Rol;
import jakarta.persistence.EntityManager;
import co.livorno.controlstock.inventario.servicio.AlertaService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orquesta la carga de datos de demostración (ADR-008) en una sola transacción: protecciones, usuarios demo, catálogo
 * y entrada inicial. La simulación diaria y el cierre llegan en B4/B5. Todo el stock pasa por los servicios reales.
 */
@Component
@Profile("seed")
public class GeneradorDatosSemilla {

    private static final Logger log = LoggerFactory.getLogger(GeneradorDatosSemilla.class);
    static final LocalTime HORA_CATALOGO = LocalTime.of(7, 0);
    static final LocalTime HORA_ENTRADA_INICIAL = LocalTime.of(8, 0);

    private final PropiedadesSemilla propiedades;
    private final ProteccionesSemilla protecciones;
    private final UsuariosDemo usuariosDemo;
    private final CreadorCatalogo creadorCatalogo;
    private final SimuladorDiario simulador;
    private final RelojSimulado reloj;
    private final ComoUsuario comoUsuario;
    private final TransactionTemplate transaccion;
    private final EntityManager entityManager;
    private final AlertaService alertaService;
    private final VerificadorConsistencia verificador;
    private final JdbcTemplate jdbc;

    public GeneradorDatosSemilla(PropiedadesSemilla propiedades, ProteccionesSemilla protecciones,
                                 UsuariosDemo usuariosDemo, CreadorCatalogo creadorCatalogo, SimuladorDiario simulador,
                                 RelojSimulado reloj,
                                 ComoUsuario comoUsuario, TransactionTemplate transaccion,
                                 EntityManager entityManager, AlertaService alertaService,
                                 VerificadorConsistencia verificador, JdbcTemplate jdbc) {
        this.propiedades = propiedades;
        this.protecciones = protecciones;
        this.usuariosDemo = usuariosDemo;
        this.creadorCatalogo = creadorCatalogo;
        this.simulador = simulador;
        this.reloj = reloj;
        this.comoUsuario = comoUsuario;
        this.transaccion = transaccion;
        this.entityManager = entityManager;
        this.alertaService = alertaService;
        this.verificador = verificador;
        this.jdbc = jdbc;
    }

    /**
     * Todo o nada: cualquier excepción revierte la transacción. Con ventas existentes no hace nada; con datos
     * parciales, perfil prod o fecha final inválida lanza {@link SemillaAbortadaException}.
     */
    public ResumenSemilla generar() {
        protecciones.validarPerfil();
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate fin = propiedades.fechaFin() != null ? propiedades.fechaFin() : hoy.minusDays(1);
        protecciones.validarFechaFin(fin, hoy);
        long t0 = System.currentTimeMillis();
        ResumenSemilla resumen;
        try {
            resumen = transaccion.execute(estado -> cargar(fin));
        } finally {
            reloj.volverAlSistema();
        }
        if (resumen == null || !resumen.generado()) {
            return resumen;
        }
        return cerrar(resumen, t0);
    }

    /**
     * Cierre con el reloj real y la carga ya confirmada: las alertas se evalúan en transacción propia
     * ({@code AlertaService.evaluar} usa REQUIRES_NEW), variante por variante en orden de id.
     */
    private ResumenSemilla cerrar(ResumenSemilla resumen, long t0) {
        List<Long> ids = jdbc.queryForList("select id from variante order by id", Long.class);
        ids.forEach(alertaService::evaluar);
        verificador.verificarAlertas();
        Map<String, Long> porTipo = new HashMap<>();
        jdbc.query("select tipo, count(*) as n from alerta where estado = 'ACTIVA' group by tipo",
                rs -> {
                    porTipo.put(rs.getString("tipo"), rs.getLong("n"));
                });
        Long activas = jdbc.queryForObject("select count(*) from reserva where estado = 'ACTIVA'", Long.class);
        return resumen.conCierre(activas == null ? 0 : activas, porTipo.getOrDefault("AGOTADO", 0L),
                porTipo.getOrDefault("STOCK_BAJO", 0L), System.currentTimeMillis() - t0);
    }

    private ResumenSemilla cargar(LocalDate fin) {
        if (protecciones.evaluarBase() == ProteccionesSemilla.Estado.YA_HAY_DATOS) {
            log.info("Datos semilla: ya hay ventas en la base, no se hace nada.");
            return ResumenSemilla.yaHabiaDatos();
        }
        usuariosDemo.validarPassword(propiedades.password());
        LocalDate inicio = fin.minusMonths(propiedades.meses()).plusDays(1);
        reloj.avanzarA(inicio.atTime(HORA_CATALOGO));

        Map<Rol, UsuarioAutenticado> usuarios = usuariosDemo.crearOReutilizar(propiedades.password());
        var catalogo = CatalogoSemilla.productos();
        var modelo = new ModeloDemanda(catalogo, inicio, fin, propiedades.ticketsDiaBase(),
                new Random(propiedades.semillaAleatoria()));
        // Flujo aleatorio propio de los costos de entrada, independiente del de la demanda.
        Random aleatorioCostos = new Random(propiedades.semillaAleatoria() + 1);
        UsuarioAutenticado inventario = usuarios.get(Rol.INVENTARIO);

        CreadorCatalogo.CatalogoCreado creado = comoUsuario.ejecutar(inventario,
                () -> creadorCatalogo.crearCatalogo(catalogo, modelo, inicio));
        reloj.avanzarA(inicio.atTime(HORA_ENTRADA_INICIAL));
        long unidades = comoUsuario.ejecutar(inventario,
                () -> creadorCatalogo.entradaInicial(creado, modelo, inicio, aleatorioCostos));
        entityManager.flush();
        entityManager.clear();

        ResultadoSimulacion simulacion = simulador.simular(creado, modelo, usuarios, inicio, fin, aleatorioCostos,
                new Random(propiedades.semillaAleatoria() + 2));

        verificador.verificarCarga(inicio, fin);
        return new ResumenSemilla(true, inicio, fin, usuarios.size(), creado.productoIds().size(),
                creado.variantes().size(), unidades, simulacion, 0, 0, 0, 0);
    }
}
