package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.EstadoEjecucion;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.analitica.modelo.ResultadoEjecucion;
import co.livorno.controlstock.analitica.repositorio.EjecucionAnaliticaRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de ejecuciones analiticas en transacciones propias e independientes del calculo: que el calculo falle y
 * revierta no borra el rastro de la ejecucion (R3, R4).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistroEjecucionService {

    static final String MENSAJE_INTERRUMPIDA =
            "Ejecución interrumpida (la aplicación se detuvo o falló antes de terminar).";

    private final EjecucionAnaliticaRepository ejecuciones;
    private final Clock clock;

    /**
     * Abre una ejecucion EN_CURSO. Quien llama ya tiene la guardia, asi que cualquier EN_CURSO anterior quedo huerfana
     * (caida del proceso) y se marca FALLIDA "interrumpida".
     */
    @Transactional
    public Long iniciar(OrigenEjecucion origen, Long usuarioId, LocalDate fechaCorte) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        for (EjecucionAnalitica huerfana : ejecuciones.findByEstado(EstadoEjecucion.EN_CURSO)) {
            huerfana.fallar(ahora, MENSAJE_INTERRUMPIDA);
            log.warn("Ejecución analítica {} marcada como interrumpida", huerfana.getId());
        }
        ejecuciones.flush();
        return ejecuciones.save(EjecucionAnalitica.iniciar(origen, usuarioId, ahora, fechaCorte)).getId();
    }

    @Transactional
    public EjecucionAnalitica terminar(Long id, ResultadoEjecucion resultado) {
        EjecucionAnalitica e = ejecuciones.findById(id).orElseThrow();
        e.terminar(LocalDateTime.now(clock), resultado);
        return e;
    }

    @Transactional
    public EjecucionAnalitica fallar(Long id, String mensaje) {
        EjecucionAnalitica e = ejecuciones.findById(id).orElseThrow();
        e.fallar(LocalDateTime.now(clock), mensaje);
        return e;
    }

    /** Cierra FALLIDA conservando alertas evaluadas y metrica (los conteos del calculo del resultado se ignoran). */
    @Transactional
    public EjecucionAnalitica fallar(Long id, String mensaje, ResultadoEjecucion medido) {
        EjecucionAnalitica e = ejecuciones.findById(id).orElseThrow();
        e.fallar(LocalDateTime.now(clock), mensaje, medido);
        return e;
    }

    /** Guarda los agregados del pronóstico apenas termina su paso (transacción propia). */
    @Transactional
    public void registrarPronostico(Long id, ResultadoPronosticoGuardado pronostico) {
        EjecucionAnalitica e = ejecuciones.findById(id).orElseThrow();
        e.registrarPronostico(pronostico.productosPronosticados(), pronostico.conHoltWinters(), pronostico.conSes(),
                pronostico.wapeGlobal());
    }

    /** Cierra FALLIDA conservando también los conteos del cálculo (el ABC + ROP se aplicó; falló el pronóstico). */
    @Transactional
    public EjecucionAnalitica fallarConResultado(Long id, String mensaje, ResultadoEjecucion resultado) {
        EjecucionAnalitica e = ejecuciones.findById(id).orElseThrow();
        e.fallarConResultado(LocalDateTime.now(clock), mensaje, resultado);
        return e;
    }
}
