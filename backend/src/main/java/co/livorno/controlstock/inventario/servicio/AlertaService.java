package co.livorno.controlstock.inventario.servicio;

import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.inventario.modelo.TipoAlerta;
import co.livorno.controlstock.inventario.repositorio.AlertaRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evalúa las alertas de stock de una variante. AGOTADO y STOCK_BAJO son excluyentes, igual que
 * {@code Variante.estadoStock()}: AGOTADO si disponible = 0; STOCK_BAJO si 0 &lt; disponible &le; umbral.
 */
@Service
@RequiredArgsConstructor
public class AlertaService {

    private final VarianteRepository variantes;
    private final AlertaRepository alertas;
    private final Clock clock;

    /**
     * Corre en su propia transacción porque se invoca desde un listener AFTER_COMMIT (Spring solo admite REQUIRES_NEW
     * o NOT_SUPPORTED ahí). Idempotente: reevaluar sin cambios solo refresca {@code revisadaEn}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluar(Long varianteId) {
        Variante v = variantes.findById(varianteId).orElse(null);
        if (v == null) {
            return;
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        TipoAlerta vigente = tipoVigente(v);

        for (TipoAlerta tipo : new TipoAlerta[] {TipoAlerta.AGOTADO, TipoAlerta.STOCK_BAJO}) {
            if (tipo != vigente) {
                alertas.cerrarActiva(varianteId, tipo, ahora);
            }
        }
        // Una variante inactiva no genera alertas nuevas (sí se cierran las que dejaron de aplicar).
        if (vigente != null && v.isActivo()) {
            alertas.abrirOActualizar(varianteId, vigente.name(), ahora);
        }
    }

    /** null = stock OK. */
    static TipoAlerta tipoVigente(Variante v) {
        int disponible = v.getStockDisponible();
        if (disponible <= 0) {
            return TipoAlerta.AGOTADO;
        }
        int umbral = v.getPuntoReorden() != null ? v.getPuntoReorden() : v.getStockMinimo();
        return disponible <= umbral ? TipoAlerta.STOCK_BAJO : null;
    }
}
