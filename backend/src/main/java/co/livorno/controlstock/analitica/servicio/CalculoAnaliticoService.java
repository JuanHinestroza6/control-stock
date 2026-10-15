package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.calculo.CalculadoraReorden;
import co.livorno.controlstock.analitica.calculo.ClasificacionAbc;
import co.livorno.controlstock.analitica.calculo.ClasificadorAbc;
import co.livorno.controlstock.analitica.calculo.IngresoVariante;
import co.livorno.controlstock.analitica.calculo.ResultadoReorden;
import co.livorno.controlstock.analitica.calculo.SerieDemanda;
import co.livorno.controlstock.analitica.config.PropiedadesAnalitica;
import co.livorno.controlstock.analitica.modelo.AnaliticaVariante;
import co.livorno.controlstock.analitica.repositorio.AnaliticaVarianteRepository;
import co.livorno.controlstock.analitica.repositorio.ConsultasVentaAnalitica;
import co.livorno.controlstock.analitica.repositorio.DemandaDiaVariante;
import co.livorno.controlstock.analitica.repositorio.VentaNetaVariante;
import co.livorno.controlstock.catalogo.repositorio.AnaliticaActualVariante;
import co.livorno.controlstock.catalogo.repositorio.VarianteAnalizable;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calcula la clasificación ABC y el punto de reorden de las variantes activas y los aplica (ADR-009, R1, R2, R4
 * paso 2). Una sola transacción, todo o nada: primero todas las lecturas y los cálculos en memoria; al final las
 * escrituras (un UPDATE dirigido por variante en orden de id solo donde hay cambio, limpieza de inactivas incluida, y al
 * final la instantánea). Nunca toca stock
 * ni {@code version}: una venta concurrente no recibe 409 y su UPDATE no reescribe ROP ni clase.
 *
 * <p>Ventanas respecto del corte (excluye el día en curso): ABC {@code [corte − 365 d, corte)}; ROP
 * {@code [corte − 90 d, corte)}.
 */
@Service
public class CalculoAnaliticoService {

    private static final Logger log = LoggerFactory.getLogger(CalculoAnaliticoService.class);
    private static final int INTENTOS = 5;
    private static final long ESPERA_MS = 200;

    private final VarianteRepository variantes;
    private final ConsultasVentaAnalitica consultas;
    private final AnaliticaVarianteRepository analitica;
    private final PropiedadesAnalitica propiedades;
    private final CalculadoraReorden calculadora;
    private final Clock clock;

    public CalculoAnaliticoService(VarianteRepository variantes, ConsultasVentaAnalitica consultas,
            AnaliticaVarianteRepository analitica, PropiedadesAnalitica propiedades, Clock clock) {
        this.variantes = variantes;
        this.consultas = consultas;
        this.analitica = analitica;
        this.propiedades = propiedades;
        this.calculadora = new CalculadoraReorden(propiedades);
        this.clock = clock;
    }

    /** Calcula y aplica con corte = hoy según el {@code Clock} (America/Bogota). */
    @Transactional
    public ResultadoCalculo calcularYAplicar(Long ejecucionId) {
        return ejecutar(LocalDate.now(clock), ejecucionId);
    }

    /**
     * Calcula y aplica con el corte dado.
     *
     * @param corte       fecha de corte (se excluye del cálculo)
     * @param ejecucionId ejecución existente a la que se enlaza la instantánea
     */
    @Transactional
    public ResultadoCalculo calcularYAplicar(LocalDate corte, Long ejecucionId) {
        return ejecutar(corte, ejecucionId);
    }

    private ResultadoCalculo ejecutar(LocalDate corte, Long ejecucionId) {
        Objects.requireNonNull(corte, "corte");
        Objects.requireNonNull(ejecucionId, "ejecucionId");
        LocalDateTime ahora = LocalDateTime.now(clock);

        // 1. Lecturas
        List<VarianteAnalizable> universo = variantes.activasConProducto();
        Map<Long, VentaNetaVariante> ventas = new HashMap<>();
        for (VentaNetaVariante v : consultas.ingresoNetoPorVariante(corte.minusDays(propiedades.ventanaAbcDias()),
                corte)) {
            ventas.put(v.varianteId(), v);
        }
        Map<Long, Map<LocalDate, Integer>> demanda = new HashMap<>();
        for (DemandaDiaVariante d : consultas.demandaDiariaNeta(
                corte.minusDays(propiedades.ventanaDemandaDias()), corte)) {
            demanda.computeIfAbsent(d.varianteId(), k -> new TreeMap<>()).put(d.dia(), d.neto());
        }

        // 2. Cálculo en memoria (solo variantes del universo: las inactivas no cuentan en el total)
        List<IngresoVariante> ingresos = new ArrayList<>(universo.size());
        for (VarianteAnalizable v : universo) {
            VentaNetaVariante venta = ventas.get(v.id());
            ingresos.add(new IngresoVariante(v.id(), venta == null ? BigDecimal.ZERO : venta.ingresoNeto()));
        }
        Map<Long, ClasificacionAbc> clases = new HashMap<>();
        for (ClasificacionAbc c : ClasificadorAbc.clasificar(ingresos, propiedades.corteA(), propiedades.corteB())) {
            clases.put(c.varianteId(), c);
        }

        List<AnaliticaVariante> filas = new ArrayList<>(universo.size());
        int a = 0;
        int b = 0;
        int c = 0;
        int conRop = 0;
        for (VarianteAnalizable v : universo) { // la consulta ya las entrega ordenadas por id
            ClasificacionAbc abc = clases.get(v.id());
            SerieDemanda serie = SerieDemanda.construir(demanda.getOrDefault(v.id(), Map.of()),
                    v.creadoEn().toLocalDate(), corte, propiedades.ventanaDemandaDias());
            ResultadoReorden rop = calculadora.calcular(serie, v.leadTimeDias(), abc.clase());
            switch (abc.clase()) {
                case 'A' -> a++;
                case 'B' -> b++;
                default -> c++;
            }
            if (rop.puntoReorden() != null) {
                conRop++;
            }
            VentaNetaVariante venta = ventas.get(v.id());
            filas.add(new AnaliticaVariante(v.id(), ejecucionId,
                    venta == null ? 0 : Math.toIntExact(venta.unidadesNetas()), abc.ingresoNeto(),
                    abc.participacion(), abc.acumulado(), abc.posicion(), abc.clase(), rop.diasHistoria(),
                    rop.demandaMedia(), rop.desviacion(), rop.leadTimeDias(), rop.nivelServicio(), rop.z(),
                    rop.stockSeguridad(), rop.puntoReorden(), rop.metodo(), ahora));
        }

        // 3. Escrituras. Primero los UPDATE dirigidos y solo después la instantánea: el INSERT de analitica_variante
        // toma S sobre la variante (FK) y, hecho antes, se cruzaría con la venta que ya tiene S y pedirá X (R1).
        aplicarCambios(filas);
        analitica.deleteAllInBatch();
        analitica.saveAll(filas);

        return new ResultadoCalculo(filas.size(), a, b, c, conRop);
    }

    /**
     * UPDATE dirigido por variante en orden de id, solo donde ROP o clase cambian (las inactivas con valores
     * previos quedan en null). Cada fila se bloquea con SKIP LOCKED: si una venta la tiene, no se espera (esperar
     * encolaría una X delante de la X que la venta pedirá al confirmar y se interbloquearían). Las ocupadas se
     * reintentan unos instantes; si siguen ocupadas se dejan para el próximo cálculo.
     */
    private void aplicarCambios(List<AnaliticaVariante> filas) {
        Map<Long, AnaliticaVariante> objetivo = new HashMap<>();
        for (AnaliticaVariante f : filas) {
            objetivo.put(f.getVarianteId(), f);
        }
        List<Cambio> pendientes = new ArrayList<>();
        for (AnaliticaActualVariante actual : variantes.analiticaActual()) { // ordenadas por id
            AnaliticaVariante f = objetivo.get(actual.id());
            Integer rop = f == null ? null : f.getPuntoReorden();
            Character clase = f == null ? null : Character.valueOf(f.getClaseAbc());
            if (!Objects.equals(rop, actual.puntoReorden()) || !Objects.equals(clase, actual.claseAbc())) {
                pendientes.add(new Cambio(actual.id(), rop, clase));
            }
        }
        for (int intento = 0; intento < INTENTOS && !pendientes.isEmpty(); intento++) {
            if (intento > 0) {
                esperar();
            }
            List<Cambio> ocupadas = new ArrayList<>();
            for (Cambio c : pendientes) {
                if (analitica.bloquearSiLibre(c.id()).isEmpty()) {
                    ocupadas.add(c);
                } else {
                    analitica.aplicarAVariante(c.id(), c.rop(), c.clase() == null ? null : c.clase().toString());
                }
            }
            pendientes = ocupadas;
        }
        if (!pendientes.isEmpty()) {
            log.warn("Analítica: {} variantes ocupadas por ventas en curso conservan su ROP/clase anterior hasta el"
                    + " próximo cálculo: {}", pendientes.size(), pendientes.stream().map(Cambio::id).toList());
        }
    }

    private static void esperar() {
        try {
            Thread.sleep(ESPERA_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Cambio(Long id, Integer rop, Character clase) {
    }
}
