package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.calculo.PronosticoMensual;
import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.analitica.modelo.ResultadoEjecucion;
import co.livorno.controlstock.analitica.repositorio.ConsultasEstadoAlertas;
import co.livorno.controlstock.inventario.servicio.AlertaService;
import co.livorno.controlstock.inventario.servicio.ConsultaPedidosEnCamino;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquestador de la ejecucion analitica (ADR-009, R4, R6). Deliberadamente no transaccional: cada paso tiene su propia
 * transaccion (registro, calculo ABC + ROP, una por variante en la re-evaluacion de alertas). Una sola ejecucion a la
 * vez por instancia (guardia en memoria).
 */
@Service
@Slf4j
public class EjecucionAnaliticaService {

    static final String MENSAJE_CALCULO_FALLIDO =
            "El cálculo de ABC y punto de reorden falló; se conservaron los valores anteriores.";

    static final String MENSAJE_PRONOSTICO_FALLIDO = "El pronóstico falló; el ROP usó la demanda histórica";

    private final RegistroEjecucionService registro;
    private final PronosticoService pronostico;
    private final CalculoAnaliticoService calculo;
    private final AlertaService alertas;
    private final ConsultasEstadoAlertas estado;
    private final ConsultaPedidosEnCamino pedidos;
    private final Clock clock;
    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    public EjecucionAnaliticaService(RegistroEjecucionService registro, PronosticoService pronostico,
            CalculoAnaliticoService calculo, AlertaService alertas, ConsultasEstadoAlertas estado,
            ConsultaPedidosEnCamino pedidos, Clock clock) {
        this.registro = registro;
        this.pronostico = pronostico;
        this.calculo = calculo;
        this.alertas = alertas;
        this.estado = estado;
        this.pedidos = pedidos;
        this.clock = clock;
    }

    /**
     * Ejecuta la analitica completa y devuelve la ejecucion terminada (EXITOSA, o FALLIDA si fallo el calculo).
     *
     * @param usuarioId obligatorio solo si el origen es MANUAL
     * @throws EjecucionEnCursoException si ya hay otra ejecucion en curso (409 en manual; la nocturna la omite)
     */
    public EjecucionAnalitica ejecutar(OrigenEjecucion origen, Long usuarioId) {
        if (!enCurso.compareAndSet(false, true)) {
            throw new EjecucionEnCursoException();
        }
        try {
            return ejecutarConGuardia(origen, usuarioId);
        } finally {
            enCurso.set(false);
        }
    }

    /** Métrica contrafactual (ADR-010); un fallo aquí no debe tumbar la ejecución. */
    private void registrarPosicion(Long id, Map<Long, Long> enCamino) {
        try {
            Map<Long, Long> mapa = enCamino != null ? enCamino : pedidos.enCaminoPorVariante();
            java.util.Set<Long> activas = estado.idsVariantesActivas();
            int conPedido = (int) mapa.entrySet().stream()
                    .filter(e -> e.getValue() != null && e.getValue() > 0 && activas.contains(e.getKey())).count();
            registro.registrarPosicion(id, estado.enAlertaSinTransito(), conPedido);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la métrica de posición de la ejecución {}", id, e);
        }
    }

    private EjecucionAnalitica ejecutarConGuardia(OrigenEjecucion origen, Long usuarioId) {
        Long id = registro.iniciar(origen, usuarioId, LocalDate.now(clock));
        log.info("Ejecución analítica {} iniciada ({})", id, origen);

        // Paso 2: pronóstico, en su propia transacción (todo o nada). Si falla, el ROP usa la demanda histórica.
        Map<Long, PronosticoMensual> pronosticos = Map.of();
        boolean pronosticoFallo = false;
        try {
            ResultadoPronosticoGuardado guardado = pronostico.calcularYGuardar(id);
            pronosticos = guardado.porVariante();
            registro.registrarPronostico(id, guardado);
        } catch (RuntimeException e) {
            pronosticoFallo = true;
            pronosticos = Map.of();
            log.error("Falló el pronóstico de la ejecución {}; el ROP usará la demanda histórica", id, e);
        }

        // Paso 3: ABC + ROP, todo o nada. Si falla se registra y se sigue con las alertas.
        ResultadoCalculo calculado = null;
        try {
            calculado = calculo.calcularYAplicar(id, pronosticos);
        } catch (RuntimeException e) {
            log.error("Falló el cálculo de ABC y punto de reorden de la ejecución {}", id, e);
        }

        // Paso 4: re-evaluar las alertas de todas las variantes; un fallo no detiene el ciclo.
        int evaluadas = 0;
        int fallidas = 0;
        List<Long> ids = estado.idsTodasLasVariantes();
        // Lo que viene en camino se trae en lote (una consulta); si falla, cada variante lo consulta por su cuenta.
        Map<Long, Long> enCamino = null;
        try {
            enCamino = pedidos.enCaminoPorVariante();
        } catch (RuntimeException e) {
            log.warn("No se pudo leer lo que viene en camino en lote; se consultará variante por variante", e);
        }
        for (Long varianteId : ids) {
            try {
                if (enCamino != null) {
                    alertas.evaluar(varianteId, enCamino.getOrDefault(varianteId, 0L));
                } else {
                    alertas.evaluar(varianteId);
                }
                evaluadas++;
            } catch (RuntimeException e) {
                fallidas++;
                log.warn("No se pudo re-evaluar la alerta de la variante {}", varianteId, e);
            }
        }

        // Paso 5: métrica (D4) y cierre; se mide también si el cálculo falló, porque las alertas sí se re-evaluaron.
        int activas = estado.variantesActivas();
        int enAlerta = estado.enAlertaStock();
        int sinRotacion = estado.sinRotacionActivas();
        registrarPosicion(id, enCamino);
        if (calculado == null) {
            log.warn("Ejecución analítica {} FALLIDA ({} alertas evaluadas, {} fallidas)", id, evaluadas, fallidas);
            return registro.fallar(id, MENSAJE_CALCULO_FALLIDO,
                    new ResultadoEjecucion(0, 0, 0, 0, 0, evaluadas, fallidas, activas, enAlerta, sinRotacion));
        }
        ResultadoEjecucion resultado = new ResultadoEjecucion(calculado.variantesAnalizadas(), calculado.claseA(),
                calculado.claseB(), calculado.claseC(), calculado.conPuntoReorden(), evaluadas, fallidas,
                activas, enAlerta, sinRotacion);
        if (pronosticoFallo) {
            log.warn("Ejecución analítica {} FALLIDA: el pronóstico falló y el ROP usó la demanda histórica", id);
            return registro.fallarConResultado(id, MENSAJE_PRONOSTICO_FALLIDO, resultado);
        }
        EjecucionAnalitica terminada = registro.terminar(id, resultado);
        log.info("Ejecución analítica {} EXITOSA en {} ms: {} variantes, alertas {} ok / {} fallidas, en alerta {} %",
                id, terminada.getDuracionMs(), resultado.variantesAnalizadas(), evaluadas, fallidas,
                terminada.getPorcentajeAlertaStock());
        return terminada;
    }
}
