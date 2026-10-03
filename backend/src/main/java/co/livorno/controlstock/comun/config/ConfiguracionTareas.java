package co.livorno.controlstock.comun.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las tareas programadas; {@code app.tareas.habilitadas=false} las apaga (pruebas, datos semilla). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.tareas.habilitadas", havingValue = "true", matchIfMissing = true)
public class ConfiguracionTareas {
}
