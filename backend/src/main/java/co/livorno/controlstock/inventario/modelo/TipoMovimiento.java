package co.livorno.controlstock.inventario.modelo;

/** Tipos de movimiento del kardex y su efecto sobre el stock (docs/dominio.md). Único lugar con esta lógica. */
public enum TipoMovimiento {
    ENTRADA(1, 0),
    VENTA(-1, 0),
    VENTA_RESERVA(-1, -1),
    DEVOLUCION(1, 0),
    AJUSTE_POSITIVO(1, 0),
    AJUSTE_NEGATIVO(-1, 0),
    RESERVA(0, 1),
    LIBERACION(0, -1),
    ANULACION_VENTA(1, 0);

    private final int signoFisico;
    private final int signoReservado;

    TipoMovimiento(int signoFisico, int signoReservado) {
        this.signoFisico = signoFisico;
        this.signoReservado = signoReservado;
    }

    public EfectoMovimiento efecto(int cantidad) {
        return new EfectoMovimiento(signoFisico * cantidad, signoReservado * cantidad);
    }
}
