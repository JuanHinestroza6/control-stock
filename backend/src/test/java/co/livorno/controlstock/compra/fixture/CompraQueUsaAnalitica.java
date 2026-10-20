package co.livorno.controlstock.compra.fixture;

import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;

/** Dependencia prohibida a propósito, solo para probar la regla ArchUnit. */
public class CompraQueUsaAnalitica {
    EjecucionAnalitica ejecucion;
}
