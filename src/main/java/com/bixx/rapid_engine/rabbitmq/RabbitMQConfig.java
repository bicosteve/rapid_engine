package com.bixx.rapid_engine.rabbitmq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMQConfig {
    private QueueConfig matches;
    private QueueConfig results;

    @Data
    public static class QueueConfig{
        private String exchange;
        private String queue;
        private String routingKey;
    }
}
