package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.calculo.CalculadoraSugerenciaCompra;
import co.livorno.controlstock.analitica.calculo.InsumoSugerencia;
import co.livorno.controlstock.analitica.calculo.LineaSugerencia;
import co.livorno.controlstock.analitica.calculo.PronosticoMensual;
import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.EstadoEjecucion;
import co.livorno.controlstock.analitica.modelo.Pronostico;
import co.livorno.controlstock.analitica.repositorio.AnaliticaVarianteRepository;
import co.livorno.controlstock.analitica.repositorio.EjecucionAnaliticaRepository;
import co.livorno.controlstock.analitica.repositorio.PronosticoRepository;
import co.livorno.controlstock.analitica.repositorio.VarianteSugerible;
import co.livorno.controlstock.compra.servicio.FuenteSugerenciaCompra;
import co.livorno.controlstock.inventario.servicio.ConsultaPedidosEnCamino;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el puerto {@link FuenteSugerenciaCompra} (ADR-010): arma los insumos de cada variante activa (ROP o stock
 * mínimo, disponible, en camino, lead time, lote, pronóstico guardado o demanda histórica) y delega el cálculo (s, S)
 * en {@link CalculadoraSugerenciaCompra}. Solo lee; nunca toca stock. Sin ejecución analítica exitosa igual funciona
 * (umbral = stockMinimo, demanda histórica 0) y {@code ejecucionId} queda null.
 */
@Service
@Transactional(readOnly = true)
public class SugerenciaCompraAnalitica implements FuenteSugerenciaCompra {

    private final AnaliticaVarianteRepository variantes;
    private final PronosticoRepository pronosticos;
    private final EjecucionAnaliticaRepository ejecuciones;
    private final ConsultaPedidosEnCamino enCamino;
    private final Clock clock;
    private final int periodoRevisionDias;

    public SugerenciaCompraAnalitica(AnaliticaVarianteRepository variantes, PronosticoRepository pronosticos,
            EjecucionAnaliticaRepository ejecuciones, ConsultaPedidosEnCamino enCamino, Clock clock,
            @Value("${app.compras.periodo-revision-dias:30}") int periodoRevisionDias) {
        this.variantes = variantes;
        this.pronosticos = pronosticos;
        this.ejecuciones = ejecuciones;
        this.enCamino = enCamino;
        this.clock = clock;
        this.periodoRevisionDias = periodoRevisionDias;
    }

    @Override
    public Sugerencia sugerir(Long categoriaId) {
        LocalDate corte = LocalDate.now(clock);
        Long ejecucionId = ejecuciones.findFirstByEstadoOrderByIniciadaEnDescIdDesc(EstadoEjecucion.EXITOSA)
                .map(EjecucionAnalitica::getId).orElse(null);
        Map<Long, PronosticoMensual> pronostico = pronosticoPorVariante();
        Map<Long, Long> transito = enCamino.enCaminoPorVariante();

        List<InsumoSugerencia> insumos = new ArrayList<>();
        for (VarianteSugerible v : variantes.sugeribles(categoriaId)) {
            double demandaMedia = v.demandaMedia() == null ? 0.0 : v.demandaMedia().doubleValue();
            long camino = transito.getOrDefault(v.id(), 0L);
            insumos.add(new InsumoSugerencia(v.id(), v.puntoReorden(), v.stockMinimo(), v.disponible(),
                    (int) Math.min(camino, Integer.MAX_VALUE), v.leadTimeDias(), v.loteMinimo(),
                    pronostico.get(v.id()), demandaMedia));
        }
        List<LineaSugerencia> lineas = new CalculadoraSugerenciaCompra(periodoRevisionDias).sugerir(insumos, corte);
        return new Sugerencia(ejecucionId, lineas.stream()
                .map(l -> new LineaSugerida(l.varianteId(), l.cantidad(), l.justificacion())).toList());
    }

    /** Series mensuales por variante desde las filas guardadas (último cálculo), meses consecutivos desde el primero. */
    private Map<Long, PronosticoMensual> pronosticoPorVariante() {
        Map<Long, List<Pronostico>> porVariante = new LinkedHashMap<>();
        for (Pronostico p : pronosticos.findByVarianteIdIsNotNullOrderByVarianteIdAscPeriodoAsc()) {
            porVariante.computeIfAbsent(p.getVarianteId(), k -> new ArrayList<>()).add(p);
        }
        Map<Long, PronosticoMensual> resultado = new LinkedHashMap<>();
        porVariante.forEach((varianteId, filas) -> {
            List<BigDecimal> cantidades = filas.stream().map(Pronostico::getCantidadProyectada).toList();
            resultado.put(varianteId, new PronosticoMensual(YearMonth.from(filas.get(0).getPeriodo()), cantidades));
        });
        return resultado;
    }
}
