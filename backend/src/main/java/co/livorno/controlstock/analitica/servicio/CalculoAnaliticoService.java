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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calcula la clasificación ABC y el punto de reorden de las variantes activas y los aplica (ADR-009, R1, R2, R4
 * paso 2). Una sola transacción, todo o nada: primero todas las lecturas y los cálculos en memoria; al final las
 * escrituras (instantánea, un UPDATE dirigido por variante en orden de id, limpieza de inactivas). Nunca toca stock
 * ni {@code version}: una venta concurrente no recibe 409 y su UPDATE no reescribe ROP ni clase.
 *
 * <p>Ventanas respecto del corte (excluye el día en curso): ABC {@code [corte − 365 d, corte)}; ROP
 * {@code [corte − 90 d, corte)}.
 */
@Service
public class CalculoAnaliticoService {

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

        // 3. Escrituras: instantánea, UPDATE dirigido por variante (orden de id) y limpieza de inactivas
        analitica.deleteAllInBatch();
        analitica.saveAll(filas);
        for (AnaliticaVariante fila : filas) {
            analitica.aplicarAVariante(fila.getVarianteId(), fila.getPuntoReorden(),
                    String.valueOf(fila.getClaseAbc()));
        }
        analitica.limpiarInactivas();

        return new ResultadoCalculo(filas.size(), a, b, c, conRop);
    }
}
