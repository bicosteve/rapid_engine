package com.bixx.rapid_engine.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "kafka")
public class KafkaTopicConfiguration {

    private final KafkaConfig kafkaConfig;

    public KafkaTopicConfiguration(KafkaConfig kafkaConfig) {
        this.kafkaConfig = kafkaConfig;
    }

    @Bean
    public NewTopic matchesTopic() {
        return new NewTopic(
                kafkaConfig.getMatchesTopic(),
                kafkaConfig.getPartitions(),
                kafkaConfig.getReplicationFactor());
    }

    @Bean
    public NewTopic resultsTopic() {
        return new NewTopic(
                kafkaConfig.getResultsTopic(),
                kafkaConfig.getPartitions(),
                kafkaConfig.getReplicationFactor());
    }
}
