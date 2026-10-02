package co.livorno.controlstock.inventario.servicio;

import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.evento.StockCambiado;
import co.livorno.controlstock.inventario.modelo.EfectoMovimiento;
import co.livorno.controlstock.inventario.modelo.Movimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.repositorio.MovimientoRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único punto de escritura del stock (regla de oro 1). El rol de quien ajusta lo valida el controller; la actividad
 * de variante/producto la valida quien llama. Concurrencia: {@code @Version} en Variante (409 sin reintento).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovimientoService {

    private final VarianteRepository varianteRepository;
    private final MovimientoRepository movimientoRepository;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    @Transactional
    public Movimiento registrar(SolicitudMovimiento s) {
        if (s.cantidad() <= 0) {
            throw new ExcepcionNegocio("La cantidad debe ser mayor que cero.");
        }
        Variante v = varianteRepository.findById(s.varianteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Variante", s.varianteId()));
        EfectoMovimiento efecto = s.tipo().efecto(s.cantidad());
        validar(v, s);

        int fisicoAntes = v.getStockFisico();
        int reservadoAntes = v.getStockReservado();
        v.aplicarMovimiento(efecto.deltaFisico(), efecto.deltaReservado());
        Movimiento m = Movimiento.de(v, s.tipo(), s.cantidad(), fisicoAntes, reservadoAntes, s.costoUnitario(),
                s.usuarioId(), LocalDateTime.now(clock), s.referenciaTipo(), s.referenciaId(), s.observacion());
        movimientoRepository.save(m);
        eventos.publishEvent(new StockCambiado(v.getId()));
        return m;
    }

    private static void validar(Variante v, SolicitudMovimiento s) {
        TipoMovimiento tipo = s.tipo();
        if (tipo == TipoMovimiento.ENTRADA && s.costoUnitario() == null) {
            throw new ExcepcionNegocio("La entrada requiere el costo unitario.");
        }
        if (tipo == TipoMovimiento.ENTRADA && s.costoUnitario().signum() < 0) {
            throw new ExcepcionNegocio("El costo unitario no puede ser negativo.");
        }
        if (tipo != TipoMovimiento.ENTRADA && s.costoUnitario() != null) {
            throw new ExcepcionNegocio("Solo la entrada lleva costo unitario.");
        }
        if ((tipo == TipoMovimiento.AJUSTE_POSITIVO || tipo == TipoMovimiento.AJUSTE_NEGATIVO)
                && (s.observacion() == null || s.observacion().isBlank())) {
            throw new ExcepcionNegocio("El ajuste requiere una observación.");
        }
        if ((s.referenciaTipo() == null) != (s.referenciaId() == null)) {
            throw new ExcepcionNegocio("La referencia requiere tipo e id.");
        }
        boolean usaDisponible = tipo == TipoMovimiento.VENTA || tipo == TipoMovimiento.RESERVA
                || tipo == TipoMovimiento.AJUSTE_NEGATIVO;
        if (usaDisponible && s.cantidad() > v.getStockDisponible()) {
            throw new ExcepcionNegocio("Stock insuficiente para " + v.getSku() + ": disponible "
                    + v.getStockDisponible() + ", solicitado " + s.cantidad() + ".");
        }
        boolean usaReservado = tipo == TipoMovimiento.LIBERACION || tipo == TipoMovimiento.VENTA_RESERVA;
        if (usaReservado && s.cantidad() > v.getStockReservado()) {
            throw new ExcepcionNegocio("Reserva insuficiente para " + v.getSku() + ": reservado "
                    + v.getStockReservado() + ", solicitado " + s.cantidad() + ".");
        }
    }
}
