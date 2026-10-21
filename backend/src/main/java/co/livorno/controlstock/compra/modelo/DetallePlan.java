package co.livorno.controlstock.compra.modelo;

import co.livorno.controlstock.catalogo.modelo.Variante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Línea de un plan de compras: una variante, única por plan. No toca stock. */
@Entity
@Table(name = "detalle_plan")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DetallePlan {

    public static final int CANTIDAD_MAXIMA = 100_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private PlanCompra plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false, updatable = false)
    private Variante variante;

    @Column(name = "cantidad_sugerida", updatable = false)
    private Integer cantidadSugerida;

    @Column(name = "cantidad_aprobada", nullable = false)
    private int cantidadAprobada;

    @Column(name = "costo_estimado", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoEstimado;

    @Column(length = 255)
    private String justificacion;

    @Column(name = "cantidad_recibida")
    private Integer cantidadRecibida;

    @Column(name = "costo_recibido", precision = 12, scale = 2)
    private BigDecimal costoRecibido;

    static DetallePlan de(PlanCompra plan, Variante variante, Integer cantidadSugerida, int cantidadAprobada,
            BigDecimal costoEstimado, String justificacion) {
        if (cantidadAprobada < 1 || cantidadAprobada > CANTIDAD_MAXIMA) {
            throw new IllegalArgumentException("La cantidad debe estar entre 1 y " + CANTIDAD_MAXIMA);
        }
        if (costoEstimado == null || costoEstimado.signum() < 0) {
            throw new IllegalArgumentException("El costo estimado no puede ser negativo");
        }
        DetallePlan d = new DetallePlan();
        d.plan = plan;
        d.variante = variante;
        d.cantidadSugerida = cantidadSugerida;
        d.cantidadAprobada = cantidadAprobada;
        d.costoEstimado = costoEstimado.setScale(2, RoundingMode.HALF_UP);
        d.justificacion = justificacion;
        return d;
    }

    /** Recepción única: cantidad (0 a 100.000) y costo de la línea. */
    public void registrarRecepcion(int cantidad, BigDecimal costo) {
        if (cantidad < 0 || cantidad > CANTIDAD_MAXIMA) {
            throw new IllegalArgumentException("La cantidad recibida debe estar entre 0 y " + CANTIDAD_MAXIMA);
        }
        if (costo == null || costo.signum() < 0) {
            throw new IllegalArgumentException("El costo recibido no puede ser negativo");
        }
        this.cantidadRecibida = cantidad;
        this.costoRecibido = costo.setScale(2, RoundingMode.HALF_UP);
    }
}
