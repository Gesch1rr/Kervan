package com.eyup.kervan;

import org.springframework.boot.SpringApplication;

public class TestKervanApplication {

	public static void main(String[] args) {
		SpringApplication.from(KervanApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
