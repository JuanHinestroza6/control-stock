package co.livorno.controlstock.inventario.modelo;

import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Apartado de una variante para un cliente (ADR-007). No toca el stock: eso lo hace MovimientoService. Las
 * transiciones exigen estado ACTIVA (si no, 409); el servicio bloquea la fila antes de invocarlas.
 */
@Entity
@Table(name = "reserva")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reserva {

    /** Tope de vida de una reserva desde su creación. */
    public static final int DIAS_MAXIMOS = 7;

    /** Valor que reemplaza los datos del cliente al anonimizar. */
    public static final String ANONIMIZADO = "[anonimizado]";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false, updatable = false)
    private Variante variante;

    @Column(nullable = false, updatable = false)
    private int cantidad;

    @Column(name = "cliente_nombre", nullable = false, length = 120)
    private String clienteNombre;

    @Column(name = "cliente_contacto", nullable = false, length = 120)
    private String clienteContacto;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @Column(name = "vence_en", nullable = false)
    private LocalDateTime venceEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoReserva estado;

    /** Venta que la convirtió; Long (no relación) para que inventario no dependa de venta. */
    @Column(name = "venta_id")
    private Long ventaId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "cerrada_en")
    private LocalDateTime cerradaEn;

    /** Null = sistema (vencimiento automático). */
    @Column(name = "cerrada_por")
    private Long cerradaPor;

    @Column(name = "motivo_cancelacion", length = 200)
    private String motivoCancelacion;

    public static Reserva de(Variante variante, int cantidad, String clienteNombre, String clienteContacto,
            LocalDateTime creadaEn, LocalDateTime venceEn, Long usuarioId) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        if (!venceEn.isAfter(creadaEn)) {
            throw new IllegalArgumentException("El vencimiento debe ser posterior a la creación");
        }
        Reserva r = new Reserva();
        r.variante = variante;
        r.cantidad = cantidad;
        r.clienteNombre = clienteNombre;
        r.clienteContacto = clienteContacto;
        r.creadaEn = creadaEn;
        r.venceEn = venceEn;
        r.estado = EstadoReserva.ACTIVA;
        r.usuarioId = usuarioId;
        return r;
    }

    /** Número visible, p. ej. {@code R-000012}. */
    public String numero() {
        return numero(id);
    }

    public static String numero(Long id) {
        return String.format("R-%06d", id);
    }

    public boolean estaActiva() {
        return estado == EstadoReserva.ACTIVA;
    }

    public void cancelar(String motivo, LocalDateTime ahora, Long usuarioId) {
        exigirActiva();
        this.estado = EstadoReserva.CANCELADA;
        this.motivoCancelacion = motivo;
        this.cerradaEn = ahora;
        this.cerradaPor = usuarioId;
    }

    /** Vencimiento automático: cerrada por el sistema. */
    public void vencer(LocalDateTime ahora) {
        exigirActiva();
        this.estado = EstadoReserva.VENCIDA;
        this.cerradaEn = ahora;
        this.cerradaPor = null;
    }

    public void convertir(Long ventaId, LocalDateTime ahora, Long usuarioId) {
        exigirActiva();
        this.estado = EstadoReserva.CONVERTIDA;
        this.ventaId = ventaId;
        this.cerradaEn = ahora;
        this.cerradaPor = usuarioId;
    }

    /** Nuevo vencimiento = ahora + horas; no puede pasar de creadaEn + 7 días (422). */
    public void extender(int horas, LocalDateTime ahora) {
        exigirActiva();
        LocalDateTime nuevo = ahora.plusHours(horas);
        if (nuevo.isAfter(creadaEn.plusDays(DIAS_MAXIMOS))) {
            throw new ExcepcionNegocio("Una reserva no puede durar más de " + DIAS_MAXIMOS + " días.");
        }
        this.venceEn = nuevo;
    }

    /**
     * Supresión de datos personales (Ley 1581, ADR-007): reemplaza nombre y contacto del cliente. Solo reservas ya
     * cerradas; una ACTIVA lanza 409. Idempotente.
     */
    public void anonimizar() {
        exigirNoActiva();
        this.clienteNombre = ANONIMIZADO;
        this.clienteContacto = ANONIMIZADO;
    }

    public boolean estaAnonimizada() {
        return ANONIMIZADO.equals(clienteNombre) && ANONIMIZADO.equals(clienteContacto);
    }

    private void exigirNoActiva() {
        if (estado == EstadoReserva.ACTIVA) {
            throw new ConflictoException("La reserva " + numero() + " sigue ACTIVA; no se puede anonimizar.");
        }
    }

    private void exigirActiva() {
        if (estado != EstadoReserva.ACTIVA) {
            throw new ConflictoException("La reserva " + numero() + " ya está " + estado + ".");
        }
    }
}
