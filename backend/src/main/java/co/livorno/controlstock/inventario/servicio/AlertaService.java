package co.livorno.controlstock.inventario.servicio;

import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.inventario.modelo.TipoAlerta;
import co.livorno.controlstock.inventario.repositorio.AlertaRepository;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.web.Ordenamiento;
import co.livorno.controlstock.inventario.dto.AlertaResponse;
import co.livorno.controlstock.inventario.dto.ResumenAlertasResponse;
import co.livorno.controlstock.inventario.modelo.Alerta;
import co.livorno.controlstock.inventario.modelo.EstadoAlerta;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final UsuarioRepository usuarios;

    public static final Set<String> ORDEN_PERMITIDO = Set.of("creadaEn", "revisadaEn", "id");

    @Transactional(readOnly = true)
    public Page<AlertaResponse> listar(EstadoAlerta estado, TipoAlerta tipo, Pageable pageable) {
        Ordenamiento.validar(pageable, ORDEN_PERMITIDO);
        Page<Alerta> pagina = alertas.buscar(estado, tipo, pageable);
        // Una sola consulta para los nombres de quien atendió (sin N+1).
        Map<Long, String> nombres = usuarios.findAllById(pagina.stream().map(Alerta::getAtendidaPor)
                .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre, (a, b) -> a));
        return pagina.map(a -> AlertaResponse.de(a, a.getAtendidaPor() == null ? null : nombres.get(a.getAtendidaPor())));
    }

    @Transactional(readOnly = true)
    public ResumenAlertasResponse resumen() {
        long agotado = 0;
        long bajo = 0;
        long total = 0;
        for (Object[] fila : alertas.contarActivasPorTipo()) {
            long n = (Long) fila[1];
            total += n;
            if (fila[0] == TipoAlerta.AGOTADO) {
                agotado = n;
            } else if (fila[0] == TipoAlerta.STOCK_BAJO) {
                bajo = n;
            }
        }
        return new ResumenAlertasResponse(total, agotado, bajo);
    }

    /** 404 si no existe; 409 si ya estaba ATENDIDA. Bloquea la fila para que dos atenciones no se pisen. */
    @Transactional
    public AlertaResponse atender(Long id, Long usuarioId) {
        Alerta a = alertas.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Alerta", id));
        a.atender(usuarioId, LocalDateTime.now(clock));
        String nombre = usuarios.findById(usuarioId).map(Usuario::getNombre).orElse(null);
        return AlertaResponse.de(a, nombre);
    }

    /**
     * Corre en su propia transacción porque se invoca desde un listener AFTER_COMMIT (Spring solo admite REQUIRES_NEW
     * o NOT_SUPPORTED ahí). Idempotente: reevaluar sin cambios solo refresca {@code revisadaEn}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluar(Long varianteId) {
        // Bloquea la variante: dos evaluaciones simultáneas se serializan y la última ve el stock final confirmado.
        Variante v = variantes.bloquearPorId(varianteId).orElse(null);
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
