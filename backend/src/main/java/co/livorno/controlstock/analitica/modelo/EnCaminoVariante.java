package co.livorno.controlstock.analitica.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

/**
 * Vista de solo lectura de lo que viene en camino por variante: suma de {@code cantidad_aprobada} de los planes
 * APROBADO (ADR-010). Existe para filtrar por la posición de inventario en la base sin depender de clases del módulo
 * {@code compra}: solo lee las tablas. No se escribe nunca.
 */
@Entity
@Immutable
@Subselect("""
        select dp.variante_id as variante_id, sum(dp.cantidad_aprobada) as cantidad
        from detalle_plan dp join plan_compra pc on pc.id = dp.plan_id
        where pc.estado = 'APROBADO'
        group by dp.variante_id
        """)
@Synchronize({"detalle_plan", "plan_compra"})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnCaminoVariante {

    @Id
    @Column(name = "variante_id")
    private Long varianteId;

    @Column(name = "cantidad")
    private Long cantidad;
}
