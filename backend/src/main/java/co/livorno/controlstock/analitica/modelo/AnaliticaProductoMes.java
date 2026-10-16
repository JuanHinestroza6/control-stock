package co.livorno.controlstock.analitica.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * Serie mensual de un producto en el último cálculo: observada, disponibilidad, corregida y pronósticos del backtest
 * (ADR-010). Solo se conserva la última ejecución.
 */
@Entity
@Table(name = "analitica_producto_mes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnaliticaProductoMes implements Persistable<AnaliticaProductoMesId> {

    @EmbeddedId
    private AnaliticaProductoMesId id;

    @Column(name = "ejecucion_id", nullable = false)
    private Long ejecucionId;

    @Column(name = "unidades_observadas", nullable = false)
    private int unidadesObservadas;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal disponibilidad;

    @Column(name = "unidades_corregidas", nullable = false, precision = 10, scale = 2)
    private BigDecimal unidadesCorregidas;

    @Column(name = "backtest_ses", precision = 10, scale = 2)
    private BigDecimal backtestSes;

    @Column(name = "backtest_hw", precision = 10, scale = 2)
    private BigDecimal backtestHw;

    @Transient
    private boolean nueva = true;

    public AnaliticaProductoMes(Long productoId, LocalDate periodo, Long ejecucionId, int unidadesObservadas,
            BigDecimal disponibilidad, BigDecimal unidadesCorregidas, BigDecimal backtestSes, BigDecimal backtestHw) {
        this.id = new AnaliticaProductoMesId(productoId, periodo);
        this.ejecucionId = ejecucionId;
        this.unidadesObservadas = unidadesObservadas;
        this.disponibilidad = disponibilidad;
        this.unidadesCorregidas = unidadesCorregidas;
        this.backtestSes = backtestSes;
        this.backtestHw = backtestHw;
    }

    public Long getProductoId() {
        return id.productoId();
    }

    public LocalDate getPeriodo() {
        return id.periodo();
    }

    @Override
    public boolean isNew() {
        return nueva;
    }

    @PostLoad
    @PostPersist
    void marcarPersistida() {
        this.nueva = false;
    }
}
