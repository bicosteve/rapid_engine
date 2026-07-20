package com.bixx.rapid_engine;

import com.bixx.rapid_engine.messaging.MessagingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableConfigurationProperties(MessagingProperties.class)
@EnableScheduling
// This will enable scheduling for the producer
@EnableTransactionManagement
// Activates transaction management
public class Main {
    public static void main(String[] args){
        SpringApplication.run(Main.class, args);
    }

}
