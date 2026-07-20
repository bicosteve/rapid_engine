package com.bixx.rapid_engine.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "kafka")
public class KafkaTopicConfiguration {
private final KafkaConfig kafkaConfig;

@Bean
public NewTopic matchesTopic() {
return topic(kafkaConfig.getMatches().getTopic());
}

@Bean
public NewTopic resultsTopic() {
return topic(kafkaConfig.getResults().getTopic());
}

private NewTopic topic(String name) {
KafkaConfig.TopicDefaults defaults = kafkaConfig.getTopicDefaults();
return new NewTopic(name, defaults.getPartitions(), defaults.getReplicationFactor());
}
}
