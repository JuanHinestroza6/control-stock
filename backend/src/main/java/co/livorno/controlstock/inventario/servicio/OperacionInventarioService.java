package co.livorno.controlstock.inventario.servicio;

import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.AjusteRequest;
import co.livorno.controlstock.inventario.dto.EntradaRequest;
import co.livorno.controlstock.inventario.dto.LineaEntradaRequest;
import co.livorno.controlstock.inventario.dto.MovimientoResponse;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.Movimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entradas de mercancía y ajustes manuales; todo cambio de stock pasa por {@link MovimientoService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperacionInventarioService {

    private final VarianteRepository variantes;
    private final MovimientoService movimientos;
    private final UsuarioActual usuarioActual;

    /**
     * Todo o nada: valida todas las líneas antes de mover stock. Un ENTRADA por línea; el último costo mayor a cero
     * pasa a ser {@code precioCompra} del producto (un costo 0 se registra pero no lo cambia).
     */
    @Transactional
    public List<MovimientoResponse> registrarEntrada(EntradaRequest req) {
        String observacion = req.observacion() == null || req.observacion().isBlank() ? null : req.observacion().strip();
        return procesarEntrada(req.lineas(), observacion, null, null, true);
    }

    /**
     * Recepción de un plan de compras (ADR-010): igual que {@link #registrarEntrada} pero con referencia
     * {@code PLAN_COMPRA/{planId}} y permitida sobre variantes o productos inactivos (la mercancía ya pedida llega
     * igual). Método interno: lo invoca {@code compra}; no tiene endpoint propio.
     */
    @Transactional
    public List<MovimientoResponse> registrarEntradaDePlan(Long planId, List<LineaEntradaRequest> lineas,
            String observacion) {
        String obs = observacion == null || observacion.isBlank() ? null : observacion.strip();
        return procesarEntrada(lineas, obs, "PLAN_COMPRA", planId, false);
    }

    private List<MovimientoResponse> procesarEntrada(List<LineaEntradaRequest> lineas, String observacion,
            String referenciaTipo, Long referenciaId, boolean exigirActivas) {
        Set<Long> vistas = new HashSet<>();
        Map<Long, Producto> productos = new LinkedHashMap<>();
        Map<Long, BigDecimal> costoPorProducto = new HashMap<>();
        for (LineaEntradaRequest linea : lineas) {
            if (!vistas.add(linea.varianteId())) {
                throw new SolicitudInvalidaException(
                        "La variante " + linea.varianteId() + " está repetida en la entrada.");
            }
            Variante v = variantes.findById(linea.varianteId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Variante", linea.varianteId()));
            Producto p = v.getProducto();
            if (exigirActivas && (!v.isActivo() || !p.isActivo())) {
                throw new ExcepcionNegocio("La variante " + v.getSku() + " está inactiva.");
            }
            BigDecimal costo = linea.costoUnitario();
            BigDecimal previo = costoPorProducto.putIfAbsent(p.getId(), costo);
            if (previo != null && previo.compareTo(costo) != 0) {
                throw new ExcepcionNegocio("Las variantes de " + p.getCodigo()
                        + " deben tener el mismo costo en una entrada.");
            }
            productos.putIfAbsent(p.getId(), p);
        }

        // Orden de bloqueo producto → variante (el mismo que usa el pronóstico por sus FK): el precioCompra se
        // actualiza y se envía a la base ANTES de los movimientos; así nunca se toma el producto con variantes ya
        // bloqueadas (interbloqueo con el pronóstico). Productos por id, como el orden de las variantes.
        productos.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            BigDecimal costo = costoPorProducto.get(e.getKey());
            if (costo.signum() > 0) {
                e.getValue().actualizarPrecioCompra(costo);
            }
        });
        variantes.flush();

        UsuarioAutenticado usuario = usuarioActual.obtener();
        List<MovimientoResponse> respuesta = new ArrayList<>();
        // Orden por varianteId (como la venta): mismo orden de bloqueo entre transacciones concurrentes
        List<LineaEntradaRequest> ordenadas = lineas.stream()
                .sorted(Comparator.comparing(LineaEntradaRequest::varianteId)).toList();
        for (LineaEntradaRequest linea : ordenadas) {
            Movimiento m = movimientos.registrar(new SolicitudMovimiento(linea.varianteId(), TipoMovimiento.ENTRADA,
                    linea.cantidad(), linea.costoUnitario(), usuario.id(), referenciaTipo, referenciaId, observacion));
            respuesta.add(MovimientoResponse.de(m, usuario.nombre()));
        }
        return respuesta;
    }

    /** Ajuste manual; se permite sobre variantes inactivas (corregir el conteo de lo que quedó en bodega). */
    @Transactional
    public MovimientoResponse registrarAjuste(AjusteRequest req) {
        if (req.tipo() != TipoMovimiento.AJUSTE_POSITIVO && req.tipo() != TipoMovimiento.AJUSTE_NEGATIVO) {
            throw new SolicitudInvalidaException("El tipo de ajuste debe ser AJUSTE_POSITIVO o AJUSTE_NEGATIVO.");
        }
        UsuarioAutenticado usuario = usuarioActual.obtener();
        Movimiento m = movimientos.registrar(new SolicitudMovimiento(req.varianteId(), req.tipo(), req.cantidad(),
                null, usuario.id(), null, null, req.observacion().strip()));
        return MovimientoResponse.de(m, usuario.nombre());
    }
}
