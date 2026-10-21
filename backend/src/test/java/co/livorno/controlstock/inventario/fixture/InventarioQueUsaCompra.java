package co.livorno.controlstock.inventario.fixture;

import co.livorno.controlstock.compra.modelo.PlanCompra;

/** Dependencia prohibida a propósito, solo para probar la regla ArchUnit. */
public class InventarioQueUsaCompra {
    PlanCompra plan;
}
