package co.livorno.controlstock.venta.servicio;

import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.web.Ordenamiento;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.servicio.MovimientoService;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import co.livorno.controlstock.venta.dto.LineaVentaRequest;
import co.livorno.controlstock.venta.dto.RegistrarVentaRequest;
import co.livorno.controlstock.venta.dto.VentaDetalleResponse;
import co.livorno.controlstock.venta.dto.VentaResumenResponse;
import co.livorno.controlstock.venta.modelo.EstadoVenta;
import co.livorno.controlstock.venta.modelo.Venta;
import co.livorno.controlstock.venta.repositorio.VentaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
    private final VarianteRepository variantes;
    private final UsuarioRepository usuarios;
    private final MovimientoService movimientos;
    private final UsuarioActual usuarioActual;
    private final Clock clock;

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
            if (!v.isActivo() || !v.getProducto().isActivo()) {
                throw new ExcepcionNegocio("La variante " + v.getSku() + " está inactiva y no se puede vender.");
            }
            BigDecimal lista = v.getProducto().getPrecioVenta().setScale(2, RoundingMode.HALF_UP);
            BigDecimal unitario = l.precioUnitario() == null ? lista
                    : l.precioUnitario().setScale(2, RoundingMode.HALF_UP);
            if (unitario.compareTo(lista) > 0) {
                throw new ExcepcionNegocio("El precio de " + v.getSku() + " no puede superar el de lista ("
                        + lista.toPlainString() + ").");
            }
            venta.agregarLinea(v, l.cantidad(), lista, unitario, v.getProducto().getPrecioCompra());
        }
        ventas.saveAndFlush(venta); // asigna el id para la referencia del kardex

        for (var d : venta.getDetalles()) {
            movimientos.registrar(new SolicitudMovimiento(d.getVariante().getId(), TipoMovimiento.VENTA,
                    d.getCantidad(), null, usuarioId, "VENTA", venta.getId(), null));
        }
        return VentaDetalleResponse.de(venta, puedeVerCosto());
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
        return VentaDetalleResponse.de(v, puedeVerCosto());
    }

    /** El costo no se expone al rol VENDEDOR. */
    private boolean puedeVerCosto() {
        return !"VENDEDOR".equals(usuarioActual.obtener().rol());
    }
}
