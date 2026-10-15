package co.livorno.controlstock.analitica.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.livorno.controlstock.analitica.dto.AgrupacionRanking;
import co.livorno.controlstock.analitica.dto.CriterioRanking;
import co.livorno.controlstock.analitica.dto.FiltroMasVendidos;
import co.livorno.controlstock.analitica.repositorio.ConsultasVentaAnalitica;
import co.livorno.controlstock.analitica.repositorio.MasVendidoFila;
import co.livorno.controlstock.analitica.repositorio.TotalVentaNeta;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MasVendidosServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    // 2026-10-15 02:00 UTC = 2026-10-14 21:00 en Bogota
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T02:00:00Z"), BOGOTA);
    private final ConsultasVentaAnalitica consultas = mock(ConsultasVentaAnalitica.class);
    private final MasVendidosService servicio = new MasVendidosService(consultas, clock);

    @Test
    void consultar_sinParametros_aplicaLosDefectosConLaFechaDeBogota() {
        when(consultas.totalVentaNeta(any())).thenReturn(new TotalVentaNeta(0, new BigDecimal("0.00")));
        when(consultas.masVendidos(any())).thenReturn(List.of());

        var r = servicio.consultar(null, null, null, null, null, null);

        ArgumentCaptor<FiltroMasVendidos> filtro = ArgumentCaptor.forClass(FiltroMasVendidos.class);
        verify(consultas).masVendidos(filtro.capture());
        assertThat(filtro.getValue().hasta()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(filtro.getValue().desde()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(filtro.getValue().criterio()).isEqualTo(CriterioRanking.UNIDADES);
        assertThat(filtro.getValue().agrupacion()).isEqualTo(AgrupacionRanking.PRODUCTO);
        assertThat(filtro.getValue().limite()).isEqualTo(10);
        assertThat(r.items()).isEmpty();
    }

    @Test
    void consultar_participacionConUnDecimalHalfUp() {
        var fila = new MasVendidoFila(1, "LIV001", "Camiseta", 2, "Camisetas", null, null, null, null, 1,
                new BigDecimal("10.00"), true);
        when(consultas.totalVentaNeta(any())).thenReturn(new TotalVentaNeta(8, new BigDecimal("60.00")));
        when(consultas.masVendidos(any())).thenReturn(List.of(fila));

        var unidades = servicio.consultar(null, null, null, CriterioRanking.UNIDADES, null, null);
        var ingreso = servicio.consultar(null, null, null, CriterioRanking.INGRESO, null, null);

        assertThat(unidades.items().get(0).participacion()).isEqualByComparingTo("12.5");
        assertThat(ingreso.items().get(0).participacion()).isEqualByComparingTo("16.7");
    }

    @Test
    void consultar_desdePosteriorAHasta_lanzaSolicitudInvalidaEnDesde() {
        assertThatThrownBy(() -> servicio.consultar(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null, null,
                null, null))
                .isInstanceOf(SolicitudInvalidaException.class)
                .extracting(e -> ((SolicitudInvalidaException) e).getCampo()).isEqualTo("desde");
    }

    @Test
    void consultar_rangoDe731Dias_esValidoYDe732Lanza() {
        when(consultas.totalVentaNeta(any())).thenReturn(new TotalVentaNeta(0, new BigDecimal("0.00")));
        when(consultas.masVendidos(any())).thenReturn(List.of());
        LocalDate hasta = LocalDate.of(2026, 10, 14);

        servicio.consultar(hasta.minusDays(730), hasta, null, null, null, null);

        assertThatThrownBy(() -> servicio.consultar(hasta.minusDays(731), hasta, null, null, null, null))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void consultar_limiteFueraDeRango_lanzaSolicitudInvalidaEnLimite() {
        for (int limite : new int[] {0, 51, -3}) {
            assertThatThrownBy(() -> servicio.consultar(null, null, null, null, null, limite))
                    .isInstanceOf(SolicitudInvalidaException.class)
                    .extracting(e -> ((SolicitudInvalidaException) e).getCampo()).isEqualTo("limite");
        }
    }
}
