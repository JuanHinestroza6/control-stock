package co.livorno.controlstock.inventario.modelo;

/** Máquina de estados de la reserva: ACTIVA pasa a CONVERTIDA, CANCELADA o VENCIDA (ADR-007). */
public enum EstadoReserva {
    ACTIVA, CONVERTIDA, CANCELADA, VENCIDA
}
