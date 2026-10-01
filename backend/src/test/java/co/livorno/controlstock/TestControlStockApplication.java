package co.livorno.controlstock;

import org.springframework.boot.SpringApplication;

public class TestControlStockApplication {

	public static void main(String[] args) {
		SpringApplication.from(ControlStockApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
