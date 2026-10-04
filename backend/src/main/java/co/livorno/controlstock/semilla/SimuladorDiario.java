package co.livorno.controlstock.semilla;

import co.livorno.controlstock.catalogo.dto.ActualizarProductoRequest;
import co.livorno.controlstock.catalogo.dto.ProductoDetalleResponse;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.servicio.ProductoService;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.AjusteRequest;
import co.livorno.controlstock.inventario.dto.EntradaRequest;
import co.livorno.controlstock.inventario.dto.LineaEntradaRequest;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.servicio.OperacionInventarioService;
import co.livorno.controlstock.semilla.CreadorCatalogo.CatalogoCreado;
import co.livorno.controlstock.semilla.CreadorCatalogo.VarianteCreada;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.venta.dto.AnularVentaRequest;
import co.livorno.controlstock.venta.dto.DevolucionRequest;
import co.livorno.controlstock.venta.dto.LineaVentaRequest;
import co.livorno.controlstock.venta.dto.LineaVentaResponse;
import co.livorno.controlstock.venta.dto.RegistrarVentaRequest;
import co.livorno.controlstock.venta.dto.VentaDetalleResponse;
import co.livorno.controlstock.venta.servicio.DevolucionService;
import co.livorno.controlstock.venta.servicio.VentaService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Simulación día a día (ADR-008, B4): ventas, reposición, ajustes, alza de precios, devoluciones, anulaciones y el
 * producto descontinuado. Todo pasa por los servicios reales, como el usuario con el rol correcto y con el reloj
 * simulado, que solo avanza: las operaciones de cada día se ejecutan en orden de hora. Entre días solo se guardan ids
 * y contadores (flush + clear al terminar cada día). Se ejecuta dentro de la transacción de la carga.
 */
@Component
@Profile("seed")
public class SimuladorDiario {

    static final int DIAS_REPOSICION_MIN = 30;
    static final int DIAS_REPOSICION_MAX = 60;
    static final double PROBABILIDAD_DEVOLUCION = 0.04;
    static final double PROBABILIDAD_APTA = 0.80;
    static final double PROBABILIDAD_ANULACION = 0.007;
    static final double AJUSTES_POR_MES = 2.0;
    static final double PROBABILIDAD_VARIANTE_AGOTABLE = 0.05;
    static final int MESES_DESCONTINUADO = 6;
    static final BigDecimal ALZA_ENERO = new BigDecimal("1.05");

    private static final LocalTime HORA_PRECIOS = LocalTime.of(6, 0);
    private static final LocalTime HORA_REPOSICION = LocalTime.of(7, 0);
    private static final LocalTime HORA_LLEGADA = LocalTime.of(8, 0);
    private static final LocalTime CIERRE_ANULACIONES = LocalTime.of(21, 30);

    private static final List<String> MOTIVOS_DEVOLUCION = List.of("Talla incorrecta", "Prenda con defecto",
            "No le gustó el color", "Cambio de opinión", "No coincide con la foto");
    private static final List<String> MOTIVOS_ANULACION = List.of("Error al registrar la venta",
            "Cliente desistió de la compra", "Venta duplicada por error");
    private static final List<String> OBSERVACIONES_POSITIVAS = List.of("Conteo físico: sobrante",
            "Conteo físico: unidad encontrada en bodega");
    private static final List<String> OBSERVACIONES_NEGATIVAS = List.of("Merma por daño en bodega",
            "Conteo físico: faltante", "Merma por mancha en la prenda");

    private final VentaService ventas;
    private final DevolucionService devoluciones;
    private final OperacionInventarioService operaciones;
    private final ProductoService productos;
    private final ComoUsuario comoUsuario;
    private final RelojSimulado reloj;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;

    public SimuladorDiario(VentaService ventas, DevolucionService devoluciones,
                           OperacionInventarioService operaciones, ProductoService productos,
                           ComoUsuario comoUsuario, RelojSimulado reloj, EntityManager entityManager,
                           JdbcTemplate jdbc) {
        this.ventas = ventas;
        this.devoluciones = devoluciones;
        this.operaciones = operaciones;
        this.productos = productos;
        this.comoUsuario = comoUsuario;
        this.reloj = reloj;
        this.entityManager = entityManager;
        this.jdbc = jdbc;
    }

    /**
     * Simula desde {@code inicio} hasta {@code fin} inclusive. El reloj debe estar en el día de inicio a las 08:00 o
     * antes de las 09:00 (la entrada inicial ya se registró). {@code costos} fija el costo de las reposiciones y
     * {@code aleatorio} el resto de las decisiones; ambos con semilla para reproducir la carga.
     */
    public ResultadoSimulacion simular(CatalogoCreado catalogo, ModeloDemanda modelo,
                                       Map<Rol, UsuarioAutenticado> usuarios, LocalDate inicio, LocalDate fin,
                                       Random costos, Random aleatorio) {
        return new Corrida(catalogo, modelo, usuarios, inicio, fin, costos, aleatorio).ejecutar();
    }

    // ------------------------------------------------------------------------------------------------------------

    private record Clave(String codigo, Talla talla, int indiceColor) {
    }

    private record Llegada(String codigo, Map<Integer, Integer> cantidades, BigDecimal costo) {
    }

    private record PlanDevolucion(LocalTime hora, Long ventaId, Long detalleId, int varianteIdx, int cantidad,
                                  String motivo, boolean apta) {
    }

    private record Evento(LocalTime hora, long orden, Runnable accion) {
    }

    /** Estado y contadores de una corrida; solo ids y enteros entre días. */
    private final class Corrida {
        private final CatalogoCreado catalogo;
        private final ModeloDemanda modelo;
        private final LocalDate inicio;
        private final LocalDate fin;
        private final Random costos;
        private final Random azar;
        private final UsuarioAutenticado vendedor;
        private final UsuarioAutenticado inventario;
        private final UsuarioAutenticado admin;

        private final List<VarianteCreada> variantes;
        private final Map<Clave, Integer> indicePorClave = new HashMap<>();
        private final Map<String, List<Integer>> variantesPorProducto = new LinkedHashMap<>();
        private final Map<String, ProductoSemilla> productoPorCodigo = new LinkedHashMap<>();
        private final int[] fisico;
        private final int[] enCamino;
        private final boolean[] agotable;
        private final Map<String, BigDecimal> precioVenta = new HashMap<>();
        private final Set<String> desactivados = new HashSet<>();
        private final TreeMap<LocalDate, List<Llegada>> llegadas = new TreeMap<>();
        private final TreeMap<LocalDate, List<PlanDevolucion>> devolucionesPlan = new TreeMap<>();
        private final Map<Long, BigDecimal> totalVenta = new HashMap<>();
        private final LocalDate corteDescontinuado;

        private PriorityQueue<Evento> cola;
        private long orden;
        private LocalDate diaActual;
        private LocalTime horaActual;

        private long nVentas;
        private long unidadesVendidas;
        private long lineasPerdidas;
        private long unidadesPerdidas;
        private long anuladas;
        private long nDevoluciones;
        private long noAptas;
        private long entradas;
        private long ajustes;
        private int alzas;
        private BigDecimal ingreso = BigDecimal.ZERO;

        Corrida(CatalogoCreado catalogo, ModeloDemanda modelo, Map<Rol, UsuarioAutenticado> usuarios,
                LocalDate inicio, LocalDate fin, Random costos, Random azar) {
            this.catalogo = catalogo;
            this.modelo = modelo;
            this.inicio = inicio;
            this.fin = fin;
            this.costos = costos;
            this.azar = azar;
            this.vendedor = usuarios.get(Rol.VENDEDOR);
            this.inventario = usuarios.get(Rol.INVENTARIO);
            this.admin = usuarios.get(Rol.ADMIN);
            this.variantes = catalogo.variantes();
            this.corteDescontinuado = fin.minusMonths(MESES_DESCONTINUADO);
            this.fisico = new int[variantes.size()];
            this.enCamino = new int[variantes.size()];
            this.agotable = new boolean[variantes.size()];

            Map<Long, Integer> stockInicial = new HashMap<>();
            jdbc.query("select id, stock_fisico from variante", rs -> {
                stockInicial.put(rs.getLong("id"), rs.getInt("stock_fisico"));
            });
            for (int i = 0; i < variantes.size(); i++) {
                VarianteCreada v = variantes.get(i);
                indicePorClave.put(new Clave(v.producto().codigo(), v.talla(), v.indiceColor()), i);
                variantesPorProducto.computeIfAbsent(v.producto().codigo(), k -> new ArrayList<>()).add(i);
                productoPorCodigo.putIfAbsent(v.producto().codigo(), v.producto());
                precioVenta.putIfAbsent(v.producto().codigo(), v.producto().precioVenta());
                fisico[i] = stockInicial.getOrDefault(v.id(), 0);
                // Algunas variantes se dejan agotar (nunca se reponen); el descontinuado se maneja aparte.
                agotable[i] = azar.nextDouble() < PROBABILIDAD_VARIANTE_AGOTABLE;
            }
        }

        ResultadoSimulacion ejecutar() {
            int dias = 0;
            for (LocalDate dia = inicio; !dia.isAfter(fin); dia = dia.plusDays(1)) {
                simularDia(dia);
                entityManager.flush();
                entityManager.clear();
                dias++;
            }
            return new ResultadoSimulacion(dias, nVentas, unidadesVendidas, lineasPerdidas, unidadesPerdidas,
                    anuladas, nDevoluciones, noAptas, entradas, ajustes, alzas, desactivados.size(), ingreso);
        }

        private void simularDia(LocalDate dia) {
            diaActual = dia;
            cola = new PriorityQueue<>((a, b) -> {
                int c = a.hora().compareTo(b.hora());
                return c != 0 ? c : Long.compare(a.orden(), b.orden());
            });
            boolean primerDia = dia.equals(inicio);
            if (!primerDia) {
                if (dia.getMonthValue() == 1 && dia.getDayOfMonth() == 1) {
                    encolar(HORA_PRECIOS, this::subirPrecios);
                }
                encolar(HORA_REPOSICION, this::reponer);
            }
            List<Llegada> deHoy = llegadas.remove(dia);
            if (deHoy != null) {
                for (Llegada l : deHoy) {
                    encolar(HORA_LLEGADA, () -> recibir(l));
                }
            }
            List<PlanDevolucion> devolucionesHoy = devolucionesPlan.remove(dia);
            if (devolucionesHoy != null) {
                for (PlanDevolucion d : devolucionesHoy) {
                    encolar(d.hora(), () -> devolver(d));
                }
            }
            for (ModeloDemanda.Ticket t : modelo.ticketsDelDia(dia)) {
                encolar(t.hora(), () -> vender(t));
            }
            if (azar.nextDouble() < AJUSTES_POR_MES / 30.0) {
                LocalTime hora = LocalTime.of(10, 0).plusMinutes(azar.nextInt(9 * 60));
                int idx = azar.nextInt(variantes.size());
                boolean positivo = azar.nextDouble() < 0.4;
                int cantidad = 1 + azar.nextInt(3);
                int obs = azar.nextInt(3);
                encolar(hora, () -> ajustar(idx, positivo, cantidad, obs));
            }
            while (!cola.isEmpty()) {
                Evento evento = cola.poll();
                horaActual = evento.hora();
                evento.accion().run();
            }
        }

        private void encolar(LocalTime hora, Runnable accion) {
            cola.add(new Evento(hora, orden++, accion));
        }

        private void en(UsuarioAutenticado usuario, Runnable accion) {
            reloj.avanzarA(diaActual.atTime(horaActual));
            comoUsuario.ejecutar(usuario, accion);
        }

        // ------------------------------------------------------------------ operaciones

        private int disponible(int idx) {
            return fisico[idx];  // B4 no reserva: disponible = físico (las reservas llegan en B5)
        }

        private boolean activo(String codigo) {
            return !desactivados.contains(codigo);
        }

        private void vender(ModeloDemanda.Ticket ticket) {
            record Pedida(int idx, int cantidad, BigDecimal precio) {
            }
            List<Pedida> pedidas = new ArrayList<>();
            for (ModeloDemanda.Linea l : ticket.lineas()) {
                String codigo = l.producto().codigo();
                if (!activo(codigo)) {
                    continue;  // producto descontinuado y desactivado: ya no se vende
                }
                int idx = indicePorClave.get(new Clave(codigo, l.talla(), l.indiceColor()));
                int vendible = Math.min(l.cantidad(), disponible(idx));
                if (vendible < l.cantidad()) {
                    lineasPerdidas++;
                    unidadesPerdidas += l.cantidad() - vendible;
                }
                if (vendible > 0) {
                    BigDecimal precio = l.descuentoPorcentaje() > 0
                            ? ModeloDemanda.precioConDescuento(precioVenta.get(codigo), l.descuentoPorcentaje())
                            : null;
                    pedidas.add(new Pedida(idx, vendible, precio));
                }
            }
            if (pedidas.isEmpty()) {
                return;
            }
            List<LineaVentaRequest> lineas = pedidas.stream()
                    .map(p -> new LineaVentaRequest(variantes.get(p.idx()).id(), p.cantidad(), p.precio())).toList();
            VentaDetalleResponse[] venta = new VentaDetalleResponse[1];
            en(vendedor, () -> venta[0] = ventas.registrar(new RegistrarVentaRequest(lineas)));
            VentaDetalleResponse v = venta[0];
            nVentas++;
            ingreso = ingreso.add(v.total());
            totalVenta.put(v.id(), v.total());
            boolean conDevolucion = false;
            for (int i = 0; i < pedidas.size(); i++) {
                Pedida p = pedidas.get(i);
                fisico[p.idx()] -= p.cantidad();
                unidadesVendidas += p.cantidad();
            }
            for (LineaVentaResponse linea : v.lineas()) {
                int idx = indiceDeVariante(linea.varianteId());
                if (azar.nextDouble() < PROBABILIDAD_DEVOLUCION) {
                    LocalDate fecha = diaActual.plusDays(1 + azar.nextInt(15));
                    LocalTime hora = LocalTime.of(9, 0).plusMinutes(azar.nextInt(12 * 60));
                    int cantidad = 1 + azar.nextInt(linea.cantidad());
                    String motivo = MOTIVOS_DEVOLUCION.get(azar.nextInt(MOTIVOS_DEVOLUCION.size()));
                    boolean apta = azar.nextDouble() < PROBABILIDAD_APTA;
                    if (!fecha.isAfter(fin)) {
                        devolucionesPlan.computeIfAbsent(fecha, k -> new ArrayList<>()).add(
                                new PlanDevolucion(hora, v.id(), linea.detalleId(), idx, cantidad, motivo, apta));
                        conDevolucion = true;
                    }
                }
            }
            // Se sortea siempre (consumo de aleatorios fijo); solo aplica a ventas sin devolución programada.
            boolean anular = azar.nextDouble() < PROBABILIDAD_ANULACION;
            LocalTime horaAnulacion = ticket.hora().plusMinutes(1 + azar.nextInt(180));
            String motivo = MOTIVOS_ANULACION.get(azar.nextInt(MOTIVOS_ANULACION.size()));
            if (anular && !conDevolucion) {
                LocalTime hora = horaAnulacion.isAfter(CIERRE_ANULACIONES) ? CIERRE_ANULACIONES : horaAnulacion;
                if (hora.isBefore(ticket.hora())) {
                    hora = ticket.hora();
                }
                encolar(hora, () -> anular(v, motivo));
            }
        }

        private int indiceDeVariante(Long varianteId) {
            for (int i = 0; i < variantes.size(); i++) {
                if (variantes.get(i).id().equals(varianteId)) {
                    return i;
                }
            }
            throw new IllegalStateException("Variante desconocida " + varianteId);
        }

        private void anular(VentaDetalleResponse v, String motivo) {
            en(admin, () -> ventas.anular(v.id(), new AnularVentaRequest(motivo)));
            for (LineaVentaResponse l : v.lineas()) {
                fisico[indiceDeVariante(l.varianteId())] += l.cantidad();
                unidadesVendidas -= l.cantidad();
            }
            ingreso = ingreso.subtract(totalVenta.get(v.id()));
            anuladas++;
        }

        private void devolver(PlanDevolucion d) {
            en(vendedor, () -> devoluciones.registrar(d.ventaId(),
                    new DevolucionRequest(d.detalleId(), d.cantidad(), d.motivo(), d.apta())));
            nDevoluciones++;
            if (d.apta()) {
                fisico[d.varianteIdx()] += d.cantidad();
            } else {
                noAptas++;
            }
        }

        private void ajustar(int idx, boolean positivo, int cantidad, int obs) {
            VarianteCreada v = variantes.get(idx);
            if (!activo(v.producto().codigo())) {
                return;
            }
            boolean sumar = positivo || disponible(idx) == 0;
            int q = sumar ? cantidad : Math.min(cantidad, disponible(idx));
            String texto = sumar ? OBSERVACIONES_POSITIVAS.get(obs % OBSERVACIONES_POSITIVAS.size())
                    : OBSERVACIONES_NEGATIVAS.get(obs % OBSERVACIONES_NEGATIVAS.size());
            TipoMovimiento tipo = sumar ? TipoMovimiento.AJUSTE_POSITIVO : TipoMovimiento.AJUSTE_NEGATIVO;
            en(inventario, () -> operaciones.registrarAjuste(new AjusteRequest(v.id(), tipo, q, texto)));
            fisico[idx] += sumar ? q : -q;
            ajustes++;
        }

        private void subirPrecios() {
            en(admin, () -> {
                for (String codigo : variantesPorProducto.keySet()) {
                    if (!activo(codigo)) {
                        continue;
                    }
                    Long id = catalogo.productoIds().get(codigo);
                    ProductoDetalleResponse actual = productos.obtener(id);
                    BigDecimal nuevo = alzaConTerminacion900(actual.precioVenta());
                    productos.actualizar(id, new ActualizarProductoRequest(actual.nombre(), actual.categoria().id(),
                            actual.precioCompra(), nuevo, actual.leadTimeDias(), actual.loteMinimo()));
                    precioVenta.put(codigo, nuevo);
                }
            });
            alzas++;
        }

        private void reponer() {
                        for (Map.Entry<String, List<Integer>> e : variantesPorProducto.entrySet()) {
                String codigo = e.getKey();
                ProductoSemilla p = productoPorCodigo.get(codigo);
                if (!activo(codigo)) {
                    continue;
                }
                if (p.descontinuado() && diaActual.isAfter(corteDescontinuado)) {
                    desactivarSiAgotado(codigo, e.getValue());
                    continue;
                }
                LocalDate fechaLlegada = diaActual.plusDays(p.leadTimeDias());
                if (fechaLlegada.isAfter(fin)) {
                    continue;
                }
                int dias = DIAS_REPOSICION_MIN + azar.nextInt(DIAS_REPOSICION_MAX - DIAS_REPOSICION_MIN + 1);
                Map<Integer, Integer> cantidades = null;
                for (int idx : e.getValue()) {
                    VarianteCreada v = variantes.get(idx);
                    if (agotable[idx] || disponible(idx) + enCamino[idx] > v.stockMinimo()) {
                        continue;
                    }
                    double demanda = modelo.demandaEsperada(p, diaActual, dias);
                    int cantidad = Math.max(p.loteMinimo(), (int) Math.ceil(
                            demanda * ModeloDemanda.participacionVariante(p, v.talla(), v.indiceColor())));
                    if (cantidades == null) {
                        cantidades = new LinkedHashMap<>();
                    }
                    cantidades.put(idx, cantidad);
                    enCamino[idx] += cantidad;
                }
                if (cantidades != null) {
                    BigDecimal costo = CreadorCatalogo.costoEntrada(p.costoBase(),
                            modelo.mesesDesdeInicio(diaActual), costos);
                    llegadas.computeIfAbsent(fechaLlegada, k -> new ArrayList<>())
                            .add(new Llegada(codigo, cantidades, costo));
                }
            }
        }

        private void desactivarSiAgotado(String codigo, List<Integer> idxs) {
            for (int idx : idxs) {
                if (fisico[idx] > 0 || enCamino[idx] > 0) {
                    return;
                }
            }
            Long id = catalogo.productoIds().get(codigo);
            en(admin, () -> productos.cambiarEstado(id, false));
            desactivados.add(codigo);
        }

        private void recibir(Llegada l) {
            List<LineaEntradaRequest> lineas = new ArrayList<>();
            for (Map.Entry<Integer, Integer> e : l.cantidades().entrySet()) {
                lineas.add(new LineaEntradaRequest(variantes.get(e.getKey()).id(), e.getValue(), l.costo()));
            }
            en(inventario, () -> operaciones.registrarEntrada(
                    new EntradaRequest("Reposición de " + l.codigo(), lineas)));
            for (Map.Entry<Integer, Integer> e : l.cantidades().entrySet()) {
                fisico[e.getKey()] += e.getValue();
                enCamino[e.getKey()] -= e.getValue();
            }
            entradas++;
        }
    }

    /** +5 % y terminación 900: 79.900 → 83.895 → 83.900 (sube al siguiente x900 si no supera el precio actual). */
    static BigDecimal alzaConTerminacion900(BigDecimal precio) {
        long objetivo = precio.multiply(ALZA_ENERO).setScale(0, RoundingMode.HALF_UP).longValueExact();
        long nuevo = (objetivo / 1000) * 1000 + 900;
        if (nuevo > objetivo + 500) {
            nuevo -= 1000;
        }
        if (nuevo <= precio.longValue()) {
            nuevo += 1000;
        }
        return BigDecimal.valueOf(nuevo).setScale(2);
    }
}
