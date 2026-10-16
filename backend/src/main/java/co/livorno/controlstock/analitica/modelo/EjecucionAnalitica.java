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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Registro de una ejecucion de la analitica (nocturna, manual o de la semilla): trazabilidad, "ultima ejecucion" de la
 * interfaz y metrica de alertas (ADR-009). Sin setters: el ciclo es {@link #iniciar} y luego {@link #terminar} o
 * {@link #fallar}.
 */
@Entity
@Table(name = "ejecucion_analitica")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EjecucionAnalitica {

    private static final int LARGO_MENSAJE = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private OrigenEjecucion origen;

    /** Solo en ejecuciones MANUAL. */
    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;

    @Column(name = "iniciada_en", nullable = false, updatable = false)
    private LocalDateTime iniciadaEn;

    @Column(name = "terminada_en")
    private LocalDateTime terminadaEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoEjecucion estado;

    @Column(name = "fecha_corte", nullable = false, updatable = false)
    private LocalDate fechaCorte;

    @Column(name = "variantes_analizadas")
    private Integer variantesAnalizadas;

    @Column(name = "clase_a")
    private Integer claseA;

    @Column(name = "clase_b")
    private Integer claseB;

    @Column(name = "clase_c")
    private Integer claseC;

    @Column(name = "con_punto_reorden")
    private Integer conPuntoReorden;

    @Column(name = "alertas_evaluadas")
    private Integer alertasEvaluadas;

    @Column(name = "alertas_fallidas")
    private Integer alertasFallidas;

    @Column(name = "variantes_activas")
    private Integer variantesActivas;

    @Column(name = "en_alerta_stock")
    private Integer enAlertaStock;

    @Column(name = "sin_rotacion_activas")
    private Integer sinRotacionActivas;

    @Column(name = "duracion_ms")
    private Long duracionMs;

    @Column(length = LARGO_MENSAJE)
    private String mensaje;

    /** Fase 6 (ADR-010): productos con pronóstico, por método, y WAPE global. Null si el pronóstico no corrió. */
    @Column(name = "productos_pronosticados")
    private Integer productosPronosticados;

    @Column(name = "con_holt_winters")
    private Integer conHoltWinters;

    @Column(name = "con_ses")
    private Integer conSes;

    @Column(name = "wape_global", precision = 7, scale = 4)
    private BigDecimal wapeGlobal;

    /** Variantes que estarían en alerta de stock sin contar lo que viene en camino (métrica contrafactual). */
    @Column(name = "en_alerta_sin_transito")
    private Integer enAlertaSinTransito;

    @Column(name = "con_pedido_en_camino")
    private Integer conPedidoEnCamino;

    private EjecucionAnalitica(OrigenEjecucion origen, Long usuarioId, LocalDateTime ahora, LocalDate fechaCorte) {
        this.origen = origen;
        this.usuarioId = usuarioId;
        this.iniciadaEn = ahora;
        this.fechaCorte = fechaCorte;
        this.estado = EstadoEjecucion.EN_CURSO;
    }

    /** Abre una ejecucion EN_CURSO. {@code usuarioId} es obligatorio si y solo si el origen es MANUAL. */
    public static EjecucionAnalitica iniciar(OrigenEjecucion origen, Long usuarioId, LocalDateTime ahora,
            LocalDate fechaCorte) {
        if ((origen == OrigenEjecucion.MANUAL) != (usuarioId != null)) {
            throw new IllegalArgumentException("El usuario es obligatorio solo en ejecuciones MANUAL");
        }
        return new EjecucionAnalitica(origen, usuarioId, ahora, fechaCorte);
    }

    /** Cierra la ejecucion como EXITOSA con sus conteos y la metrica de alertas. */
    public void terminar(LocalDateTime ahora, ResultadoEjecucion resultado) {
        cerrar(ahora, EstadoEjecucion.EXITOSA);
        this.variantesAnalizadas = resultado.variantesAnalizadas();
        this.claseA = resultado.claseA();
        this.claseB = resultado.claseB();
        this.claseC = resultado.claseC();
        this.conPuntoReorden = resultado.conPuntoReorden();
        this.alertasEvaluadas = resultado.alertasEvaluadas();
        this.alertasFallidas = resultado.alertasFallidas();
        this.variantesActivas = resultado.variantesActivas();
        this.enAlertaStock = resultado.enAlertaStock();
        this.sinRotacionActivas = resultado.sinRotacionActivas();
    }

    /** Cierra la ejecucion como FALLIDA; el mensaje es generico (sin stacktrace) y se recorta a 255 caracteres. */
    public void fallar(LocalDateTime ahora, String mensaje) {
        cerrar(ahora, EstadoEjecucion.FALLIDA);
        this.mensaje = mensaje == null ? null
                : mensaje.length() <= LARGO_MENSAJE ? mensaje : mensaje.substring(0, LARGO_MENSAJE);
    }

    /**
     * Cierra como FALLIDA (fallo el calculo ABC + ROP) conservando lo que si se midio: las alertas re-evaluadas y la
     * metrica. Los conteos del calculo (analizadas, clases, con ROP) quedan null.
     */
    public void fallar(LocalDateTime ahora, String mensaje, ResultadoEjecucion medido) {
        fallar(ahora, mensaje);
        this.alertasEvaluadas = medido.alertasEvaluadas();
        this.alertasFallidas = medido.alertasFallidas();
        this.variantesActivas = medido.variantesActivas();
        this.enAlertaStock = medido.enAlertaStock();
        this.sinRotacionActivas = medido.sinRotacionActivas();
    }

    /** Registra el resultado del paso de pronóstico (wapeGlobal puede ser null). */
    public void registrarPronostico(int productosPronosticados, int conHoltWinters, int conSes,
            BigDecimal wapeGlobal) {
        this.productosPronosticados = productosPronosticados;
        this.conHoltWinters = conHoltWinters;
        this.conSes = conSes;
        this.wapeGlobal = wapeGlobal;
    }

    /** Registra la métrica contrafactual de alertas y las variantes con pedido en camino (Fase 6). */
    public void registrarPosicion(int enAlertaSinTransito, int conPedidoEnCamino) {
        this.enAlertaSinTransito = enAlertaSinTransito;
        this.conPedidoEnCamino = conPedidoEnCamino;
    }

    private void cerrar(LocalDateTime ahora, EstadoEjecucion nuevo) {
        if (estado != EstadoEjecucion.EN_CURSO) {
            throw new IllegalStateException("La ejecucion " + id + " ya termino (" + estado + ")");
        }
        this.estado = nuevo;
        this.terminadaEn = ahora;
        this.duracionMs = Duration.between(iniciadaEn, ahora).toMillis();
    }

    /** Porcentaje de variantes activas con AGOTADO o STOCK_BAJO (escala 1, HALF_UP); null si no se midio. */
    public BigDecimal getPorcentajeAlertaStock() {
        if (variantesActivas == null || enAlertaStock == null || variantesActivas == 0) {
            return null;
        }
        return BigDecimal.valueOf(enAlertaStock).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(variantesActivas), 1, RoundingMode.HALF_UP);
    }
}
