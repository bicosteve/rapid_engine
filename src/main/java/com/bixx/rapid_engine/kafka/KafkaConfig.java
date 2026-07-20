package com.bixx.rapid_engine.kafka;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaConfig {

    private String matchesTopic = "matches";
    private String resultsTopic = "results";
    private int partitions = 1;
    private short replicationFactor = 1;
}
