package co.livorno.controlstock;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ControlStockApplication {

	public static final String ZONA_HORARIA = "America/Bogota";

	public static void main(String[] args) {
		// Regla de oro 5: toda la JVM trabaja en hora de Bogota (tambien -Duser.timezone en Surefire).
		TimeZone.setDefault(TimeZone.getTimeZone(ZONA_HORARIA));
		SpringApplication.run(ControlStockApplication.class, args);
	}

}
