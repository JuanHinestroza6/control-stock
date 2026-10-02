package co.livorno.controlstock.inventario.servicio;

import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.inventario.dto.MovimientoResponse;
import co.livorno.controlstock.inventario.modelo.Movimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.repositorio.MovimientoRepository;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta del kardex (solo lectura). Orden fijo: fecha desc, id desc. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KardexService {

    private final MovimientoRepository movimientos;
    private final UsuarioRepository usuarios;

    /** Fechas inclusivas: se consulta {@code [desde 00:00, hasta + 1 día 00:00)}. El orden del Pageable se ignora. */
    public Page<MovimientoResponse> consultar(Long varianteId, TipoMovimiento tipo, LocalDate desde, LocalDate hasta,
            Pageable pageable) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new SolicitudInvalidaException("La fecha inicial no puede ser posterior a la final.");
        }
        Pageable sinOrden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<Movimiento> pagina = movimientos.buscar(varianteId, tipo,
                desde != null ? desde.atStartOfDay() : null,
                hasta != null ? hasta.plusDays(1).atStartOfDay() : null, sinOrden);

        // Una sola consulta para los nombres de usuario de toda la página (sin N+1).
        Map<Long, String> nombres = usuarios.findAllById(pagina.stream().map(Movimiento::getUsuarioId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre, (a, b) -> a));
        return pagina.map(m -> MovimientoResponse.de(m, m.getUsuarioId() == null ? null : nombres.get(m.getUsuarioId())));
    }
}
