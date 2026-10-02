package co.livorno.controlstock.venta.modelo;

import co.livorno.controlstock.usuario.modelo.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Devolución parcial o total de una línea de venta. Inmutable: no se edita ni se borra. */
@Entity
@Immutable
@Table(name = "devolucion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Devolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detalle_venta_id", nullable = false, updatable = false)
    private DetalleVenta detalleVenta;

    @Column(nullable = false, updatable = false)
    private int cantidad;

    @Column(nullable = false, length = 255, updatable = false)
    private String motivo;

    @Column(name = "apta_para_venta", nullable = false, updatable = false)
    private boolean aptaParaVenta;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    public Devolucion(DetalleVenta detalleVenta, int cantidad, String motivo, boolean aptaParaVenta,
            LocalDateTime fecha, Usuario usuario) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        if (detalleVenta == null || fecha == null || usuario == null) {
            throw new IllegalArgumentException("La devolución requiere línea, fecha y usuario");
        }
        this.detalleVenta = detalleVenta;
        this.cantidad = cantidad;
        this.motivo = motivo.strip();
        this.aptaParaVenta = aptaParaVenta;
        this.fecha = fecha;
        this.usuario = usuario;
    }
}
