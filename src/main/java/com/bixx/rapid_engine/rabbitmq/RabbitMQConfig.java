package com.bixx.rapid_engine.rabbitmq;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "rabbitmq")
@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMQConfig {
 @Valid
 @NotNull
 private QueueConfig matches;
 @Valid
 @NotNull
 private QueueConfig results;

 @Data
 public static class QueueConfig{
 @NotBlank
 private String exchange;
 @NotBlank
 private String queue;
 @NotBlank
 private String routingKey;
    }
}
