package com.bixx.rapid_engine.rabbitmq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMQConfig {
    private String exchange;
    private String queue;
    private String routingKey;
}
