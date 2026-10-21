package co.livorno.controlstock.compra.fixture;

import co.livorno.controlstock.venta.modelo.Venta;

/** Dependencia prohibida a propósito, solo para probar la regla ArchUnit. */
public class CompraQueUsaVenta {
    Venta venta;
}
