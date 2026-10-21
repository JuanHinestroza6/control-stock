package co.livorno.controlstock.inventario.servicio;

import co.livorno.controlstock.catalogo.evento.UmbralCambiado;
import co.livorno.controlstock.catalogo.evento.VarianteDesactivada;
import co.livorno.controlstock.inventario.evento.PosicionCambiada;
import co.livorno.controlstock.inventario.evento.StockCambiado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evalúa alertas después del commit. Un fallo aquí nunca debe llegar al cliente: la operación ya está confirmada,
 * así que se registra en el log y se sigue (la siguiente operación sobre la variante reevalúa).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.alertas.por-evento", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AlertaListener {

    private final AlertaService alertas;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCambiarStock(StockCambiado evento) {
        evaluar(evento.varianteId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCambiarUmbral(UmbralCambiado evento) {
        evaluar(evento.varianteId());
    }

    /** Aprobar un plan o cancelar uno APROBADO cambia lo que viene en camino sin mover stock (ADR-010). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCambiarPosicion(PosicionCambiada evento) {
        evaluar(evento.varianteId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alDesactivarVariante(VarianteDesactivada evento) {
        evaluar(evento.varianteId());
    }

    private void evaluar(Long varianteId) {
        try {
            alertas.evaluar(varianteId);
        } catch (RuntimeException e) {
            log.error("No se pudieron evaluar las alertas de la variante {}", varianteId, e);
        }
    }
}
