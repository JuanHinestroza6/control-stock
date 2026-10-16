package co.livorno.controlstock.analitica.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class PropiedadesPronosticoTest {

    private static PropiedadesPronostico enlazar(Map<String, String> valores) {
        return new Binder(new MapConfigurationPropertySource(valores))
                .bind("app.pronostico", PropiedadesPronostico.class).get();
    }

    @Test
    void enlazar_sinValores_cargaLosDefectosDelPlan() {
        PropiedadesPronostico p = enlazar(Map.of("app.pronostico.meses-historia", "36"));

        assertThat(p.mesesHistoria()).isEqualTo(36);
        assertThat(p.disponibilidadMinima()).isEqualByComparingTo("0.5");
        assertThat(p.ventanaMezclaDias()).isEqualTo(180);
        assertThat(p.horizonte()).isEqualTo(3);
        assertThat(p.mesesBacktest()).isEqualTo(6);
    }

    @Test
    void enlazar_valoresEnLosLimites_seAceptan() {
        PropiedadesPronostico p = enlazar(Map.of("app.pronostico.meses-historia", "3",
                "app.pronostico.disponibilidad-minima", "1", "app.pronostico.ventana-mezcla-dias", "730",
                "app.pronostico.horizonte", "12", "app.pronostico.meses-backtest", "3"));

        assertThat(p.mesesHistoria()).isEqualTo(3);
        assertThat(p.horizonte()).isEqualTo(12);
    }

    @Test
    void enlazar_valoresFueraDeRango_fallaAlArrancar() {
        for (Map<String, String> invalido : java.util.List.of(
                Map.of("app.pronostico.meses-historia", "2"),
                Map.of("app.pronostico.meses-historia", "121"),
                Map.of("app.pronostico.disponibilidad-minima", "0"),
                Map.of("app.pronostico.disponibilidad-minima", "1.01"),
                Map.of("app.pronostico.ventana-mezcla-dias", "0"),
                Map.of("app.pronostico.ventana-mezcla-dias", "731"),
                Map.of("app.pronostico.horizonte", "0"),
                Map.of("app.pronostico.horizonte", "13"),
                Map.of("app.pronostico.meses-backtest", "0"),
                Map.of("app.pronostico.meses-backtest", "37"))) {
            assertThatThrownBy(() -> enlazar(invalido)).as(invalido.toString())
                    .hasRootCauseInstanceOf(IllegalStateException.class);
        }
    }
}
