package co.livorno.controlstock.analitica.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.analitica.modelo.EstadoEjecucion;
import co.livorno.controlstock.analitica.repositorio.AnaliticaVarianteRepository;
import co.livorno.controlstock.analitica.repositorio.EjecucionAnaliticaRepository;
import co.livorno.controlstock.analitica.repositorio.PronosticoRepository;
import co.livorno.controlstock.analitica.repositorio.VarianteSugerible;
import co.livorno.controlstock.compra.servicio.FuenteSugerenciaCompra.Sugerencia;
import co.livorno.controlstock.inventario.servicio.ConsultaPedidosEnCamino;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SugerenciaCompraAnaliticaTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Bogota"));

    private Sugerencia sugerirConPeriodo(int periodo) {
        var variantes = mock(AnaliticaVarianteRepository.class);
        var pronosticos = mock(PronosticoRepository.class);
        var ejecuciones = mock(EjecucionAnaliticaRepository.class);
        var transito = mock(ConsultaPedidosEnCamino.class);
        // dado: sin pronóstico, demanda histórica de 2/día, ROP 10, disponible 0, lote 1
        when(variantes.sugeribles(null)).thenReturn(List.of(
                new VarianteSugerible(1L, 10, 2, 0, 10, 1, new BigDecimal("2.0"), null)));
        when(pronosticos.findByVarianteIdIsNotNullOrderByVarianteIdAscPeriodoAsc()).thenReturn(List.of());
        when(ejecuciones.findFirstByEstadoOrderByIniciadaEnDescIdDesc(EstadoEjecucion.EXITOSA))
                .thenReturn(Optional.empty());
        when(transito.enCaminoPorVariante()).thenReturn(Map.of());
        return new SugerenciaCompraAnalitica(variantes, pronosticos, ejecuciones, transito, clock, periodo)
                .sugerir(null);
    }

    @Test
    void sugerir_conPeriodoDeRevisionDistinto_cambiaLaCantidadSugerida() {
        // cuando
        Sugerencia a30 = sugerirConPeriodo(30);
        Sugerencia a10 = sugerirConPeriodo(10);

        // entonces: S = 10 + 2 × R → 70 con R = 30 y 30 con R = 10
        assertThat(a30.ejecucionId()).isNull();
        assertThat(a30.lineas().get(0).cantidad()).isEqualTo(70);
        assertThat(a10.lineas().get(0).cantidad()).isEqualTo(30);
        assertThat(a10.lineas().get(0).justificacion()).contains("demanda histórica 10 d 20");
    }
}
