package co.livorno.controlstock.analitica.modelo;

import co.livorno.controlstock.catalogo.modelo.Variante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Instantanea del ultimo calculo de ABC y punto de reorden de una variante activa (una fila por variante, se
 * reemplaza en cada ejecucion; ADR-009). Guarda los insumos para explicar el resultado. El id es el de la variante
 * (asignado), por eso implementa {@link Persistable}: evita un SELECT previo al insertar.
 */
@Entity
@Table(name = "analitica_variante")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnaliticaVariante implements Persistable<Long> {

    @Id
    @Column(name = "variante_id")
    private Long varianteId;

    /** Solo lectura (consultas con join fetch); la escritura usa {@link #varianteId}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variante_id", insertable = false, updatable = false)
    private Variante variante;

    @Column(name = "ejecucion_id", nullable = false)
    private Long ejecucionId;

    @Column(name = "unidades_netas", nullable = false)
    private int unidadesNetas;

    @Column(name = "ingreso_neto", nullable = false, precision = 14, scale = 2)
    private BigDecimal ingresoNeto;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal participacion;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal acumulado;

    @Column(nullable = false)
    private int posicion;

    @Column(name = "clase_abc", nullable = false, columnDefinition = "char(1)")
    private char claseAbc;

    @Column(name = "dias_historia", nullable = false)
    private int diasHistoria;

    @Column(name = "demanda_media", nullable = false, precision = 10, scale = 4)
    private BigDecimal demandaMedia;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal desviacion;

    @Column(name = "lead_time_dias", nullable = false)
    private int leadTimeDias;

    @Column(name = "nivel_servicio", nullable = false, precision = 4, scale = 3)
    private BigDecimal nivelServicio;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal z;

    @Column(name = "stock_seguridad", precision = 10, scale = 2)
    private BigDecimal stockSeguridad;

    @Column(name = "punto_reorden")
    private Integer puntoReorden;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_reorden", nullable = false, length = 12)
    private MetodoReorden metodoReorden;

    @Column(name = "calculado_en", nullable = false)
    private LocalDateTime calculadoEn;

    @Transient
    private boolean nueva = true;

    public AnaliticaVariante(Long varianteId, Long ejecucionId, int unidadesNetas, BigDecimal ingresoNeto,
            BigDecimal participacion, BigDecimal acumulado, int posicion, char claseAbc, int diasHistoria,
            BigDecimal demandaMedia, BigDecimal desviacion, int leadTimeDias, BigDecimal nivelServicio, BigDecimal z,
            BigDecimal stockSeguridad, Integer puntoReorden, MetodoReorden metodoReorden, LocalDateTime calculadoEn) {
        this.varianteId = varianteId;
        this.ejecucionId = ejecucionId;
        this.unidadesNetas = unidadesNetas;
        this.ingresoNeto = ingresoNeto;
        this.participacion = participacion;
        this.acumulado = acumulado;
        this.posicion = posicion;
        this.claseAbc = claseAbc;
        this.diasHistoria = diasHistoria;
        this.demandaMedia = demandaMedia;
        this.desviacion = desviacion;
        this.leadTimeDias = leadTimeDias;
        this.nivelServicio = nivelServicio;
        this.z = z;
        this.stockSeguridad = stockSeguridad;
        this.puntoReorden = puntoReorden;
        this.metodoReorden = metodoReorden;
        this.calculadoEn = calculadoEn;
    }

    @Override
    public Long getId() {
        return varianteId;
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
