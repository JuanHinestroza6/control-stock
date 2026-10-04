package co.livorno.controlstock.venta.servicio;

import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.web.Ordenamiento;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.Reserva;
import co.livorno.controlstock.inventario.repositorio.ReservaRepository;
import co.livorno.controlstock.venta.dto.ConvertirReservasRequest;
import co.livorno.controlstock.venta.dto.PrecioReservaRequest;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.servicio.MovimientoService;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import co.livorno.controlstock.venta.dto.AnularVentaRequest;
import co.livorno.controlstock.venta.dto.DevolucionResponse;
import co.livorno.controlstock.venta.dto.LineaVentaRequest;
import co.livorno.controlstock.venta.dto.RegistrarVentaRequest;
import co.livorno.controlstock.venta.dto.VentaDetalleResponse;
import co.livorno.controlstock.venta.dto.VentaResumenResponse;
import co.livorno.controlstock.venta.modelo.EstadoVenta;
import co.livorno.controlstock.venta.modelo.Venta;
import co.livorno.controlstock.venta.repositorio.DevolucionRepository;
import co.livorno.controlstock.venta.repositorio.VentaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VentaService {

    public static final Set<String> ORDEN_PERMITIDO = Set.of("fecha", "total");

    private final VentaRepository ventas;
    private final DevolucionRepository devoluciones;
    private final VarianteRepository variantes;
    private final UsuarioRepository usuarios;
    private final MovimientoService movimientos;
    private final UsuarioActual usuarioActual;
    private final Clock clock;
    private final ReservaRepository reservas;

    /** Todo o nada: cualquier error (stock, precio, variante) revierte la venta y los movimientos. */
    @Transactional
    public VentaDetalleResponse registrar(RegistrarVentaRequest request) {
        List<LineaVentaRequest> lineas = request.lineas();
        Set<Long> vistos = new HashSet<>();
        for (LineaVentaRequest l : lineas) {
            if (!vistos.add(l.varianteId())) {
                throw new SolicitudInvalidaException("La variante " + l.varianteId() + " está repetida en la venta.");
            }
        }
        // Orden estable por varianteId: evita interbloqueos entre ventas concurrentes.
        List<LineaVentaRequest> ordenadas = lineas.stream()
                .sorted(Comparator.comparing(LineaVentaRequest::varianteId)).toList();

        Long usuarioId = usuarioActual.id();
        Usuario vendedor = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", usuarioId));
        Venta venta = new Venta(vendedor, LocalDateTime.now(clock));

        for (LineaVentaRequest l : ordenadas) {
            Variante v = variantes.findById(l.varianteId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Variante", l.varianteId()));
            agregarLinea(venta, v, l.cantidad(), l.precioUnitario());
        }
        ventas.saveAndFlush(venta); // asigna el id para la referencia del kardex

        for (var d : venta.getDetalles()) {
            movimientos.registrar(new SolicitudMovimiento(d.getVariante().getId(), TipoMovimiento.VENTA,
                    d.getCantidad(), null, usuarioId, "VENTA", venta.getId(), null));
        }
        return VentaDetalleResponse.de(venta, puedeVerCosto());
    }

    /**
     * Construcción de una línea compartida por la venta directa y la conversión de reservas: variante y producto
     * activos (422); precioLista = precioVenta actual; precioUnitario opcional (0 &lt; precio &le; lista);
     * costoUnitario = precioCompra actual.
     */
    private void agregarLinea(Venta venta, Variante v, int cantidad, BigDecimal precioUnitario) {
        if (!v.isActivo() || !v.getProducto().isActivo()) {
            throw new ExcepcionNegocio("La variante " + v.getSku() + " está inactiva y no se puede vender.");
        }
        BigDecimal lista = v.getProducto().getPrecioVenta().setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitario = precioUnitario == null ? lista : precioUnitario.setScale(2, RoundingMode.HALF_UP);
        if (unitario.compareTo(lista) > 0) {
            throw new ExcepcionNegocio("El precio de " + v.getSku() + " no puede superar el de lista ("
                    + lista.toPlainString() + ").");
        }
        venta.agregarLinea(v, cantidad, lista, unitario, v.getProducto().getPrecioCompra());
    }

    /**
     * Convierte reservas ACTIVAS del mismo cliente en una venta (ADR-007). Bloquea las reservas en orden de id
     * (404 si falta alguna, 409 si alguna no está ACTIVA), exige el mismo cliente por contacto normalizado (422),
     * agrupa por variante, y por línea registra un VENTA_RESERVA (referencia VENTA/{id}). Todo o nada.
     */
    @Transactional
    public VentaDetalleResponse registrarDesdeReservas(ConvertirReservasRequest request) {
        List<Long> ids = request.reservaIds();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new SolicitudInvalidaException("Hay reservas repetidas en la solicitud.");
        }
        List<Reserva> reservasBloqueadas = reservas.bloquearVarias(ids);
        Set<Long> halladas = reservasBloqueadas.stream().map(Reserva::getId).collect(Collectors.toSet());
        for (Long id : ids) {
            if (!halladas.contains(id)) {
                throw new RecursoNoEncontradoException("Reserva", id);
            }
        }
        for (Reserva r : reservasBloqueadas) {
            if (!r.estaActiva()) {
                throw new ConflictoException("La reserva " + r.numero() + " ya está " + r.getEstado() + ".");
            }
        }
        if (reservasBloqueadas.stream().map(r -> contactoNormalizado(r.getClienteContacto())).distinct().count() > 1) {
            throw new ExcepcionNegocio("Las reservas deben ser del mismo cliente.");
        }

        // Agrupa por variante (orden por id: igual que la venta directa, evita interbloqueos).
        Map<Long, List<Reserva>> porVariante = new TreeMap<>();
        for (Reserva r : reservasBloqueadas) {
            porVariante.computeIfAbsent(r.getVariante().getId(), k -> new ArrayList<>()).add(r);
        }
        Map<Long, BigDecimal> precios = new HashMap<>();
        for (PrecioReservaRequest p : request.precios() == null ? List.<PrecioReservaRequest>of() : request.precios()) {
            if (!porVariante.containsKey(p.varianteId())) {
                throw new SolicitudInvalidaException("La variante " + p.varianteId()
                        + " no está en las reservas indicadas.");
            }
            if (precios.put(p.varianteId(), p.precioUnitario()) != null) {
                throw new SolicitudInvalidaException("El precio de la variante " + p.varianteId()
                        + " está repetido.");
            }
        }

        Long usuarioId = usuarioActual.id();
        Usuario vendedor = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", usuarioId));
        LocalDateTime ahora = LocalDateTime.now(clock);
        Venta venta = new Venta(vendedor, ahora);
        for (var e : porVariante.entrySet()) {
            Variante v = e.getValue().get(0).getVariante();
            int cantidad = e.getValue().stream().mapToInt(Reserva::getCantidad).sum();
            agregarLinea(venta, v, cantidad, precios.get(e.getKey()));
        }
        ventas.saveAndFlush(venta);

        for (var d : venta.getDetalles()) {
            String numeros = porVariante.get(d.getVariante().getId()).stream().map(Reserva::numero)
                    .collect(Collectors.joining(", ", "Reservas ", ""));
            movimientos.registrar(new SolicitudMovimiento(d.getVariante().getId(), TipoMovimiento.VENTA_RESERVA,
                    d.getCantidad(), null, usuarioId, "VENTA", venta.getId(), numeros));
        }
        reservasBloqueadas.forEach(r -> r.convertir(venta.getId(), ahora, usuarioId));
        reservas.flush();
        return VentaDetalleResponse.de(venta, puedeVerCosto(),
                reservasBloqueadas.stream().map(Reserva::numero).toList());
    }

    /** Minúsculas y sin espacios, para comparar el contacto del cliente. */
    private static String contactoNormalizado(String contacto) {
        return contacto.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Anula la venta (solo ADMIN, lo exige el controller): bloquea la fila, y por cada línea registra un movimiento
     * ANULACION_VENTA (+físico) con referencia VENTA/{id}. Ya anulada: 409; con devoluciones: 422.
     */
    @Transactional
    public VentaDetalleResponse anular(Long id, AnularVentaRequest request) {
        Venta venta = ventas.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Venta", id));
        Long usuarioId = usuarioActual.id();
        Usuario admin = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", usuarioId));

        venta.anular(admin, LocalDateTime.now(clock), request.motivo()); // 409 si ya está anulada
        if (devoluciones.existsByDetalleVentaVentaId(venta.getId())) {
            throw new ExcepcionNegocio("La venta " + venta.numero()
                    + " tiene devoluciones; corrige el stock con ajustes.");
        }
        venta.getDetalles().stream().sorted(Comparator.comparing(d -> d.getVariante().getId())).forEach(d ->
                movimientos.registrar(new SolicitudMovimiento(d.getVariante().getId(), TipoMovimiento.ANULACION_VENTA,
                        d.getCantidad(), null, usuarioId, "VENTA", venta.getId(), venta.getMotivoAnulacion())));
        ventas.saveAndFlush(venta);
        // Las reservas de origen siguen CONVERTIDAS: anular solo devuelve el físico (ADR-007).
        return VentaDetalleResponse.de(venta, true,
                reservas.findByVentaIdOrderById(id).stream().map(Reserva::numero).toList());
    }

    public Page<VentaResumenResponse> listar(LocalDate desde, LocalDate hasta, EstadoVenta estado,
            Pageable pageable) {
        Ordenamiento.validar(pageable, ORDEN_PERMITIDO);
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new SolicitudInvalidaException("La fecha final no puede ser anterior a la inicial.");
        }
        Specification<Venta> spec = (root, query, cb) -> cb.conjunction();
        if (desde != null) {
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("fecha"), desde.atStartOfDay()));
        }
        if (hasta != null) {
            spec = spec.and((r, q, cb) -> cb.lessThan(r.get("fecha"), hasta.plusDays(1).atStartOfDay()));
        }
        if (estado != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("estado"), estado));
        }
        return ventas.findAll(spec, pageable).map(VentaResumenResponse::de);
    }

    public VentaDetalleResponse obtener(Long id) {
        Venta v = ventas.conDetalle(id).orElseThrow(() -> new RecursoNoEncontradoException("Venta", id));
        Map<Long, List<DevolucionResponse>> porLinea = devoluciones.porVenta(id).stream()
                .collect(Collectors.groupingBy(d -> d.getDetalleVenta().getId(),
                        Collectors.mapping(DevolucionResponse::de, Collectors.toList())));
        List<String> origen = reservas.findByVentaIdOrderById(id).stream().map(Reserva::numero).toList();
        return VentaDetalleResponse.de(v, puedeVerCosto(), porLinea, origen);
    }

    /** El costo no se expone al rol VENDEDOR. */
    private boolean puedeVerCosto() {
        return !"VENDEDOR".equals(usuarioActual.obtener().rol());
    }
}
