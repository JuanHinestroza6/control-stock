package co.livorno.controlstock.inventario.modelo;

import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
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
 * Alerta de inventario. La columna generada {@code variante_activa} (índice único de alertas activas) no se mapea.
 * Las alertas automáticas las abre {@code AlertaService} con una sentencia atómica; esta entidad sirve para consultas
 * y para atender a mano.
 */
@Entity
@Table(name = "alerta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false, updatable = false)
    private Variante variante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15, updatable = false)
    private TipoAlerta tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoAlerta estado;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @Column(name = "revisada_en", nullable = false)
    private LocalDateTime revisadaEn;

    @Column(name = "atendida_en")
    private LocalDateTime atendidaEn;

    /** null con estado ATENDIDA = cerrada automáticamente por el sistema. */
    @Column(name = "atendida_por")
    private Long atendidaPor;

    public Alerta(Variante variante, TipoAlerta tipo, LocalDateTime ahora) {
        this.variante = variante;
        this.tipo = tipo;
        this.estado = EstadoAlerta.ACTIVA;
        this.creadaEn = ahora;
        this.revisadaEn = ahora;
    }

    /** Atiende a mano; una alerta ya ATENDIDA responde 409. */
    public void atender(Long usuarioId, LocalDateTime ahora) {
        if (estado == EstadoAlerta.ATENDIDA) {
            throw new ConflictoException("La alerta ya fue atendida.");
        }
        this.estado = EstadoAlerta.ATENDIDA;
        this.atendidaEn = ahora;
        this.atendidaPor = usuarioId;
    }
}
