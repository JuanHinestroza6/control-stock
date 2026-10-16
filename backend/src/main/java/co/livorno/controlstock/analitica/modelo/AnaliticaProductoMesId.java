package co.livorno.controlstock.analitica.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;

/** Clave de {@link AnaliticaProductoMes}: producto y mes (día 1). */
@Embeddable
public record AnaliticaProductoMesId(
        @Column(name = "producto_id") Long productoId,
        @Column(name = "periodo") LocalDate periodo) implements Serializable {
}
