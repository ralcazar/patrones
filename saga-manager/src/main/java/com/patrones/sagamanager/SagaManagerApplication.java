package com.patrones.sagamanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SagaManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SagaManagerApplication.class, args);
	}
}
