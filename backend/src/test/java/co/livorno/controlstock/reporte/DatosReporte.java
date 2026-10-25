package co.livorno.controlstock.reporte;

import co.livorno.controlstock.compra.dto.AprobarPlanRequest;
import co.livorno.controlstock.compra.dto.CrearPlanRequest;
import co.livorno.controlstock.compra.dto.LineaPlanRequest;
import co.livorno.controlstock.compra.dto.PlanCompraDetalleResponse;
import co.livorno.controlstock.compra.servicio.PlanCompraService;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.CancelarReservaRequest;
import co.livorno.controlstock.inventario.dto.CrearReservaRequest;
import co.livorno.controlstock.inventario.dto.LineaReservaRequest;
import co.livorno.controlstock.inventario.servicio.ReservaService;
import co.livorno.controlstock.inventario.dto.AjusteRequest;
import co.livorno.controlstock.inventario.dto.EntradaRequest;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.dto.LineaEntradaRequest;
import co.livorno.controlstock.inventario.servicio.OperacionInventarioService;
import co.livorno.controlstock.venta.dto.AnularVentaRequest;
import co.livorno.controlstock.venta.dto.ConvertirReservasRequest;
import co.livorno.controlstock.venta.dto.DevolucionRequest;
import co.livorno.controlstock.venta.dto.DevolucionResponse;
import co.livorno.controlstock.venta.dto.LineaVentaRequest;
import co.livorno.controlstock.venta.dto.RegistrarVentaRequest;
import co.livorno.controlstock.venta.dto.VentaDetalleResponse;
import co.livorno.controlstock.venta.servicio.DevolucionService;
import co.livorno.controlstock.venta.servicio.VentaService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Datos de prueba de los reportes: el catálogo se inserta por SQL (datos de referencia) y todo lo que mueve stock o
 * dinero (entradas, ventas, devoluciones, anulaciones) pasa por los servicios reales. Como los servicios fechan con el
 * reloj del sistema, las fechas de ventas, devoluciones y anulaciones se mueven después al pasado con un UPDATE de
 * preparación.
 */
public class DatosReporte {

    /** Línea de venta de prueba; precioUnitario null = precio de lista. */
    public record L(Long varianteId, int cantidad, String precioUnitario) {}

    private final JdbcTemplate jdbc;
    private final VentaService ventas;
    private final DevolucionService devoluciones;
    private final OperacionInventarioService operaciones;
    private ReservaService reservas;
    private PlanCompraService planes;
    private final List<Long> planesCreados = new ArrayList<>();
    private final String sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
    private final List<Long> usuarios = new ArrayList<>();
    private final List<Long> categorias = new ArrayList<>();
    private final List<Long> productos = new ArrayList<>();
    public Long adminId;
    public Long vendedorId;
    public final String nombreAdmin = "Admin Reporte";

    public DatosReporte(JdbcTemplate jdbc, VentaService ventas, DevolucionService devoluciones,
            OperacionInventarioService operaciones) {
        this.jdbc = jdbc;
        this.ventas = ventas;
        this.devoluciones = devoluciones;
        this.operaciones = operaciones;
        adminId = usuario(nombreAdmin, "ADMIN");
        vendedorId = usuario("Vendedor Reporte", "VENDEDOR");
    }

    /** Habilita {@link #reservar} y {@link #pedidoEnCamino}. */
    public DatosReporte conReservasYPlanes(ReservaService reservas, PlanCompraService planes) {
        this.reservas = reservas;
        this.planes = planes;
        return this;
    }

    public String sufijo() {
        return sufijo;
    }

    private Long usuario(String nombre, String rol) {
        String email = rol.toLowerCase() + "." + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES (?, ?, 'hash', ?, TRUE, FALSE, NOW(6), NOW(6))", nombre, email, rol);
        Long id = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        usuarios.add(id);
        return id;
    }

    public Long categoria(String nombre) {
        String unico = nombre + " " + sufijo;
        jdbc.update("INSERT INTO categoria (nombre, creado_en, actualizado_en) VALUES (?, NOW(6), NOW(6))", unico);
        Long id = jdbc.queryForObject("SELECT id FROM categoria WHERE nombre = ?", Long.class, unico);
        categorias.add(id);
        return id;
    }

    public Long producto(String codigo, Long categoriaId, String precioCompra, String precioVenta) {
        String unico = codigo + sufijo;
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, activo,"
                + " creado_en, actualizado_en) VALUES (?, ?, ?, ?, ?, TRUE, NOW(6), NOW(6))", unico,
                "Prenda " + codigo, categoriaId, new BigDecimal(precioCompra), new BigDecimal(precioVenta));
        Long id = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, unico);
        productos.add(id);
        return id;
    }

    public Long variante(Long productoId, String talla) {
        String sku = "S" + productoId + "-" + talla + "-" + sufijo;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, activo,"
                + " creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', 0, 0, TRUE, NOW(6), NOW(6))",
                productoId, sku, talla);
        return jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
    }

    // ---------- operaciones por los servicios reales ----------

    public void entrada(Long varianteId, int cantidad, String costo) {
        como(adminId, "ADMIN", () -> operaciones.registrarEntrada(new EntradaRequest(null,
                List.of(new LineaEntradaRequest(varianteId, cantidad, new BigDecimal(costo))))).size());
    }

    /** Ajuste manual como ADMIN (positivo o negativo). */
    public void ajuste(Long varianteId, boolean positivo, int cantidad) {
        como(adminId, "ADMIN", () -> operaciones.registrarAjuste(new AjusteRequest(varianteId,
                positivo ? TipoMovimiento.AJUSTE_POSITIVO : TipoMovimiento.AJUSTE_NEGATIVO, cantidad,
                "Conteo físico de prueba")).id());
    }

    /**
     * Mueve al pasado los movimientos de la variante del tipo dado (solo preparación: los servicios fechan con el reloj
     * del sistema; en producción el kardex nunca se edita).
     */
    public void fecharMovimientos(Long varianteId, String tipo, LocalDateTime fecha) {
        jdbc.update("UPDATE movimiento SET fecha = ? WHERE variante_id = ? AND tipo = ?", fecha, varianteId, tipo);
    }

    /** Vende como VENDEDOR y fecha la venta en {@code fecha}. */
    public VentaDetalleResponse vender(LocalDateTime fecha, L... lineas) {
        List<LineaVentaRequest> reqs = new ArrayList<>();
        for (L l : lineas) {
            reqs.add(new LineaVentaRequest(l.varianteId(), l.cantidad(),
                    l.precioUnitario() == null ? null : new BigDecimal(l.precioUnitario())));
        }
        VentaDetalleResponse v = como(vendedorId, "VENDEDOR", () -> ventas.registrar(new RegistrarVentaRequest(reqs)));
        jdbc.update("UPDATE venta SET fecha = ? WHERE id = ?", fecha, v.id());
        return v;
    }

    /** Devuelve como VENDEDOR y fecha la devolución en {@code fecha}. */
    public DevolucionResponse devolver(VentaDetalleResponse venta, int indiceLinea, int cantidad, boolean apta,
            LocalDateTime fecha) {
        Long detalleId = venta.lineas().get(indiceLinea).detalleId();
        DevolucionResponse d = como(vendedorId, "VENDEDOR", () -> devoluciones.registrar(venta.id(),
                new DevolucionRequest(detalleId, cantidad, "Devolución de prueba", apta)));
        jdbc.update("UPDATE devolucion SET fecha = ? WHERE id = ?", fecha, d.id());
        return d;
    }

    /** Anula como ADMIN; deja la venta y la anulación fechadas en {@code fecha} (anulada una hora después). */
    public void anular(VentaDetalleResponse venta, LocalDateTime fecha) {
        como(adminId, "ADMIN", () -> ventas.anular(venta.id(), new AnularVentaRequest("Error de digitación")).id());
        jdbc.update("UPDATE venta SET fecha = ?, anulada_en = ? WHERE id = ?", fecha, fecha.plusHours(1), venta.id());
    }

    private <T> T como(Long usuarioId, String rol, java.util.function.Supplier<T> accion) {
        SecurityContext previo = SecurityContextHolder.getContext();
        try {
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            UsuarioAutenticado u = new UsuarioAutenticado(usuarioId, rol.toLowerCase() + "@livorno.test", "Prueba", rol);
            ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(u, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
            SecurityContextHolder.setContext(ctx);
            return accion.get();
        } finally {
            SecurityContextHolder.setContext(previo);
        }
    }

    /** Aparta unidades como VENDEDOR para un cliente de prueba. */
    public Long reservar(Long varianteId, int cantidad, String clienteNombre, String clienteContacto) {
        return como(vendedorId, "VENDEDOR", () -> reservas.crear(new CrearReservaRequest(clienteNombre,
                clienteContacto, null, List.of(new LineaReservaRequest(varianteId, cantidad)))).get(0).id());
    }

    /** Convierte la reserva en venta como VENDEDOR (movimiento VENTA_RESERVA). */
    public void convertirReserva(Long reservaId) {
        como(vendedorId, "VENDEDOR",
                () -> ventas.registrarDesdeReservas(new ConvertirReservasRequest(List.of(reservaId), null)).id());
    }

    /** Cancela la reserva como VENDEDOR (movimiento LIBERACION). */
    public void cancelarReserva(Long reservaId) {
        como(vendedorId, "VENDEDOR",
                () -> reservas.cancelar(reservaId, new CancelarReservaRequest("Cliente desistió")).id());
    }

    /** Plan manual creado por INVENTARIO y aprobado por ADMIN: queda en camino. */
    public void pedidoEnCamino(Long varianteId, int cantidad, String costoEstimado) {
        PlanCompraDetalleResponse plan = como(adminId, "ADMIN", () -> planes.crear(new CrearPlanRequest(null,
                List.of(new LineaPlanRequest(varianteId, cantidad, new BigDecimal(costoEstimado))))));
        planesCreados.add(plan.id());
        como(adminId, "ADMIN", () -> planes.aprobar(plan.id(), new AprobarPlanRequest(plan.version())).id());
    }

    /** Desactiva la variante por SQL (preparación; los servicios la bloquean si hay reservas). */
    public void desactivarVariante(Long varianteId) {
        jdbc.update("UPDATE variante SET activo = FALSE WHERE id = ?", varianteId);
    }

    /** Borra todo lo creado, en orden de dependencias (alertas y reservas antes que las variantes). */
    public void limpiar() {
        SecurityContextHolder.clearContext();
        if (!productos.isEmpty()) {
            String prod = csv(productos);
            String variantes = "(SELECT id FROM variante WHERE producto_id IN (" + prod + "))";
            jdbc.update("DELETE FROM devolucion WHERE detalle_venta_id IN (SELECT id FROM detalle_venta"
                    + " WHERE variante_id IN " + variantes + ")");
            jdbc.update("DELETE FROM movimiento WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM alerta WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM reserva WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM analitica_variante WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM detalle_venta WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM detalle_plan WHERE variante_id IN " + variantes);
        }
        if (!planesCreados.isEmpty()) {
            jdbc.update("DELETE FROM plan_compra WHERE id IN (" + csv(planesCreados) + ")");
        }
        if (!usuarios.isEmpty()) {
            jdbc.update("DELETE FROM venta WHERE usuario_id IN (" + csv(usuarios) + ")");
        }
        if (!productos.isEmpty()) {
            jdbc.update("DELETE FROM variante WHERE producto_id IN (" + csv(productos) + ")");
            jdbc.update("DELETE FROM producto WHERE id IN (" + csv(productos) + ")");
        }
        if (!categorias.isEmpty()) {
            jdbc.update("DELETE FROM categoria WHERE id IN (" + csv(categorias) + ")");
        }
        if (!usuarios.isEmpty()) {
            jdbc.update("DELETE FROM usuario WHERE id IN (" + csv(usuarios) + ")");
        }
    }

    private static String csv(List<Long> ids) {
        return ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("NULL");
    }
}
