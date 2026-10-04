package co.livorno.controlstock.semilla;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Arranque de la carga de datos de demostración (ADR-008). Con {@code app.semilla.salir-al-terminar} termina la
 * aplicación con código 0 (éxito o "ya hay datos") o 1 (fallo o base no vacía); sin esa opción, un fallo se relanza.
 */
@Component
@Profile("seed")
@ConditionalOnProperty(name = "app.semilla.ejecutar", havingValue = "true", matchIfMissing = true)
public class EjecutorSemilla implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EjecutorSemilla.class);

    private final GeneradorDatosSemilla generador;
    private final PropiedadesSemilla propiedades;
    private final ConfigurableApplicationContext contexto;

    public EjecutorSemilla(GeneradorDatosSemilla generador, PropiedadesSemilla propiedades,
                           ConfigurableApplicationContext contexto) {
        this.generador = generador;
        this.propiedades = propiedades;
        this.contexto = contexto;
    }

    @Override
    public void run(String... args) {
        int codigo = 0;
        RuntimeException fallo = null;
        try {
            log.info("{}", generador.generar().texto());
        } catch (RuntimeException e) {
            codigo = 1;
            fallo = e;
            log.error("La carga de datos semilla falló: {}", e.getMessage(), e);
        }
        if (propiedades.salirAlTerminar()) {
            terminar(codigo);
        } else if (fallo != null) {
            throw fallo;
        }
    }

    /** Cierra el contexto y la JVM con el código dado. */
    void terminar(int codigo) {
        int salida = SpringApplication.exit(contexto, () -> codigo);
        System.exit(salida);
    }
}
