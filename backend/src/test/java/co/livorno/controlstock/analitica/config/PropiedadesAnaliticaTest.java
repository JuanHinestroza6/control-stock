package co.livorno.controlstock.analitica.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class PropiedadesAnaliticaTest {

    private static PropiedadesAnalitica enlazar(java.util.Map<String, String> valores) {
        return new Binder(new MapConfigurationPropertySource(valores))
                .bind("app.analitica", PropiedadesAnalitica.class).get();
    }

    @Test
    void enlazar_valoresDelYml_cargaLosDefectosDeLaFase5() {
        PropiedadesAnalitica p = enlazar(java.util.Map.of("app.analitica.cron", "0 0 2 * * *",
                "app.analitica.clases.a.nivel-servicio", "0.95", "app.analitica.clases.a.z", "1.65"));

        assertThat(p.ventanaAbcDias()).isEqualTo(365);
        assertThat(p.ventanaDemandaDias()).isEqualTo(90);
        assertThat(p.historiaMinimaDias()).isEqualTo(30);
        assertThat(p.corteA()).isEqualByComparingTo("0.80");
        assertThat(p.corteB()).isEqualByComparingTo("0.95");
        assertThat(p.parametrosDe('A').z()).isEqualByComparingTo("1.65");
        assertThat(p.parametrosDe('B').nivelServicio()).isEqualByComparingTo("0.90");
        assertThat(p.parametrosDe('C').z()).isEqualByComparingTo("0.84");
    }

    @Test
    void parametrosDe_claseNula_usaLosDeC() {
        PropiedadesAnalitica p = enlazar(java.util.Map.of("app.analitica.cron", "0 0 2 * * *"));

        assertThat(p.parametrosDe(null)).isEqualTo(p.parametrosDe('C'));
    }

    @Test
    void enlazar_cortesInvalidos_fallaAlArrancar() {
        assertThatThrownBy(() -> enlazar(java.util.Map.of("app.analitica.corte-a", "0.96")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void enlazar_historiaMinimaMayorQueLaVentana_fallaAlArrancar() {
        assertThatThrownBy(() -> enlazar(java.util.Map.of("app.analitica.historia-minima-dias", "120")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
