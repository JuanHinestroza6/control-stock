package co.livorno.controlstock.compra.servicio;

import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.compra.dto.ActualizarPlanRequest;
import co.livorno.controlstock.compra.dto.CancelarPlanRequest;
import co.livorno.controlstock.compra.dto.CrearPlanRequest;
import co.livorno.controlstock.compra.dto.LineaPlanRequest;
import co.livorno.controlstock.compra.dto.PlanCompraDetalleResponse;
import co.livorno.controlstock.compra.dto.PlanCompraResumenResponse;
import co.livorno.controlstock.compra.modelo.DetallePlan;
import co.livorno.controlstock.compra.modelo.EstadoPlan;
import co.livorno.controlstock.compra.modelo.PlanCompra;
import co.livorno.controlstock.compra.repositorio.DetallePlanRepository;
import co.livorno.controlstock.compra.repositorio.EnCaminoVariante;
import co.livorno.controlstock.compra.repositorio.PlanCompraRepository;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.web.Ordenamiento;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan de compras (ADR-010): crear manual, editar BORRADOR, cancelar y consultar. Nunca toca stock. Toda transición
 * bloquea la fila del plan (PESSIMISTIC_WRITE) y verifica el estado; editar además compara la versión (409).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanCompraService {

    static final int MAX_LINEAS = 300;
    static final int MAX_OBSERVACION = 255;
    static final Set<String> ORDEN_PERMITIDO = Set.of("id", "creadoEn", "aprobadoEn", "estado", "origen");

    private final PlanCompraRepository planes;
    private final DetallePlanRepository detalles;
    private final VarianteRepository variantes;
    private final UsuarioRepository usuarios;
    private final UsuarioActual usuarioActual;
    private final Clock clock;

    @Transactional
    public PlanCompraDetalleResponse crear(CrearPlanRequest request) {
        String observacion = normalizarObservacion(request.observacion());
        validarLineas(request.lineas());
        LocalDateTime ahora = LocalDateTime.now(clock);
        PlanCompra plan = PlanCompra.manual(observacion, ahora, usuarioActual.id());
        agregarLineas(plan, request.lineas(), Map.of());
        planes.saveAndFlush(plan);
        return detalle(plan, ahora);
    }

    @Transactional
    public PlanCompraDetalleResponse actualizar(Long id, ActualizarPlanRequest request) {
        String observacion = normalizarObservacion(request.observacion());
        validarLineas(request.lineas());
        if (request.version() == null) {
            throw new SolicitudInvalidaException("version", "La versión del plan es obligatoria.");
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        PlanCompra plan = bloquear(id);
        if (plan.getEstado() != EstadoPlan.BORRADOR) {
            throw new ConflictoException("El plan " + plan.numero() + " ya está " + plan.getEstado() + ".");
        }
        if (request.version() != plan.getVersion()) {
            throw new ConflictoException("El plan " + plan.numero()
                    + " cambió mientras lo editabas. Recarga la página y vuelve a intentar.");
        }
        // Las variantes que siguen conservan la cantidad sugerida y la justificación originales.
        Map<Long, DetallePlan> anteriores = new HashMap<>();
        for (DetallePlan d : plan.getLineas()) {
            anteriores.put(d.getVariante().getId(), d);
        }
        Map<Long, Object[]> conservar = new HashMap<>();
        anteriores.forEach((varianteId, d) -> conservar.put(varianteId,
                new Object[] {d.getCantidadSugerida(), d.getJustificacion()}));
        plan.vaciarLineas(ahora);
        // Los DELETE de huérfanos deben ir antes de los INSERT por el índice único (plan, variante).
        planes.flush();
        plan.actualizarObservacion(observacion, ahora);
        agregarLineas(plan, request.lineas(), conservar);
        planes.saveAndFlush(plan);
        return detalle(plan, ahora);
    }

    @Transactional
    public PlanCompraDetalleResponse cancelar(Long id, CancelarPlanRequest request) {
        String motivo = request.motivo() == null ? "" : request.motivo().strip();
        if (motivo.length() < 5 || motivo.length() > 200) {
            throw new SolicitudInvalidaException("motivo", "El motivo debe tener entre 5 y 200 caracteres.");
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        PlanCompra plan = bloquear(id);
        var usuario = usuarioActual.obtener();
        // INVENTARIO solo descarta borradores (matriz de roles); sobre un plan ya final responde 409 la entidad.
        if (plan.getEstado() == EstadoPlan.APROBADO && "INVENTARIO".equals(usuario.rol())) {
            throw new AccessDeniedException("INVENTARIO no puede cancelar un plan aprobado");
        }
        plan.cancelar(motivo, ahora, usuario.id());
        planes.saveAndFlush(plan);
        // La publicación de PosicionCambiada al cancelar un APROBADO la agrega B7/B8 (el evento aún no existe).
        return detalle(plan, ahora);
    }

    public Page<PlanCompraResumenResponse> listar(EstadoPlan estado, Pageable pageable) {
        Ordenamiento.validar(pageable, ORDEN_PERMITIDO);
        LocalDateTime ahora = LocalDateTime.now(clock);
        Page<PlanCompra> pagina = planes.buscar(estado, pageable);
        Map<Long, String> nombres = nombres(pagina.getContent());
        return pagina.map(p -> PlanCompraResumenResponse.de(p, nombres, ahora));
    }

    public PlanCompraDetalleResponse obtener(Long id) {
        PlanCompra plan = planes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Plan de compra", id));
        return detalle(plan, LocalDateTime.now(clock));
    }

    // ---------- internos ----------

    private PlanCompra bloquear(Long id) {
        return planes.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Plan de compra", id));
    }

    private PlanCompraDetalleResponse detalle(PlanCompra plan, LocalDateTime ahora) {
        Map<Long, Long> enCamino = new HashMap<>();
        for (EnCaminoVariante e : detalles.enCaminoPorVariante()) {
            enCamino.put(e.varianteId(), e.unidades());
        }
        return PlanCompraDetalleResponse.de(plan, nombres(List.of(plan)), enCamino, ahora, null);
    }

    private Map<Long, String> nombres(List<PlanCompra> lista) {
        Set<Long> ids = new HashSet<>();
        for (PlanCompra p : lista) {
            for (Long u : new Long[] {p.getCreadoPor(), p.getAprobadoPor(), p.getCanceladoPor()}) {
                if (u != null) {
                    ids.add(u);
                }
            }
        }
        Map<Long, String> nombres = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Usuario u : usuarios.findAllById(ids)) {
                nombres.put(u.getId(), u.getNombre());
            }
        }
        return nombres;
    }

    /** Agrega las líneas ordenadas por id de variante; el costo por defecto es el precioCompra actual. */
    private void agregarLineas(PlanCompra plan, List<LineaPlanRequest> lineas, Map<Long, Object[]> conservar) {
        Map<Long, LineaPlanRequest> ordenadas = new TreeMap<>();
        for (LineaPlanRequest l : lineas) {
            ordenadas.put(l.varianteId(), l);
        }
        Map<Long, Variante> encontradas = new LinkedHashMap<>();
        for (Variante v : variantes.findAllById(ordenadas.keySet())) {
            encontradas.put(v.getId(), v);
        }
        for (Long varianteId : ordenadas.keySet()) {
            Variante v = encontradas.get(varianteId);
            if (v == null) {
                throw new RecursoNoEncontradoException("Variante", varianteId);
            }
            Producto p = v.getProducto();
            if (!v.isActivo() || !p.isActivo()) {
                throw new ExcepcionNegocio("La variante " + v.getSku() + " está inactiva y no puede estar en un plan.");
            }
            LineaPlanRequest l = ordenadas.get(varianteId);
            BigDecimal costo = l.costoEstimado() != null ? l.costoEstimado() : p.getPrecioCompra();
            Object[] previo = conservar.get(varianteId);
            Integer sugerida = previo == null ? null : (Integer) previo[0];
            String justificacion = previo == null ? null : (String) previo[1];
            plan.agregarLinea(v, sugerida, l.cantidad(), costo, justificacion);
        }
    }

    private static void validarLineas(List<LineaPlanRequest> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            throw new SolicitudInvalidaException("lineas", "El plan debe tener al menos una línea.");
        }
        if (lineas.size() > MAX_LINEAS) {
            throw new SolicitudInvalidaException("lineas", "El plan no puede tener más de " + MAX_LINEAS + " líneas.");
        }
        Set<Long> vistas = new HashSet<>();
        for (LineaPlanRequest l : lineas) {
            if (l == null || l.varianteId() == null) {
                throw new SolicitudInvalidaException("lineas", "Cada línea necesita una variante.");
            }
            if (!vistas.add(l.varianteId())) {
                throw new SolicitudInvalidaException("lineas",
                        "La variante " + l.varianteId() + " está repetida en el plan.");
            }
            if (l.cantidad() == null || l.cantidad() < 1 || l.cantidad() > DetallePlan.CANTIDAD_MAXIMA) {
                throw new SolicitudInvalidaException("lineas",
                        "La cantidad debe estar entre 1 y " + DetallePlan.CANTIDAD_MAXIMA + ".");
            }
            if (l.costoEstimado() != null && l.costoEstimado().signum() < 0) {
                throw new SolicitudInvalidaException("lineas", "El costo estimado no puede ser negativo.");
            }
        }
    }

    private static String normalizarObservacion(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.strip();
        if (limpio.length() > MAX_OBSERVACION) {
            throw new SolicitudInvalidaException("observacion",
                    "La observación no puede superar " + MAX_OBSERVACION + " caracteres.");
        }
        return limpio;
    }
}
