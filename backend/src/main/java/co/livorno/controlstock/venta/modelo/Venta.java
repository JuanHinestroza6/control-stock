package co.livorno.controlstock.venta.modelo;

import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.usuario.modelo.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * Agregado de venta: cabecera + líneas en cascada. No toca stock (regla de oro 1): VentaService llama a
 * MovimientoService por cada línea. Estados: REGISTRADA a ANULADA.
 */
@Entity
@Table(name = "venta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario vendedor;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO.setScale(2);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoVenta estado = EstadoVenta.REGISTRADA;

    @Column(name = "anulada_en")
    private LocalDateTime anuladaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulada_por")
    private Usuario anuladaPor;

    @Column(name = "motivo_anulacion", length = 255)
    private String motivoAnulacion;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleVenta> detalles = new ArrayList<>();

    public Venta(Usuario vendedor, LocalDateTime fecha) {
        if (vendedor == null || fecha == null) {
            throw new IllegalArgumentException("La venta requiere vendedor y fecha");
        }
        this.vendedor = vendedor;
        this.fecha = fecha;
    }

    /** Agrega una línea (una por variante) y recalcula el total. */
    public DetalleVenta agregarLinea(Variante variante, int cantidad, BigDecimal precioLista,
            BigDecimal precioUnitario, BigDecimal costoUnitario) {
        if (estado != EstadoVenta.REGISTRADA) {
            throw new IllegalStateException("No se pueden agregar líneas a una venta anulada");
        }
        if (detalles.stream().anyMatch(d -> d.getVariante() == variante)) {
            throw new IllegalArgumentException("La variante ya está en la venta");
        }
        DetalleVenta d = new DetalleVenta(this, variante, cantidad, precioLista, precioUnitario, costoUnitario);
        detalles.add(d);
        total = detalles.stream().map(DetalleVenta::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return d;
    }

    /**
     * Marca la venta como ANULADA. Lanza ConflictoException (409) si ya lo está. La regla de devoluciones, el rol y
     * los movimientos ANULACION_VENTA son del servicio.
     */
    public void anular(Usuario por, LocalDateTime cuando, String motivo) {
        if (estado == EstadoVenta.ANULADA) {
            throw new ConflictoException("La venta " + numero() + " ya está anulada.");
        }
        if (por == null || cuando == null || motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("La anulación requiere usuario, fecha y motivo");
        }
        this.estado = EstadoVenta.ANULADA;
        this.anuladaPor = por;
        this.anuladaEn = cuando;
        this.motivoAnulacion = motivo.strip();
    }

    public List<DetalleVenta> getDetalles() {
        return Collections.unmodifiableList(detalles);
    }

    public int unidades() {
        return detalles.stream().mapToInt(DetalleVenta::getCantidad).sum();
    }

    public BigDecimal descuentoTotal() {
        return detalles.stream().map(DetalleVenta::descuento).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Número visible derivado del id: V-000123 (null si aún no se persistió). */
    public String numero() {
        return id == null ? null : String.format("V-%06d", id);
    }
}
