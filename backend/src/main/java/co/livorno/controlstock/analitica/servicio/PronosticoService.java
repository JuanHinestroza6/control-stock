package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.calculo.CorrectorCensura;
import co.livorno.controlstock.analitica.calculo.DemandaVariante;
import co.livorno.controlstock.analitica.calculo.Desagregador;
import co.livorno.controlstock.analitica.calculo.DiasConStock;
import co.livorno.controlstock.analitica.calculo.DisponibilidadVariante;
import co.livorno.controlstock.analitica.calculo.PronosticoMensual;
import co.livorno.controlstock.analitica.calculo.PronosticoVariante;
import co.livorno.controlstock.analitica.calculo.ResultadoPronostico;
import co.livorno.controlstock.analitica.calculo.SelectorPronostico;
import co.livorno.controlstock.analitica.calculo.SerieCorregida;
import co.livorno.controlstock.analitica.calculo.SerieMensual;
import co.livorno.controlstock.analitica.config.PropiedadesPronostico;
import co.livorno.controlstock.analitica.modelo.AnaliticaProducto;
import co.livorno.controlstock.analitica.modelo.AnaliticaProductoMes;
import co.livorno.controlstock.analitica.modelo.MetodoPronostico;
import co.livorno.controlstock.analitica.modelo.Pronostico;
import co.livorno.controlstock.analitica.repositorio.AnaliticaProductoMesRepository;
import co.livorno.controlstock.analitica.repositorio.AnaliticaProductoRepository;
import co.livorno.controlstock.analitica.repositorio.ConsultasDemandaMensual;
import co.livorno.controlstock.analitica.repositorio.ConsultasDisponibilidad;
import co.livorno.controlstock.analitica.repositorio.DemandaMensualProducto;
import co.livorno.controlstock.analitica.repositorio.HistorialDisponibilidad;
import co.livorno.controlstock.analitica.repositorio.PronosticoRepository;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pronóstico mensual por producto y desagregación a variantes (ADR-010, decisiones 1 y 2): serie de demanda de
 * inventario, corrección por agotados, selección SES/Holt-Winters por backtesting, reparto entre las variantes
 * activas y reemplazo de las tablas de la última ejecución ({@code pronostico}, {@code analitica_producto},
 * {@code analitica_producto_mes}).
 *
 * <p>Una sola transacción, todo o nada: primero todas las lecturas y los cálculos en memoria; al final las escrituras
 * (borrado de la ejecución anterior e inserciones). Las filas de {@code pronostico} con variante se insertan en orden
 * ascendente de {@code variante_id}, el mismo orden en que las ventas bloquean (R5: sin ciclo de espera con una venta
 * de varias líneas). Nunca toca stock ni {@code Variante} (regla de oro 1): solo lee y escribe sus propias tablas.
 */
@Service
public class PronosticoService {

    private final ConsultasDemandaMensual demanda;
    private final ConsultasDisponibilidad disponibilidad;
    private final ProductoRepository productos;
    private final PronosticoRepository pronosticos;
    private final AnaliticaProductoRepository analiticaProductos;
    private final AnaliticaProductoMesRepository analiticaMeses;
    private final PropiedadesPronostico propiedades;
    private final Clock clock;

    public PronosticoService(ConsultasDemandaMensual demanda, ConsultasDisponibilidad disponibilidad,
            ProductoRepository productos, PronosticoRepository pronosticos,
            AnaliticaProductoRepository analiticaProductos, AnaliticaProductoMesRepository analiticaMeses,
            PropiedadesPronostico propiedades, Clock clock) {
        this.demanda = demanda;
        this.disponibilidad = disponibilidad;
        this.productos = productos;
        this.pronosticos = pronosticos;
        this.analiticaProductos = analiticaProductos;
        this.analiticaMeses = analiticaMeses;
        this.propiedades = propiedades;
        this.clock = clock;
    }

    /** Calcula y guarda con corte = hoy según el {@code Clock} (America/Bogota). */
    @Transactional
    public ResultadoPronosticoGuardado calcularYGuardar(Long ejecucionId) {
        return calcularYGuardar(ejecucionId, LocalDate.now(clock));
    }

    /**
     * Calcula y guarda con el corte dado: el mes de {@code corte} es el mes actual (primero pronosticado, fuera de la
     * serie) y la ventana de mezcla es {@code [corte − 180 d, corte)}.
     */
    @Transactional
    public ResultadoPronosticoGuardado calcularYGuardar(Long ejecucionId, LocalDate corte) {
        Objects.requireNonNull(ejecucionId, "ejecucionId");
        Objects.requireNonNull(corte, "corte");
        LocalDateTime ahora = LocalDateTime.now(clock);
        YearMonth mesActual = YearMonth.from(corte);
        LocalDate inicioMes = mesActual.atDay(1);
        LocalDate inicioSerie = mesActual.minusMonths(propiedades.mesesHistoria()).atDay(1);
        LocalDate inicioMezcla = corte.minusDays(propiedades.ventanaMezclaDias());
        double piso = propiedades.disponibilidadMinima().doubleValue();

        // 1. Lecturas (una consulta por lote). Solo productos activos; el historial ya trae solo variantes activas.
        List<Producto> activos = productos.findAll().stream().filter(Producto::isActivo)
                .sorted(Comparator.comparing(Producto::getId)).toList();
        Map<Long, Map<YearMonth, Integer>> netoPorProducto = new HashMap<>();
        for (DemandaMensualProducto f : demanda.netoPorProductoYMes(mesActual, propiedades.mesesHistoria())) {
            netoPorProducto.computeIfAbsent(f.productoId(), k -> new HashMap<>()).put(f.mes(), f.neto());
        }
        Map<Long, Long> demandaSerie = demanda.netoPorVariante(inicioSerie, inicioMes);
        Map<Long, Long> demandaMezcla = demanda.netoPorVariante(inicioMezcla, corte);
        Map<Long, List<HistorialDisponibilidad>> historiales = new HashMap<>();
        for (HistorialDisponibilidad h : disponibilidad.historial(
                inicioSerie.isBefore(inicioMezcla) ? inicioSerie : inicioMezcla, corte)) {
            historiales.computeIfAbsent(h.productoId(), k -> new ArrayList<>()).add(h);
        }

        // 2. Cálculo en memoria
        List<AnaliticaProducto> filasProducto = new ArrayList<>();
        List<AnaliticaProductoMes> filasMes = new ArrayList<>();
        List<Pronostico> filasPronosticoProducto = new ArrayList<>();
        List<Pronostico> filasVariante = new ArrayList<>();
        Map<Long, PronosticoMensual> porVariante = new HashMap<>();
        int conHw = 0;
        int conSes = 0;
        double errorTotal = 0;
        double realTotal = 0;

        for (Producto p : activos) {
            SerieMensual serie = SerieMensual.construir(netoPorProducto.getOrDefault(p.getId(), Map.of()),
                    p.getCreadoEn(), mesActual, propiedades.mesesHistoria());
            List<HistorialDisponibilidad> variantes = historiales.getOrDefault(p.getId(), List.of());
            List<DisponibilidadVariante> insumos = new ArrayList<>(variantes.size());
            for (HistorialDisponibilidad h : variantes) {
                insumos.add(new DisponibilidadVariante(demandaSerie.getOrDefault(h.varianteId(), 0L),
                        CorrectorCensura.diasConStockPorMes(h.saldos(), h.creadaEn(), serie.mesInicial(),
                                serie.meses())));
            }
            SerieCorregida corregida = CorrectorCensura.corregir(serie, insumos, piso);
            ResultadoPronostico r = SelectorPronostico.seleccionar(corregida.corregida(), propiedades.horizonte());

            filasProducto.add(instantanea(p.getId(), ejecucionId, corregida, r, ahora));
            filasMes.addAll(meses(p.getId(), ejecucionId, corregida, r));
            if (r.metodo() == MetodoPronostico.SIN_HISTORIA) {
                continue;
            }
            if (r.metodo() == MetodoPronostico.HOLT_WINTERS) {
                conHw++;
            } else {
                conSes++;
            }
            if (r.conBacktest() && r.realBacktest() > 0) {
                errorTotal += r.errorAbsolutoBacktest();
                realTotal += r.realBacktest();
            }
            for (int i = 0; i < r.pronostico().size(); i++) {
                filasPronosticoProducto.add(new Pronostico(p.getId(), null, mesActual.plusMonths(i).atDay(1),
                        r.pronostico().get(i), r.metodo(), r.errorWape(), ahora, ejecucionId));
            }
            List<DemandaVariante> insumosMezcla = new ArrayList<>(variantes.size());
            for (HistorialDisponibilidad h : variantes) {
                DiasConStock dias = CorrectorCensura.diasConStock(h.saldos(), h.creadaEn(), inicioMezcla, corte);
                insumosMezcla.add(new DemandaVariante(h.varianteId(), demandaMezcla.getOrDefault(h.varianteId(), 0L),
                        dias));
            }
            for (PronosticoVariante pv : Desagregador.desagregar(r.pronostico(), insumosMezcla, piso)) {
                porVariante.put(pv.varianteId(), new PronosticoMensual(mesActual, pv.pronostico()));
                for (int i = 0; i < pv.pronostico().size(); i++) {
                    filasVariante.add(new Pronostico(p.getId(), pv.varianteId(), mesActual.plusMonths(i).atDay(1),
                            pv.pronostico().get(i), r.metodo(), r.errorWape(), ahora, ejecucionId));
                }
            }
        }

        // 3. Escrituras: reemplazo de la ejecución anterior; variantes en orden ascendente de id (R5)
        filasVariante.sort(Comparator.comparing(Pronostico::getVarianteId).thenComparing(Pronostico::getPeriodo));
        pronosticos.deleteAllInBatch();
        analiticaMeses.deleteAllInBatch();
        analiticaProductos.deleteAllInBatch();
        analiticaProductos.saveAll(filasProducto);
        analiticaMeses.saveAll(filasMes);
        pronosticos.saveAll(filasPronosticoProducto);
        pronosticos.saveAll(filasVariante);
        pronosticos.flush();

        BigDecimal wapeGlobal = realTotal > 0
                ? BigDecimal.valueOf(errorTotal / realTotal).setScale(4, RoundingMode.HALF_UP) : null;
        return new ResultadoPronosticoGuardado(porVariante, conHw + conSes, conHw, conSes, wapeGlobal);
    }

    private static AnaliticaProducto instantanea(Long productoId, Long ejecucionId, SerieCorregida c,
            ResultadoPronostico r, LocalDateTime ahora) {
        int n = c.meses();
        int[] observada = c.observada();
        double[] corregida = c.corregida();
        int observadas = 0;
        double corregidas = 0;
        for (int i = Math.max(0, n - 12); i < n; i++) {
            observadas += observada[i];
            corregidas += corregida[i];
        }
        return new AnaliticaProducto(productoId, ejecucionId, r.mesesHistoria(), r.metodo(), r.alfa(), r.beta(),
                r.gamma(), r.wapeSes(), r.wapeHw(), r.errorWape(), r.motivoSinHw(), observadas,
                BigDecimal.valueOf(corregidas).setScale(2, RoundingMode.HALF_UP), ahora);
    }

    private static List<AnaliticaProductoMes> meses(Long productoId, Long ejecucionId, SerieCorregida c,
            ResultadoPronostico r) {
        int n = c.meses();
        int[] observada = c.observada();
        double[] disp = c.disponibilidad();
        double[] corregida = c.corregida();
        // El backtest cubre los últimos 6 meses de la serie, en orden (orígenes n−6 y n−3, 3 meses cada uno).
        int inicioBacktest = n - r.backtestSes().size();
        List<AnaliticaProductoMes> filas = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int k = i - inicioBacktest;
            BigDecimal bSes = r.conBacktest() && k >= 0 ? r.backtestSes().get(k) : null;
            BigDecimal bHw = !r.backtestHw().isEmpty() && k >= 0 ? r.backtestHw().get(k) : null;
            filas.add(new AnaliticaProductoMes(productoId, c.mes(i).atDay(1), ejecucionId, observada[i],
                    BigDecimal.valueOf(Math.min(1.0, disp[i])).setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(corregida[i]).setScale(2, RoundingMode.HALF_UP), bSes, bHw));
        }
        return filas;
    }
}
