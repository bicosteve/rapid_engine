package com.bixx.rapid_engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableScheduling // This will enable scheduling for the producer
@EnableTransactionManagement // Activates transaction management
@ConfigurationPropertiesScan // Scans for classes annotated with @ConfigurationProperties(prefix="app.rabbitmq")
public class RapidEngineApplication {
	public static void main(String[] args) {
		SpringApplication.run(RapidEngineApplication.class, args);
	}

}
