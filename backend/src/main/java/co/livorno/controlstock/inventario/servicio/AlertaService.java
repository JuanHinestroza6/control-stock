package co.livorno.controlstock.inventario.servicio;

import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.inventario.repositorio.MovimientoRepository;
import org.springframework.beans.factory.annotation.Value;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.inventario.modelo.TipoAlerta;
import co.livorno.controlstock.inventario.repositorio.AlertaRepository;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.web.Ordenamiento;
import co.livorno.controlstock.inventario.dto.AlertaResponse;
import co.livorno.controlstock.inventario.dto.ResumenAlertasResponse;
import co.livorno.controlstock.inventario.modelo.Alerta;
import co.livorno.controlstock.inventario.modelo.EstadoAlerta;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evalúa las alertas de una variante. AGOTADO, SIN_ROTACION y STOCK_BAJO son excluyentes con esa prioridad: AGOTADO si
 * disponible = 0; SIN_ROTACION si el producto no vende hace N días (ADR-009); STOCK_BAJO si 0 &lt; disponible y la
 * posición (disponible + en camino, ADR-010) es &le; umbral. AGOTADO sigue sobre el disponible.
 */
@Service
public class AlertaService {

    private final VarianteRepository variantes;
    private final AlertaRepository alertas;
    private final Clock clock;
    private final UsuarioRepository usuarios;
    private final MovimientoRepository movimientos;
    private final int diasSinRotacion;
    private final ConsultaPedidosEnCamino pedidos;

    public AlertaService(VarianteRepository variantes, AlertaRepository alertas, Clock clock,
            UsuarioRepository usuarios, MovimientoRepository movimientos, ConsultaPedidosEnCamino pedidos,
            @Value("${app.alertas.sin-rotacion-dias:60}") int diasSinRotacion) {
        this.variantes = variantes;
        this.alertas = alertas;
        this.clock = clock;
        this.usuarios = usuarios;
        this.movimientos = movimientos;
        this.pedidos = pedidos;
        this.diasSinRotacion = diasSinRotacion;
    }

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

    /**
     * Alertas de stock ACTIVAS más urgentes para el panel de inicio: primero AGOTADO por mayor umbral (más demanda
     * esperada: un agotado con umbral 0 no tiene demanda) y antigüedad, luego STOCK_BAJO por menor cobertura de la
     * posición ((disponible + en camino) / umbral, la misma posición que abre la alerta, ADR-010) y antigüedad.
     * SIN_ROTACION no es una urgencia de stock y no entra. 400 si {@code limite} no está entre 1 y
     * {@value #LIMITE_URGENTES_MAXIMO}.
     */
    @Transactional(readOnly = true)
    public List<AlertaResponse> urgentes(Integer limite) {
        int n = limite != null ? limite : LIMITE_URGENTES_POR_DEFECTO;
        if (n < 1 || n > LIMITE_URGENTES_MAXIMO) {
            throw new SolicitudInvalidaException("limite",
                    "El límite debe estar entre 1 y " + LIMITE_URGENTES_MAXIMO + ".");
        }
        List<Alerta> agotadas = alertas.buscar(EstadoAlerta.ACTIVA, TipoAlerta.AGOTADO, Pageable.unpaged()).getContent();
        List<Alerta> bajas = alertas.buscar(EstadoAlerta.ACTIVA, TipoAlerta.STOCK_BAJO, Pageable.unpaged()).getContent();
        Map<Long, Long> enCamino = bajas.isEmpty() ? Map.of() : pedidos.enCaminoPorVariante();
        return Stream.concat(agotadas.stream().sorted(POR_DEMANDA), bajas.stream().sorted(porCobertura(enCamino)))
                .limit(n)
                .map(a -> AlertaResponse.de(a, null))
                .toList();
    }

    static final int LIMITE_URGENTES_POR_DEFECTO = 5;
    static final int LIMITE_URGENTES_MAXIMO = 20;

    /** Umbral descendente, luego la más antigua. */
    private static final Comparator<Alerta> POR_DEMANDA = Comparator
            .comparingLong((Alerta a) -> umbral(a.getVariante())).reversed()
            .thenComparing(Alerta::getCreadaEn).thenComparing(Alerta::getId);

    /** posición / umbral ascendente sin flotantes (p1·u2 vs p2·u1, umbral mínimo 1), luego la más antigua. */
    private static Comparator<Alerta> porCobertura(Map<Long, Long> enCamino) {
        ToLongFunction<Alerta> posicion = a -> a.getVariante().getStockDisponible()
                + enCamino.getOrDefault(a.getVariante().getId(), 0L);
        return ((Comparator<Alerta>) (a, b) -> Long.compare(
                posicion.applyAsLong(a) * Math.max(1, umbral(b.getVariante())),
                posicion.applyAsLong(b) * Math.max(1, umbral(a.getVariante()))))
                .thenComparing(Alerta::getCreadaEn).thenComparing(Alerta::getId);
    }

    /** Mismo umbral que muestra {@link AlertaResponse}. */
    private static long umbral(Variante v) {
        return v.getPuntoReorden() != null ? v.getPuntoReorden() : v.getStockMinimo();
    }

    @Transactional(readOnly = true)
    public ResumenAlertasResponse resumen() {
        long agotado = 0;
        long bajo = 0;
        long sinRotacion = 0;
        long total = 0;
        for (Object[] fila : alertas.contarActivasPorTipo()) {
            long n = (Long) fila[1];
            total += n;
            if (fila[0] == TipoAlerta.AGOTADO) {
                agotado = n;
            } else if (fila[0] == TipoAlerta.STOCK_BAJO) {
                bajo = n;
            } else if (fila[0] == TipoAlerta.SIN_ROTACION) {
                sinRotacion = n;
            }
        }
        return new ResumenAlertasResponse(total, agotado, bajo, sinRotacion);
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
     * o NOT_SUPPORTED ahí). Idempotente: reevaluar sin cambios solo refresca {@code revisadaEn}. También lo usa la
     * re-evaluación nocturna (una transacción por variante).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void evaluar(Long varianteId) {
        evaluarConPosicion(varianteId, null);
    }

    /**
     * Igual que {@link #evaluar(Long)} pero con las unidades en camino ya conocidas: la re-evaluación masiva las trae
     * en lote (una consulta para todas las variantes) en vez de consultar variante por variante.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void evaluar(Long varianteId, long enCamino) {
        evaluarConPosicion(varianteId, enCamino);
    }

    private void evaluarConPosicion(Long varianteId, Long enCaminoConocido) {
        // Bloquea la variante: dos evaluaciones simultáneas se serializan y la última ve el stock final confirmado.
        // READ_COMMITTED: cada lectura ve lo último confirmado y no hay bloqueos de hueco (menos interbloqueos).
        Variante v = variantes.bloquearPorId(varianteId).orElse(null);
        if (v == null) {
            return;
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        Producto producto = v.getProducto();
        boolean activa = v.isActivo() && producto.isActivo();
        long enCamino = !activa ? 0L : enCaminoConocido != null ? enCaminoConocido : pedidos.enCamino(varianteId);

        // Una venta reciente del producto cierra la SIN_ROTACION de todas sus variantes (también al anular una venta
        // se recalcula: la anulada ya no cuenta).
        boolean ventaReciente = activa && ventaReciente(producto, ahora);
        TipoAlerta vigente = aplicar(varianteId, v, producto, activa, ventaReciente, enCamino, ahora);
        // Carrera con la venta de otra talla: si ella confirmó y cerró las SIN_ROTACION del producto antes de que
        // esta transacción insertara la suya, nadie la cerraría. Se relee lo confirmado tras abrirla y, si ya hay
        // venta, se corrige. Queda una ventana de milisegundos entre esta relectura y el commit (ver ADR-009).
        if (vigente == TipoAlerta.SIN_ROTACION && ventaReciente(producto, ahora)) {
            aplicar(varianteId, v, producto, activa, true, enCamino, ahora);
        }
    }

    private TipoAlerta aplicar(Long varianteId, Variante v, Producto producto, boolean activa, boolean ventaReciente,
            long enCamino, LocalDateTime ahora) {
        if (ventaReciente) {
            cerrarSinRotacionDeProducto(producto.getId(), ahora);
        }
        TipoAlerta vigente = activa ? calcularTipo(v, producto, ahora, ventaReciente, enCamino) : null;
        for (TipoAlerta tipo : TipoAlerta.values()) {
            if (tipo != vigente) {
                alertas.cerrarActiva(varianteId, tipo, ahora);
            }
        }
        // Variante o producto inactivo: vigente es null, así que se cierran todas las ACTIVAS y no se abre ninguna.
        if (vigente != null) {
            alertas.abrirOActualizar(varianteId, vigente.name(), ahora);
        }
        return vigente;
    }

    /** Lectura simple de las SIN_ROTACION activas del producto; solo si hay, un UPDATE por clave primaria. */
    private void cerrarSinRotacionDeProducto(Long productoId, LocalDateTime ahora) {
        List<Long> ids = alertas.idsSinRotacionActivas(productoId);
        if (!ids.isEmpty()) {
            alertas.cerrarPorIds(ids, ahora);
        }
    }

    /**
     * Tipo de alerta que debería estar ACTIVA hoy para la variante (null = ninguna), sin modificar nada. Lo usan el
     * verificador de la semilla y las pruebas.
     */
    @Transactional(readOnly = true)
    public TipoAlerta tipoEsperado(Long varianteId) {
        Variante v = variantes.findById(varianteId).orElse(null);
        if (v == null) {
            return null;
        }
        Producto producto = v.getProducto();
        if (!v.isActivo() || !producto.isActivo()) {
            return null;
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        return calcularTipo(v, producto, ahora, ventaReciente(producto, ahora), pedidos.enCamino(varianteId));
    }

    private boolean ventaReciente(Producto producto, LocalDateTime ahora) {
        return movimientos.ultimaVentaNetaDesde(producto.getId(), ahora.minusDays(diasSinRotacion)) != null;
    }

    /** Prioridad AGOTADO &gt; SIN_ROTACION &gt; STOCK_BAJO (excluyentes). Variante y producto ya verificados activos. */
    private TipoAlerta calcularTipo(Variante v, Producto producto, LocalDateTime ahora, boolean ventaReciente,
            long enCamino) {
        if (v.getStockDisponible() <= 0) {
            return TipoAlerta.AGOTADO;
        }
        if (!ventaReciente && sinRotacionAbrible(v, producto, ahora)) {
            return TipoAlerta.SIN_ROTACION;
        }
        return tipoStock(v, enCamino);
    }

    /** El producto existe hace al menos N días y la SIN_ROTACION no fue atendida a mano en este episodio. */
    private boolean sinRotacionAbrible(Variante v, Producto producto, LocalDateTime ahora) {
        LocalDateTime creado = producto.getCreadoEn();
        if (creado == null || creado.isAfter(ahora.minusDays(diasSinRotacion))) {
            return false;
        }
        // Episodio: desde (última venta o creación del producto) + N días. Atendida a mano dentro de él, no se reabre.
        LocalDateTime ultimaVenta = movimientos.ultimaVentaNeta(producto.getId());
        LocalDateTime base = ultimaVenta != null && ultimaVenta.isAfter(creado) ? ultimaVenta : creado;
        return !alertas.existeSinRotacionAtendidaAMano(v.getId(), base.plusDays(diasSinRotacion));
    }

    /** AGOTADO / STOCK_BAJO / null según el stock solamente (igual que {@code Variante.estadoStock()}). */
    static TipoAlerta tipoStock(Variante v) {
        return tipoStock(v, 0L);
    }

    /**
     * AGOTADO si disponible = 0 (el pedido en camino no se puede vender); STOCK_BAJO si 0 &lt; disponible y
     * {@code disponible + enCamino} &le; umbral (ADR-010).
     */
    static TipoAlerta tipoStock(Variante v, long enCamino) {
        int disponible = v.getStockDisponible();
        if (disponible <= 0) {
            return TipoAlerta.AGOTADO;
        }
        int umbral = v.getPuntoReorden() != null ? v.getPuntoReorden() : v.getStockMinimo();
        return disponible + enCamino <= umbral ? TipoAlerta.STOCK_BAJO : null;
    }
}
