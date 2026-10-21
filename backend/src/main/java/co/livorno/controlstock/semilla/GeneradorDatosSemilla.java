package co.livorno.controlstock.semilla;

import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.usuario.modelo.Rol;
import jakarta.persistence.EntityManager;
import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.EstadoEjecucion;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.analitica.servicio.EjecucionAnaliticaService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.TreeMap;
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
 * entrada inicial, simulación diaria y, al confirmar, el cierre analítico. Todo el stock pasa por los servicios reales.
 */
@Component
@Profile("seed")
public class GeneradorDatosSemilla {

    private static final Logger log = LoggerFactory.getLogger(GeneradorDatosSemilla.class);
    static final String MENSAJE_RECUPERACION = "La carga de datos quedó confirmada, pero la analítica de cierre falló. "
            + "No repitas la carga (diría 'ya hay datos'): arranca la aplicación con el perfil dev e inicia sesión como "
            + "ADMIN para ejecutar 'Recalcular' en Analítica (POST /api/analitica/recalcular).";
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
    private final EjecucionAnaliticaService ejecucionAnalitica;
    private final VerificadorConsistencia verificador;
    private final JdbcTemplate jdbc;

    public GeneradorDatosSemilla(PropiedadesSemilla propiedades, ProteccionesSemilla protecciones,
                                 UsuariosDemo usuariosDemo, CreadorCatalogo creadorCatalogo, SimuladorDiario simulador,
                                 RelojSimulado reloj,
                                 ComoUsuario comoUsuario, TransactionTemplate transaccion,
                                 EntityManager entityManager, EjecucionAnaliticaService ejecucionAnalitica,
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
        this.ejecucionAnalitica = ejecucionAnalitica;
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
        if (resumen == null) {
            return null;
        }
        if (!resumen.generado()) {
            return resumen;
        }
        return cerrar(resumen, t0);
    }

    /**
     * Cierre con la carga ya confirmada y el reloj en el del sistema: ejecuta la orquestación analítica completa
     * (origen SEMILLA: ABC, ROP y re-evaluación de todas las alertas) y verifica el resultado. Si falla, la carga
     * sigue confirmada: se lanza {@link SemillaAbortadaException} (el ejecutor termina con código 1) explicando cómo
     * recuperarse sin repetir la carga.
     */
    private ResumenSemilla cerrar(ResumenSemilla resumen, long t0) {
        EjecucionAnalitica ejecucion;
        try {
            ejecucion = ejecucionAnalitica.ejecutar(OrigenEjecucion.SEMILLA, null);
        } catch (RuntimeException e) {
            log.error("La analítica de cierre no se pudo ejecutar: {}", e.getMessage(), e);
            throw new SemillaAbortadaException(MENSAJE_RECUPERACION + " Causa: " + e.getMessage());
        }
        if (ejecucion.getEstado() != EstadoEjecucion.EXITOSA) {
            log.error("La analítica de cierre terminó {}: {}", ejecucion.getEstado(), ejecucion.getMensaje());
            throw new SemillaAbortadaException(MENSAJE_RECUPERACION + " Estado: " + ejecucion.getEstado() + ". "
                    + ejecucion.getMensaje());
        }
        try {
            verificador.verificarAnalitica();
        } catch (SemillaAbortadaException e) {
            throw new SemillaAbortadaException(MENSAJE_RECUPERACION + " " + e.getMessage());
        }

        Map<String, Long> porTipo = new HashMap<>();
        jdbc.query("select tipo, count(*) as n from alerta where estado = 'ACTIVA' group by tipo",
                rs -> {
                    porTipo.put(rs.getString("tipo"), rs.getLong("n"));
                });
        Map<String, Long> porMetodo = new TreeMap<>();
        jdbc.query("select metodo_reorden, count(*) as n from analitica_variante group by metodo_reorden",
                rs -> {
                    porMetodo.put(rs.getString(1), rs.getLong(2));
                });
        Long activas = jdbc.queryForObject("select count(*) from reserva where estado = 'ACTIVA'", Long.class);
        var analitica = new ResumenSemilla.CierreAnalitica(ejecucion.getClaseA(), ejecucion.getClaseB(),
                ejecucion.getClaseC(), ejecucion.getConPuntoReorden(), porMetodo, ejecucion.getVariantesActivas(),
                ejecucion.getEnAlertaStock(), ejecucion.getPorcentajeAlertaStock(),
                porTipo.getOrDefault("SIN_ROTACION", 0L), ejecucion.getDuracionMs(),
                ejecucion.getConHoltWinters(), ejecucion.getConSes(), ejecucion.getWapeGlobal(),
                ejecucion.getEnAlertaSinTransito(), ejecucion.getConPedidoEnCamino());
        return resumen.conCierre(activas == null ? 0 : activas, porTipo.getOrDefault("AGOTADO", 0L),
                porTipo.getOrDefault("STOCK_BAJO", 0L), System.currentTimeMillis() - t0, analitica);
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
        Map<Long, Integer> enCamino = new HashMap<>();
        for (int i = 0; i < creado.variantes().size(); i++) {
            int unidadesEnCamino = simulacion.enCaminoPorVariante().get(i);
            if (unidadesEnCamino > 0) {
                enCamino.put(creado.variantes().get(i).id(), unidadesEnCamino);
            }
        }
        verificador.verificarEnCamino(enCamino);
        return new ResumenSemilla(true, inicio, fin, usuarios.size(), creado.productoIds().size(),
                creado.variantes().size(), unidades, simulacion, 0, 0, 0, 0, null);
    }
}
