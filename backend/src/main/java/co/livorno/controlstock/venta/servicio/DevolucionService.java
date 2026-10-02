package co.livorno.controlstock.venta.servicio;

import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.servicio.MovimientoService;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import co.livorno.controlstock.venta.dto.DevolucionRequest;
import co.livorno.controlstock.venta.dto.DevolucionResponse;
import co.livorno.controlstock.venta.modelo.Devolucion;
import co.livorno.controlstock.venta.modelo.DetalleVenta;
import co.livorno.controlstock.venta.modelo.EstadoVenta;
import co.livorno.controlstock.venta.modelo.Venta;
import co.livorno.controlstock.venta.repositorio.DevolucionRepository;
import co.livorno.controlstock.venta.repositorio.VentaRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DevolucionService {

    private final VentaRepository ventas;
    private final DevolucionRepository devoluciones;
    private final UsuarioRepository usuarios;
    private final MovimientoService movimientos;
    private final UsuarioActual usuarioActual;
    private final Clock clock;

    /**
     * Registra una devolución sobre una línea de la venta. Bloquea la fila de la venta (una devolución no apta no toca
     * Variante, y su @Version no la protegería; ADR-006). Si es apta, suma stock con un movimiento DEVOLUCION.
     */
    @Transactional
    public DevolucionResponse registrar(Long ventaId, DevolucionRequest request) {
        Venta venta = ventas.bloquear(ventaId).orElseThrow(() -> new RecursoNoEncontradoException("Venta", ventaId));
        if (venta.getEstado() == EstadoVenta.ANULADA) {
            throw new ExcepcionNegocio("La venta " + venta.numero() + " está anulada y no admite devoluciones.");
        }
        DetalleVenta linea = venta.getDetalles().stream()
                .filter(d -> d.getId().equals(request.detalleVentaId())).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Línea de venta", request.detalleVentaId()));

        int porDevolver = linea.getCantidad() - devoluciones.sumaDevuelta(linea.getId());
        if (request.cantidad() > porDevolver) {
            throw new ExcepcionNegocio("Solo quedan " + porDevolver + " unidades por devolver de "
                    + linea.getVariante().getSku() + ".");
        }

        Long usuarioId = usuarioActual.id();
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", usuarioId));
        Devolucion devolucion = devoluciones.saveAndFlush(new Devolucion(linea, request.cantidad(),
                request.motivo(), request.aptaParaVenta(), LocalDateTime.now(clock), usuario));

        if (devolucion.isAptaParaVenta()) {
            movimientos.registrar(new SolicitudMovimiento(linea.getVariante().getId(), TipoMovimiento.DEVOLUCION,
                    devolucion.getCantidad(), null, usuarioId, "DEVOLUCION", devolucion.getId(),
                    devolucion.getMotivo()));
        }
        return DevolucionResponse.de(devolucion);
    }
}
