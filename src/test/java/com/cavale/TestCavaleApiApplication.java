package com.cavale;

import org.springframework.boot.SpringApplication;

public class TestCavaleApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(CavaleApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
