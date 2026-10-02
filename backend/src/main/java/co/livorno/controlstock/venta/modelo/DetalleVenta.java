package co.livorno.controlstock.venta.modelo;

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

/** Línea de venta con instantánea de precios y costo al momento de vender (ADR-006). Se crea solo desde Venta. */
@Entity
@Table(name = "detalle_venta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false, updatable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false, updatable = false)
    private Variante variante;

    @Column(nullable = false, updatable = false)
    private int cantidad;

    @Column(name = "precio_lista", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal precioLista;

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal precioUnitario;

    @Column(name = "costo_unitario", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal costoUnitario;

    DetalleVenta(Venta venta, Variante variante, int cantidad, BigDecimal precioLista, BigDecimal precioUnitario,
            BigDecimal costoUnitario) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        this.venta = venta;
        this.variante = variante;
        this.cantidad = cantidad;
        this.precioLista = escala(precioLista);
        this.precioUnitario = escala(precioUnitario);
        this.costoUnitario = escala(costoUnitario);
        if (this.precioLista.signum() <= 0) {
            throw new IllegalArgumentException("El precio de lista debe ser mayor que cero");
        }
        if (this.precioUnitario.signum() <= 0 || this.precioUnitario.compareTo(this.precioLista) > 0) {
            throw new IllegalArgumentException("El precio unitario debe ser mayor que cero y no superar el de lista");
        }
        if (this.costoUnitario.signum() < 0) {
            throw new IllegalArgumentException("El costo unitario no puede ser negativo");
        }
    }

    /** Subtotal de la línea: cantidad por precioUnitario. */
    public BigDecimal subtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Descuento otorgado en la línea: (precioLista - precioUnitario) por cantidad. */
    public BigDecimal descuento() {
        return precioLista.subtract(precioUnitario).multiply(BigDecimal.valueOf(cantidad))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
