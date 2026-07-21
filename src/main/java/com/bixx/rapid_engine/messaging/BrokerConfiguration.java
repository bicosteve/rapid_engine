package com.bixx.rapid_engine.messaging;

import com.bixx.rapid_engine.kafka.KafkaConfig;
import com.bixx.rapid_engine.rabbitmq.RabbitMQConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

final class BrokerConfiguration {

    private BrokerConfiguration() {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "rabbitmq")
	@EnableConfigurationProperties(RabbitMQConfig.class)
    static class Rabbit {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "kafka")
	@EnableConfigurationProperties(KafkaConfig.class)
    static class Kafka {
    }
}
