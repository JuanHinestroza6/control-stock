package co.livorno.controlstock.analitica.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.analitica.dto.PuntoTendencia;
import co.livorno.controlstock.analitica.dto.TendenciaResponse;
import co.livorno.controlstock.analitica.repositorio.ConsultasTendencias;
import co.livorno.controlstock.analitica.repositorio.FilaTendencia;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class TendenciasServiceTest {

    // 2026-10-15 02:00 UTC = 2026-10-14 21:00 en Bogota: el mes en curso es 2026-10
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T02:00:00Z"), ZoneId.of("America/Bogota"));
    private final ConsultasTendencias consultas = mock(ConsultasTendencias.class);
    private final TendenciasService servicio = new TendenciasService(consultas, clock);

    private static FilaTendencia fila(String mes, long unidades, String ingreso) {
        return new FilaTendencia(YearMonth.parse(mes), unidades, new BigDecimal(ingreso));
    }

    @Test
    void consultar_sinParametros_devuelve24MesesCompletosHastaElMesAnterior() {
        // dado: sin datos
        when(consultas.porMes(any(), any(), any(), any())).thenReturn(List.of());

        // cuando
        TendenciaResponse r = servicio.consultar(null, null, null, null);

        // entonces: 2024-10 .. 2026-09 y se pide un año más atrás para comparar
        assertThat(r.desde()).isEqualTo("2024-10");
        assertThat(r.hasta()).isEqualTo("2026-09");
        assertThat(r.puntos()).hasSize(24);
        assertThat(r.puntos().get(0).unidades()).isZero();
        assertThat(r.puntos().get(0).variacionUnidades()).isNull();
        assertThat(r.totalIngreso()).isEqualByComparingTo("0");
        verify(consultas).porMes(YearMonth.parse("2023-10"), YearMonth.parse("2026-09"), null, null);
    }

    @Test
    void consultar_conAnioAnterior_calculaVariacionConUnDecimal() {
        // dado: abril 2026 = 7 u / 700; abril 2025 = 3 u / 300; mayo sin año anterior
        when(consultas.porMes(any(), any(), any(), any())).thenReturn(List.of(
                fila("2025-04", 3, "300.00"), fila("2026-04", 7, "700.00"), fila("2026-05", 4, "400.00")));

        // cuando
        TendenciaResponse r = servicio.consultar("2026-04", "2026-05", 2L, 9L);

        // entonces
        PuntoTendencia abril = r.puntos().get(0);
        assertThat(abril.periodo()).isEqualTo("2026-04");
        assertThat(abril.unidades()).isEqualTo(7);
        assertThat(abril.unidadesAnioAnterior()).isEqualTo(3);
        assertThat(abril.variacionUnidades()).isEqualByComparingTo("133.3");
        assertThat(abril.variacionIngreso()).isEqualByComparingTo("133.3");
        PuntoTendencia mayo = r.puntos().get(1);
        assertThat(mayo.unidadesAnioAnterior()).isZero();
        assertThat(mayo.variacionUnidades()).isNull();
        assertThat(mayo.variacionIngreso()).isNull();
        assertThat(r.totalUnidades()).isEqualTo(11);
        assertThat(r.totalIngreso()).isEqualByComparingTo("1100.00");
        assertThat(r.categoriaId()).isEqualTo(2L);
        assertThat(r.productoId()).isEqualTo(9L);
        verify(consultas).porMes(eq(YearMonth.parse("2025-04")), eq(YearMonth.parse("2026-05")), eq(2L), eq(9L));
    }

    @Test
    void consultar_caidaRespectoAlAnioAnterior_variacionNegativa() {
        when(consultas.porMes(any(), any(), any(), any())).thenReturn(List.of(
                fila("2025-06", 8, "800.00"), fila("2026-06", 6, "600.00")));

        TendenciaResponse r = servicio.consultar("2026-06", "2026-06", null, null);

        assertThat(r.puntos().get(0).variacionUnidades()).isEqualByComparingTo("-25.0");
    }

    @Test
    void consultar_exactamente36Meses_esValido() {
        when(consultas.porMes(any(), any(), any(), any())).thenReturn(List.of());

        TendenciaResponse r = servicio.consultar("2023-10", "2026-09", null, null);

        assertThat(r.puntos()).hasSize(36);
    }

    @Test
    void consultar_masDe36Meses_lanza400() {
        assertThatThrownBy(() -> servicio.consultar("2023-09", "2026-09", null, null))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("36");
    }

    @Test
    void consultar_desdePosteriorAHasta_lanza400() {
        assertThatThrownBy(() -> servicio.consultar("2026-05", "2026-04", null, null))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void consultar_mesEnCursoOFuturo_lanza400() {
        assertThatThrownBy(() -> servicio.consultar("2026-08", "2026-10", null, null))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("completos");
    }

    @Test
    void consultar_formatoInvalido_lanza400() {
        assertThatThrownBy(() -> servicio.consultar("octubre", null, null, null))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("AAAA-MM");
    }
}
