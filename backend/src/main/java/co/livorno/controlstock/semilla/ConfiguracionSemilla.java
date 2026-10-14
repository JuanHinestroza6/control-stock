package co.livorno.controlstock.semilla;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("seed")
@EnableConfigurationProperties(PropiedadesSemilla.class)
public class ConfiguracionSemilla {
}
