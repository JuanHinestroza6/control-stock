package co.livorno.controlstock.inventario.modelo;

/** Deltas que un movimiento aplica sobre el stock físico y el reservado de una variante. */
public record EfectoMovimiento(int deltaFisico, int deltaReservado) {
}
