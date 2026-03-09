package com.bixx.rapid_engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // This will enable scheduling for the producer
public class RapidEngineApplication {
	public static void main(String[] args) {
		SpringApplication.run(RapidEngineApplication.class, args);
	}

}
