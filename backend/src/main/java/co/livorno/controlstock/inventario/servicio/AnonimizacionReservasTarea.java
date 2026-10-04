package co.livorno.controlstock.inventario.servicio;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea nocturna: anonimiza nombre y contacto de las reservas cerradas hace {@code dias} días o más (Ley 1581,
 * ADR-007). Cada lote va en su propia transacción; el log solo registra cantidades, nunca datos del cliente.
 */
@Component
@Slf4j
public class AnonimizacionReservasTarea {

    static final int TAMANO_LOTE = 200;

    private final AnonimizacionReservasService servicio;
    private final Clock clock;
    private final int dias;

    public AnonimizacionReservasTarea(AnonimizacionReservasService servicio, Clock clock,
            @Value("${app.reservas.anonimizacion.dias:90}") int dias) {
        if (dias < 1) {
            throw new IllegalArgumentException("app.reservas.anonimizacion.dias debe ser al menos 1");
        }
        this.servicio = servicio;
        this.clock = clock;
        this.dias = dias;
    }

    @Scheduled(cron = "${app.reservas.anonimizacion.cron:0 30 2 * * *}", zone = "America/Bogota")
    public void ejecutar() {
        try {
            procesar(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.warn("Falló la anonimización de reservas; se reintenta en la siguiente ejecución", e);
        }
    }

    /** Devuelve cuántas reservas se anonimizaron. Cerradas con {@code cerradaEn <= ahora - dias}. */
    public int procesar(LocalDateTime ahora) {
        LocalDateTime limite = ahora.minusDays(dias);
        int total = 0;
        int lote;
        do {
            lote = servicio.anonimizarLote(limite, TAMANO_LOTE);
            total += lote;
        } while (lote == TAMANO_LOTE);
        if (total > 0) {
            log.info("Reservas anonimizadas: {}", total);
        }
        return total;
    }
}
