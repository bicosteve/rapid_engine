package com.bixx.rapid_engine.kafka;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaConfig {
private TopicConfig matches;
private TopicConfig results;
private TopicDefaults topicDefaults = new TopicDefaults();

@Data
public static class TopicConfig {
private String topic;
}

@Data
public static class TopicDefaults {
private int partitions = 1;
private short replicationFactor = 1;
}
}
