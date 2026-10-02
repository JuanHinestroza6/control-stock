package co.livorno.controlstock.inventario.modelo;

import co.livorno.controlstock.catalogo.modelo.Variante;
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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Renglón inmutable del kardex (regla de oro 2): sin setters; los errores se corrigen con un AJUSTE. */
@Entity
@Immutable
@Table(name = "movimiento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false, updatable = false)
    private Variante variante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TipoMovimiento tipo;

    @Column(nullable = false, updatable = false)
    private int cantidad;

    @Column(name = "fisico_antes", nullable = false, updatable = false)
    private int fisicoAntes;

    @Column(name = "fisico_despues", nullable = false, updatable = false)
    private int fisicoDespues;

    @Column(name = "reservado_antes", nullable = false, updatable = false)
    private int reservadoAntes;

    @Column(name = "reservado_despues", nullable = false, updatable = false)
    private int reservadoDespues;

    /** Solo en ENTRADA. */
    @Column(name = "costo_unitario", precision = 12, scale = 2, updatable = false)
    private BigDecimal costoUnitario;

    /** Null = proceso del sistema. */
    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @Column(name = "referencia_tipo", length = 20, updatable = false)
    private String referenciaTipo;

    @Column(name = "referencia_id", updatable = false)
    private Long referenciaId;

    @Column(length = 255, updatable = false)
    private String observacion;

    /**
     * Fábrica: calcula los saldos "después" desde los "antes" y el efecto del tipo. No valida contra el stock real
     * (eso es de MovimientoService); solo la coherencia del propio registro.
     */
    public static Movimiento de(Variante variante, TipoMovimiento tipo, int cantidad, int fisicoAntes,
            int reservadoAntes, BigDecimal costoUnitario, Long usuarioId, LocalDateTime fecha,
            String referenciaTipo, Long referenciaId, String observacion) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        if ((tipo == TipoMovimiento.ENTRADA) != (costoUnitario != null)) {
            throw new IllegalArgumentException("El costo unitario es obligatorio solo en ENTRADA");
        }
        if (costoUnitario != null && costoUnitario.signum() < 0) {
            throw new IllegalArgumentException("El costo unitario no puede ser negativo");
        }
        if ((referenciaTipo == null) != (referenciaId == null)) {
            throw new IllegalArgumentException("La referencia requiere tipo e id");
        }
        EfectoMovimiento efecto = tipo.efecto(cantidad);
        Movimiento m = new Movimiento();
        m.variante = variante;
        m.tipo = tipo;
        m.cantidad = cantidad;
        m.fisicoAntes = fisicoAntes;
        m.fisicoDespues = fisicoAntes + efecto.deltaFisico();
        m.reservadoAntes = reservadoAntes;
        m.reservadoDespues = reservadoAntes + efecto.deltaReservado();
        m.costoUnitario = costoUnitario == null ? null : costoUnitario.setScale(2, RoundingMode.HALF_UP);
        m.usuarioId = usuarioId;
        m.fecha = fecha;
        m.referenciaTipo = referenciaTipo;
        m.referenciaId = referenciaId;
        m.observacion = observacion;
        return m;
    }
}
