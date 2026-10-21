package co.livorno.controlstock.compra.modelo;

import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Plan de compras (ADR-010). Nunca toca el stock: recibir lo hace el servicio vía OperacionInventarioService. Las
 * transiciones exigen el estado correcto (si no, 409); el servicio bloquea la fila antes de invocarlas.
 */
@Entity
@Table(name = "plan_compra")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoPlan estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private OrigenPlan origen;

    @Column(length = 255)
    private String observacion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private Long creadoPor;

    @Column(name = "aprobado_en")
    private LocalDateTime aprobadoEn;

    @Column(name = "aprobado_por")
    private Long aprobadoPor;

    @Column(name = "recibido_en")
    private LocalDateTime recibidoEn;

    @Column(name = "recibido_por")
    private Long recibidoPor;

    @Column(name = "cancelado_en")
    private LocalDateTime canceladoEn;

    @Column(name = "cancelado_por")
    private Long canceladoPor;

    @Column(name = "motivo_cancelacion", length = 200)
    private String motivoCancelacion;

    /** Ejecución analítica de la que salió la sugerencia (null en planes manuales). */
    @Column(name = "ejecucion_id", updatable = false)
    private Long ejecucionId;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetallePlan> lineas = new ArrayList<>();

    public static PlanCompra manual(String observacion, LocalDateTime ahora, Long usuarioId) {
        return nuevo(OrigenPlan.MANUAL, observacion, null, ahora, usuarioId);
    }

    public static PlanCompra sugerido(String observacion, Long ejecucionId, LocalDateTime ahora, Long usuarioId) {
        return nuevo(OrigenPlan.SUGERIDO, observacion, ejecucionId, ahora, usuarioId);
    }

    private static PlanCompra nuevo(OrigenPlan origen, String observacion, Long ejecucionId, LocalDateTime ahora,
            Long usuarioId) {
        PlanCompra p = new PlanCompra();
        p.estado = EstadoPlan.BORRADOR;
        p.origen = origen;
        p.observacion = observacion;
        p.ejecucionId = ejecucionId;
        p.creadoEn = ahora;
        p.creadoPor = usuarioId;
        p.actualizadoEn = ahora;
        return p;
    }

    /** Número visible, p. ej. {@code PC-000012}. */
    public String numero() {
        return numero(id);
    }

    public static String numero(Long id) {
        return String.format("PC-%06d", id);
    }

    /** Agrega una línea (solo en BORRADOR). */
    public DetallePlan agregarLinea(Variante variante, Integer cantidadSugerida, int cantidadAprobada,
            BigDecimal costoEstimado, String justificacion) {
        exigirEstado(EstadoPlan.BORRADOR);
        DetallePlan d = DetallePlan.de(this, variante, cantidadSugerida, cantidadAprobada, costoEstimado,
                justificacion);
        lineas.add(d);
        return d;
    }

    /** Quita todas las líneas (solo en BORRADOR) para reemplazarlas. */
    public void vaciarLineas(LocalDateTime ahora) {
        exigirEstado(EstadoPlan.BORRADOR);
        lineas.clear();
        this.actualizadoEn = ahora;
    }

    public void actualizarObservacion(String observacion, LocalDateTime ahora) {
        exigirEstado(EstadoPlan.BORRADOR);
        this.observacion = observacion;
        this.actualizadoEn = ahora;
    }

    public void aprobar(LocalDateTime ahora, Long usuarioId) {
        exigirEstado(EstadoPlan.BORRADOR);
        this.estado = EstadoPlan.APROBADO;
        this.aprobadoEn = ahora;
        this.aprobadoPor = usuarioId;
        this.actualizadoEn = ahora;
    }

    /** BORRADOR o APROBADO a CANCELADO; el permiso por rol lo decide el endpoint. */
    public void cancelar(String motivo, LocalDateTime ahora, Long usuarioId) {
        if (estado != EstadoPlan.BORRADOR && estado != EstadoPlan.APROBADO) {
            throw yaEn();
        }
        this.estado = EstadoPlan.CANCELADO;
        this.motivoCancelacion = motivo;
        this.canceladoEn = ahora;
        this.canceladoPor = usuarioId;
        this.actualizadoEn = ahora;
    }

    public void recibir(LocalDateTime ahora, Long usuarioId) {
        exigirEstado(EstadoPlan.APROBADO);
        this.estado = EstadoPlan.RECIBIDO;
        this.recibidoEn = ahora;
        this.recibidoPor = usuarioId;
        this.actualizadoEn = ahora;
    }

    private void exigirEstado(EstadoPlan esperado) {
        if (estado != esperado) {
            throw yaEn();
        }
    }

    private ConflictoException yaEn() {
        return new ConflictoException("El plan " + numero() + " ya está " + estado + ".");
    }
}
