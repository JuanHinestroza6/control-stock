package co.livorno.controlstock.analitica.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesAnalitica.class)
public class ConfiguracionAnalitica {
}
