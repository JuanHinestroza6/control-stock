package co.livorno.controlstock.catalogo.modelo;

import co.livorno.controlstock.comun.modelo.EntidadAuditable;
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
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "producto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Producto extends EntidadAuditable {

    public static final int LEAD_TIME_POR_DEFECTO = 15;
    public static final int LOTE_MINIMO_POR_DEFECTO = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Inmutable tras la creación; se guarda en mayúsculas. */
    @Column(nullable = false, unique = true, length = 20, updatable = false)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(name = "precio_compra", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioCompra;

    @Column(name = "precio_venta", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioVenta;

    @Column(name = "lead_time_dias", nullable = false)
    private int leadTimeDias;

    @Column(name = "lote_minimo", nullable = false)
    private int loteMinimo;

    @Column(nullable = false)
    private boolean activo = true;

    /** leadTimeDias y loteMinimo nulos toman los valores por defecto del dominio (15 y 1). */
    public Producto(String codigo, String nombre, Categoria categoria, BigDecimal precioCompra,
            BigDecimal precioVenta, Integer leadTimeDias, Integer loteMinimo) {
        this.codigo = codigo.strip().toUpperCase(Locale.ROOT);
        this.activo = true;
        actualizarDatos(nombre, categoria, precioCompra, precioVenta,
                leadTimeDias != null ? leadTimeDias : LEAD_TIME_POR_DEFECTO,
                loteMinimo != null ? loteMinimo : LOTE_MINIMO_POR_DEFECTO);
    }

    public void actualizarDatos(String nombre, Categoria categoria, BigDecimal precioCompra,
            BigDecimal precioVenta, int leadTimeDias, int loteMinimo) {
        this.nombre = nombre.strip();
        this.categoria = categoria;
        this.precioCompra = precioCompra.setScale(2, RoundingMode.HALF_UP);
        this.precioVenta = precioVenta.setScale(2, RoundingMode.HALF_UP);
        this.leadTimeDias = leadTimeDias;
        this.loteMinimo = loteMinimo;
    }

    /** Último costo de compra conocido; lo actualiza el registro de entradas de inventario. */
    public void actualizarPrecioCompra(BigDecimal precioCompra) {
        if (precioCompra.signum() < 0) {
            throw new IllegalArgumentException("El precio de compra no puede ser negativo");
        }
        this.precioCompra = precioCompra.setScale(2, RoundingMode.HALF_UP);
    }

    public void activar() {
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }
}
