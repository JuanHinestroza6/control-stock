package co.livorno.controlstock.analitica.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * Instantánea del último pronóstico de un producto: método elegido, parámetros, WAPE de cada método y motivo por el
 * que Holt-Winters no compitió (ADR-010). Una fila por producto, se reemplaza en cada ejecución. El id es el del
 * producto (asignado), por eso implementa {@link Persistable}.
 */
@Entity
@Table(name = "analitica_producto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnaliticaProducto implements Persistable<Long> {

    @Id
    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "ejecucion_id", nullable = false)
    private Long ejecucionId;

    @Column(name = "meses_historia", nullable = false)
    private int mesesHistoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MetodoPronostico metodo;

    @Column(precision = 3, scale = 2)
    private BigDecimal alfa;

    @Column(precision = 3, scale = 2)
    private BigDecimal beta;

    @Column(precision = 3, scale = 2)
    private BigDecimal gamma;

    @Column(name = "wape_ses", precision = 7, scale = 4)
    private BigDecimal wapeSes;

    @Column(name = "wape_hw", precision = 7, scale = 4)
    private BigDecimal wapeHw;

    @Column(name = "error_wape", precision = 7, scale = 4)
    private BigDecimal errorWape;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_sin_hw", length = 20)
    private MotivoSinHoltWinters motivoSinHw;

    @Column(name = "unidades_observadas_12m", nullable = false)
    private int unidadesObservadas12m;

    @Column(name = "unidades_corregidas_12m", nullable = false, precision = 10, scale = 2)
    private BigDecimal unidadesCorregidas12m;

    @Column(name = "calculado_en", nullable = false)
    private LocalDateTime calculadoEn;

    @Transient
    private boolean nueva = true;

    public AnaliticaProducto(Long productoId, Long ejecucionId, int mesesHistoria, MetodoPronostico metodo,
            BigDecimal alfa, BigDecimal beta, BigDecimal gamma, BigDecimal wapeSes, BigDecimal wapeHw,
            BigDecimal errorWape, MotivoSinHoltWinters motivoSinHw, int unidadesObservadas12m,
            BigDecimal unidadesCorregidas12m, LocalDateTime calculadoEn) {
        this.productoId = productoId;
        this.ejecucionId = ejecucionId;
        this.mesesHistoria = mesesHistoria;
        this.metodo = metodo;
        this.alfa = alfa;
        this.beta = beta;
        this.gamma = gamma;
        this.wapeSes = wapeSes;
        this.wapeHw = wapeHw;
        this.errorWape = errorWape;
        this.motivoSinHw = motivoSinHw;
        this.unidadesObservadas12m = unidadesObservadas12m;
        this.unidadesCorregidas12m = unidadesCorregidas12m;
        this.calculadoEn = calculadoEn;
    }

    @Override
    public Long getId() {
        return productoId;
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
