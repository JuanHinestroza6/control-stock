package co.livorno.controlstock.catalogo.modelo;

import co.livorno.controlstock.comun.modelo.EntidadAuditable;
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
import jakarta.persistence.Version;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Unidad de inventario (SKU). Regla de oro 1: no existe ningún setter ni método que cambie
 * {@code stockFisico} o {@code stockReservado}; nacen en 0/0 y solo {@code MovimientoService} (Fase 2)
 * podrá modificarlos mediante un único método de aplicación de movimientos.
 */
@Entity
@Table(name = "variante")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Variante extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private Producto producto;

    /** Inmutable; lo genera el servidor. */
    @Column(nullable = false, unique = true, length = 40, updatable = false)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5, updatable = false)
    private Talla talla;

    /** Inmutable; normalizado en mayúsculas. */
    @Column(nullable = false, length = 30, updatable = false)
    private String color;

    @Column(name = "stock_fisico", nullable = false)
    private int stockFisico;

    @Column(name = "stock_reservado", nullable = false)
    private int stockReservado;

    @Column(name = "stock_minimo", nullable = false)
    private int stockMinimo;

    /** Lo calculará el módulo de analítica. */
    @Column(name = "punto_reorden")
    private Integer puntoReorden;

    /** 'A', 'B' o 'C'; lo calculará el módulo de analítica. */
    @Column(name = "clase_abc", columnDefinition = "char(1)")
    private Character claseAbc;

    @Column(nullable = false)
    private boolean activo = true;

    @Version
    @Column(nullable = false)
    private long version;

    public Variante(Producto producto, String sku, Talla talla, String color, int stockMinimo) {
        if (stockMinimo < 0) {
            throw new IllegalArgumentException("El stock mínimo no puede ser negativo");
        }
        this.producto = producto;
        this.sku = sku;
        this.talla = talla;
        this.color = color.strip().toUpperCase(Locale.ROOT);
        this.stockMinimo = stockMinimo;
        this.stockFisico = 0;
        this.stockReservado = 0;
        this.activo = true;
    }

    public int getStockDisponible() {
        return stockFisico - stockReservado;
    }

    /** AGOTADO si disponible = 0; BAJO si disponible <= ROP (o stockMínimo sin ROP); si no, OK. */
    public EstadoStock estadoStock() {
        int disponible = getStockDisponible();
        if (disponible <= 0) {
            return EstadoStock.AGOTADO;
        }
        int umbral = puntoReorden != null ? puntoReorden : stockMinimo;
        return disponible <= umbral ? EstadoStock.BAJO : EstadoStock.OK;
    }

    /**
     * Único método que modifica el stock (regla de oro 1). Es público porque Variante y MovimientoService viven en
     * paquetes distintos; una regla ArchUnit garantiza que solo {@code MovimientoService} lo invoca (ADR-006).
     * Valida los invariantes: físico >= 0, reservado >= 0 y reservado <= físico; si se rompen no cambia nada.
     */
    public void aplicarMovimiento(int deltaFisico, int deltaReservado) {
        int fisico = Math.addExact(stockFisico, deltaFisico);
        int reservado = Math.addExact(stockReservado, deltaReservado);
        if (fisico < 0 || reservado < 0 || reservado > fisico) {
            throw new IllegalStateException("Movimiento inválido para la variante " + sku + ": físico " + fisico
                    + ", reservado " + reservado);
        }
        this.stockFisico = fisico;
        this.stockReservado = reservado;
    }

    public void actualizarStockMinimo(int stockMinimo) {
        if (stockMinimo < 0) {
            throw new IllegalArgumentException("El stock mínimo no puede ser negativo");
        }
        this.stockMinimo = stockMinimo;
    }

    public void activar() {
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }
}
