package co.livorno.controlstock.inventario.servicio;

import co.livorno.controlstock.inventario.repositorio.ReservaRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Vence las reservas ACTIVAS cuyo {@code venceEn} ya pasó (ADR-007). Cada reserva se procesa en su propia
 * transacción ({@link ReservaService#vencer}): un fallo o un choque con una venta no revierte las demás y se
 * reintenta en el ciclo siguiente. No es transaccional a propósito.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VencimientoReservasTarea {

    static final int LOTE_MAXIMO = 500;

    private final ReservaRepository reservas;
    private final ReservaService servicio;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.reservas.vencimiento.intervalo:PT15M}", initialDelayString = "PT1M")
    public void ejecutar() {
        procesar(LocalDateTime.now(clock));
    }

    /** Devuelve cuántas reservas se vencieron en este ciclo. */
    public int procesar(LocalDateTime ahora) {
        List<Long> ids = reservas.idsVencidas(ahora, PageRequest.of(0, LOTE_MAXIMO));
        int vencidas = 0;
        for (Long id : ids) {
            try {
                if (servicio.vencer(id, ahora)) {
                    vencidas++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo vencer la reserva {}; se reintenta en el siguiente ciclo", id, e);
            }
        }
        if (vencidas > 0) {
            log.info("Reservas vencidas: {}", vencidas);
        }
        return vencidas;
    }
}
