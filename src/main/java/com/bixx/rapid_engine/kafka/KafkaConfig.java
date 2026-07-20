package com.bixx.rapid_engine.kafka;

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
@ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "kafka")
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaConfig {
 @Valid
 @NotNull
 private TopicConfig matches;
 @Valid
 @NotNull
 private TopicConfig results;
private TopicDefaults topicDefaults = new TopicDefaults();

@Data
 public static class TopicConfig {
 @NotBlank
 private String topic;
}

@Data
public static class TopicDefaults {
private int partitions = 1;
private short replicationFactor = 1;
}
}
