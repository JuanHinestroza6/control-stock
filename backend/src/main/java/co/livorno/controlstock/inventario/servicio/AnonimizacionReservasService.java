package co.livorno.controlstock.inventario.servicio;

import co.livorno.controlstock.inventario.modelo.Reserva;
import co.livorno.controlstock.inventario.repositorio.ReservaRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Anonimiza los datos del cliente de reservas cerradas (Ley 1581, ADR-007). No genera movimientos ni toca stock. */
@Service
@RequiredArgsConstructor
public class AnonimizacionReservasService {

    private final ReservaRepository reservas;

    /** Anonimiza un lote en una transacción; devuelve cuántas reservas procesó (0 = no queda nada). */
    @Transactional
    public int anonimizarLote(LocalDateTime limite, int tamano) {
        List<Reserva> lote = reservas.pendientesDeAnonimizar(limite, Reserva.ANONIMIZADO, PageRequest.of(0, tamano));
        lote.forEach(Reserva::anonimizar);
        reservas.saveAllAndFlush(lote);
        return lote.size();
    }
}
