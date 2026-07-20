package com.bixx.rapid_engine.messaging;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.messaging")
public class MessagingProperties {
@NotNull
private Broker broker;

public enum Broker {
RABBITMQ,
KAFKA
}
}
