package com.bixx.rapid_engine.kafka;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "kafka")
class KafkaClientPropertiesConfiguration {

    private static final Set<String> OPTIONAL_SSL_STORE_PROPERTIES = Set.of(
            "ssl.truststore.location",
            "ssl.truststore.password",
            "ssl.keystore.location",
            "ssl.keystore.password",
            "ssl.key.password");

    @Bean
    static BeanPostProcessor blankOptionalKafkaSslPropertyFilter() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof KafkaProperties kafkaProperties) {
                    kafkaProperties.getProperties().entrySet().removeIf(entry ->
                            OPTIONAL_SSL_STORE_PROPERTIES.contains(entry.getKey())
                                    && !StringUtils.hasText(entry.getValue()));
                }
                return bean;
            }
        };
    }
}
