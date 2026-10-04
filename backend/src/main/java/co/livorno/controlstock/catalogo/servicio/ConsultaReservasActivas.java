package co.livorno.controlstock.catalogo.servicio;

import java.util.Optional;

/**
 * Puerto que catalogo usa para saber si hay apartados ACTIVOS (ADR-007): lo implementa inventario, así catalogo no
 * depende de inventario ni de la tabla de reservas.
 */
public interface ConsultaReservasActivas {

    /** Apartados ACTIVOS de una variante y las unidades que suman. */
    record Resumen(long apartados, long unidades) {
        public boolean hayApartados() {
            return apartados > 0;
        }
    }

    /** Primera variante (por id) de un producto con apartados ACTIVOS. */
    record VarianteConApartados(Long varianteId, String sku, long apartados, long unidades) {
    }

    Resumen resumenDeVariante(Long varianteId);

    Optional<VarianteConApartados> primeraVarianteConApartados(Long productoId);
}
