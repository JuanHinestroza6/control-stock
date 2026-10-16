package co.livorno.controlstock.analitica.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Cantidad proyectada de un mes (día 1) para un producto ({@code varianteId} null = total del producto) o para una
 * variante (ADR-010). Solo se conserva la última ejecución. Nunca toca stock. La columna generada
 * {@code variante_clave} (unicidad con variante nula) la calcula la base y no se mapea.
 */
@Entity
@Table(name = "pronostico")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pronostico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "producto_id", nullable = false, updatable = false)
    private Long productoId;

    @Column(name = "variante_id", updatable = false)
    private Long varianteId;

    @Column(nullable = false, updatable = false)
    private LocalDate periodo;

    @Column(name = "cantidad_proyectada", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidadProyectada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MetodoPronostico metodo;

    @Column(name = "error_wape", precision = 7, scale = 4)
    private BigDecimal errorWape;

    @Column(name = "calculado_en", nullable = false)
    private LocalDateTime calculadoEn;

    @Column(name = "ejecucion_id", nullable = false)
    private Long ejecucionId;

    public Pronostico(Long productoId, Long varianteId, LocalDate periodo, BigDecimal cantidadProyectada,
            MetodoPronostico metodo, BigDecimal errorWape, LocalDateTime calculadoEn, Long ejecucionId) {
        if (periodo.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("El periodo del pronóstico debe ser el día 1 del mes");
        }
        if (metodo == MetodoPronostico.SIN_HISTORIA) {
            throw new IllegalArgumentException("SIN_HISTORIA no genera filas de pronóstico");
        }
        if (cantidadProyectada.signum() < 0) {
            throw new IllegalArgumentException("La cantidad proyectada no puede ser negativa");
        }
        this.productoId = productoId;
        this.varianteId = varianteId;
        this.periodo = periodo;
        this.cantidadProyectada = cantidadProyectada;
        this.metodo = metodo;
        this.errorWape = errorWape;
        this.calculadoEn = calculadoEn;
        this.ejecucionId = ejecucionId;
    }
}
