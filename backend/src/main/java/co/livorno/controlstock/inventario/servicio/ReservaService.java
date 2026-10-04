package co.livorno.controlstock.inventario.servicio;

import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.web.Ordenamiento;
import co.livorno.controlstock.inventario.dto.CancelarReservaRequest;
import co.livorno.controlstock.inventario.dto.CrearReservaRequest;
import co.livorno.controlstock.inventario.dto.ExtenderReservaRequest;
import co.livorno.controlstock.inventario.dto.LineaReservaRequest;
import co.livorno.controlstock.inventario.dto.ReservaResponse;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.EstadoReserva;
import co.livorno.controlstock.inventario.modelo.Reserva;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.repositorio.ReservaRepository;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apartados (ADR-007). El stock reservado cambia solo vía MovimientoService (RESERVA y LIBERACION con referencia
 * {@code RESERVA/{id}}). Toda transición bloquea la fila de la reserva antes de tocar la variante (orden reserva a
 * variante).
 */
@Service
@Transactional(readOnly = true)
public class ReservaService {

    public static final Set<String> ORDEN_PERMITIDO = Set.of("venceEn", "creadaEn");
    static final int HORAS_POR_VENCER = 12;

    private final ReservaRepository reservas;
    private final VarianteRepository variantes;
    private final UsuarioRepository usuarios;
    private final MovimientoService movimientos;
    private final UsuarioActual usuarioActual;
    private final Clock clock;
    private final int vigenciaPorDefectoHoras;

    public ReservaService(ReservaRepository reservas, VarianteRepository variantes, UsuarioRepository usuarios,
            MovimientoService movimientos, UsuarioActual usuarioActual, Clock clock,
            @Value("${app.reservas.vigencia-horas:48}") int vigenciaPorDefectoHoras) {
        this.reservas = reservas;
        this.variantes = variantes;
        this.usuarios = usuarios;
        this.movimientos = movimientos;
        this.usuarioActual = usuarioActual;
        this.clock = clock;
        this.vigenciaPorDefectoHoras = vigenciaPorDefectoHoras;
    }

    /** Todo o nada: una reserva ACTIVA y un movimiento RESERVA por línea; cualquier error revierte todo. */
    @Transactional
    public List<ReservaResponse> crear(CrearReservaRequest request) {
        String nombre = normalizar(request.clienteNombre());
        String contacto = normalizar(request.clienteContacto());
        validarLargo(nombre, "El nombre del cliente", 2, 120);
        validarLargo(contacto, "El contacto del cliente", 3, 120);
        Set<Long> vistos = new HashSet<>();
        for (LineaReservaRequest l : request.lineas()) {
            if (!vistos.add(l.varianteId())) {
                throw new SolicitudInvalidaException(
                        "La variante " + l.varianteId() + " está repetida en la reserva.");
            }
        }
        // Orden estable por varianteId: evita interbloqueos entre reservas y ventas concurrentes.
        List<LineaReservaRequest> ordenadas = request.lineas().stream()
                .sorted(Comparator.comparing(LineaReservaRequest::varianteId)).toList();

        Long usuarioId = usuarioActual.id();
        LocalDateTime ahora = LocalDateTime.now(clock);
        int horas = request.vigenciaHoras() == null ? vigenciaPorDefectoHoras : request.vigenciaHoras();

        List<Reserva> creadas = new java.util.ArrayList<>();
        for (LineaReservaRequest l : ordenadas) {
            Variante v = variantes.findById(l.varianteId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Variante", l.varianteId()));
            if (!v.isActivo() || !v.getProducto().isActivo()) {
                throw new ExcepcionNegocio("La variante " + v.getSku() + " está inactiva y no se puede reservar.");
            }
            Reserva r = reservas.saveAndFlush(
                    Reserva.de(v, l.cantidad(), nombre, contacto, ahora, ahora.plusHours(horas), usuarioId));
            movimientos.registrar(new SolicitudMovimiento(v.getId(), TipoMovimiento.RESERVA, l.cantidad(), null,
                    usuarioId, "RESERVA", r.getId(), null));
            creadas.add(r);
        }
        return aRespuestas(creadas);
    }

    @Transactional
    public ReservaResponse cancelar(Long id, CancelarReservaRequest request) {
        Reserva r = reservas.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Reserva", id));
        Long usuarioId = usuarioActual.id();
        String motivo = request.motivo().strip();
        r.cancelar(motivo, LocalDateTime.now(clock), usuarioId); // 409 si no está ACTIVA
        movimientos.registrar(new SolicitudMovimiento(r.getVariante().getId(), TipoMovimiento.LIBERACION,
                r.getCantidad(), null, usuarioId, "RESERVA", r.getId(), "Cancelada"));
        reservas.saveAndFlush(r);
        return aRespuestas(List.of(r)).get(0);
    }

    @Transactional
    public ReservaResponse extender(Long id, ExtenderReservaRequest request) {
        Reserva r = reservas.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Reserva", id));
        r.extender(request.horas(), LocalDateTime.now(clock)); // 409 si no está ACTIVA; 422 si pasa de 7 días
        reservas.saveAndFlush(r);
        return aRespuestas(List.of(r)).get(0);
    }

    /**
     * Vencimiento automático (lo invoca la tarea, una transacción por reserva). Bloquea la fila: si ya no está ACTIVA
     * (otro proceso la convirtió o canceló) la omite y devuelve false. LIBERACION con usuarioId null (sistema).
     */
    @Transactional
    public boolean vencer(Long id, LocalDateTime ahora) {
        Reserva r = reservas.bloquear(id).orElse(null);
        if (r == null || !r.estaActiva()) {
            return false;
        }
        r.vencer(ahora);
        movimientos.registrar(new SolicitudMovimiento(r.getVariante().getId(), TipoMovimiento.LIBERACION,
                r.getCantidad(), null, null, "RESERVA", r.getId(), "Vencimiento automático"));
        reservas.saveAndFlush(r);
        return true;
    }

    public Page<ReservaResponse> listar(EstadoReserva estado, String q, Long varianteId, boolean porVencer,
            Pageable pageable) {
        Ordenamiento.validar(pageable, ORDEN_PERMITIDO);
        if (q != null && q.strip().length() > 60) {
            throw new SolicitudInvalidaException("La búsqueda no puede superar 60 caracteres.");
        }
        LocalDateTime limite = porVencer ? LocalDateTime.now(clock).plusHours(HORAS_POR_VENCER) : null;
        Page<Reserva> pagina = reservas.buscar(estado, varianteId, ProductoRepository.patronContiene(q), limite,
                pageable);
        Map<Long, String> nombres = nombres(pagina.getContent());
        return pagina.map(r -> ReservaResponse.de(r, nombres));
    }

    public ReservaResponse obtener(Long id) {
        Reserva r = reservas.conVariante(id).orElseThrow(() -> new RecursoNoEncontradoException("Reserva", id));
        return aRespuestas(List.of(r)).get(0);
    }

    private List<ReservaResponse> aRespuestas(List<Reserva> lista) {
        Map<Long, String> nombres = nombres(lista);
        return lista.stream().map(r -> ReservaResponse.de(r, nombres)).toList();
    }

    private Map<Long, String> nombres(List<Reserva> lista) {
        Set<Long> ids = new HashSet<>();
        for (Reserva r : lista) {
            ids.add(r.getUsuarioId());
            if (r.getCerradaPor() != null) {
                ids.add(r.getCerradaPor());
            }
        }
        Map<Long, String> nombres = new HashMap<>();
        for (Usuario u : usuarios.findAllById(ids)) {
            nombres.put(u.getId(), u.getNombre());
        }
        return nombres;
    }

    private static void validarLargo(String texto, String campo, int min, int max) {
        if (texto.length() < min || texto.length() > max) {
            throw new SolicitudInvalidaException(
                    campo + " debe tener entre " + min + " y " + max + " caracteres sin contar espacios sobrantes.");
        }
    }

    /** Sin espacios en los extremos y con los internos colapsados. */
    static String normalizar(String texto) {
        return texto.strip().replaceAll("\\s+", " ");
    }
}
