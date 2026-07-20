package com.bixx.rapid_engine.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingProfileConfigurationTest {

    @Test
    void devProfileParsesAndRequiresBrokerSelection() {
        Properties properties = propertiesFrom("application-dev.yaml");

        assertThat(properties.getProperty("app.messaging.broker")).isEqualTo("${MESSAGING_BROKER}");
        assertThat(properties.getProperty("spring.rabbitmq.publisher-confirm-type")).isEqualTo("correlated");
		assertThat(properties.getProperty("spring.rabbitmq.publisher-returns")).isEqualTo("true");
        assertThat(properties.getProperty("spring.kafka.admin.auto-create")).isEqualTo("true");
        assertThat(properties.getProperty("spring.kafka.security.protocol")).isEqualTo("${KAFKA_SECURITY_PROTOCOL:PLAINTEXT}");
        assertThat(properties.getProperty("spring.kafka.properties.sasl.mechanism")).isEqualTo("${KAFKA_SASL_MECHANISM:PLAIN}");
        assertThat(properties.getProperty("spring.kafka.properties.sasl.jaas.config")).isEqualTo("${KAFKA_SASL_JAAS_CONFIG:}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.endpoint.identification.algorithm"))
                .isEqualTo("${KAFKA_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM:https}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.truststore.location"))
                .isEqualTo("${KAFKA_SSL_TRUSTSTORE_LOCATION:}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.truststore.password"))
                .isEqualTo("${KAFKA_SSL_TRUSTSTORE_PASSWORD:}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.truststore.type"))
                .isEqualTo("${KAFKA_SSL_TRUSTSTORE_TYPE:JKS}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.keystore.location"))
                .isEqualTo("${KAFKA_SSL_KEYSTORE_LOCATION:}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.keystore.password"))
                .isEqualTo("${KAFKA_SSL_KEYSTORE_PASSWORD:}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.keystore.type"))
                .isEqualTo("${KAFKA_SSL_KEYSTORE_TYPE:JKS}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.key.password"))
                .isEqualTo("${KAFKA_SSL_KEY_PASSWORD:}");
        assertThat(properties.getProperty("app.rabbitmq.matches.exchange")).isNotBlank();

        assertThat(properties.getProperty("app.rabbitmq.results.exchange")).isNotBlank();
        assertThat(properties.getProperty("app.kafka.matches-topic")).isNotBlank();
        assertThat(properties.getProperty("app.kafka.results-topic")).isNotBlank();
    }

 @Test
 void exampleTemplatesKeepKafkaHostnameVerificationEnabled() throws IOException {
 assertThat(resolvedKafkaHostnameVerification("application-dev.yaml", ".env-example")).isEqualTo("https");
 assertThat(resolvedKafkaHostnameVerification("application-prod.yaml", "env.docker.example")).isEqualTo("https");
 }

 @Test
 void prodProfileParsesAndRequiresBrokerSelection() {

        Properties properties = propertiesFrom("application-prod.yaml");

        assertThat(properties.getProperty("app.messaging.broker")).isEqualTo("${MESSAGING_BROKER}");
        assertThat(properties.getProperty("spring.kafka.producer.key-serializer"))
            .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
        assertThat(properties.getProperty("spring.kafka.producer.value-serializer"))
            .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
        assertThat(properties.getProperty("spring.kafka.producer.acks")).isEqualTo("all");
		assertThat(properties.getProperty("spring.kafka.producer.properties.enable.idempotence")).isEqualTo("true");
        assertThat(properties.getProperty("spring.kafka.admin.auto-create")).isEqualTo("true");
        assertThat(properties.getProperty("spring.kafka.security.protocol")).isEqualTo("${KAFKA_SECURITY_PROTOCOL:PLAINTEXT}");
        assertThat(properties.getProperty("spring.kafka.properties.sasl.mechanism")).isEqualTo("${KAFKA_SASL_MECHANISM:PLAIN}");
        assertThat(properties.getProperty("spring.kafka.properties.sasl.jaas.config")).isEqualTo("${KAFKA_SASL_JAAS_CONFIG:}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.endpoint.identification.algorithm"))
                .isEqualTo("${KAFKA_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM:https}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.truststore.location"))
                .isEqualTo("${KAFKA_SSL_TRUSTSTORE_LOCATION:}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.truststore.password"))
                .isEqualTo("${KAFKA_SSL_TRUSTSTORE_PASSWORD:}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.truststore.type"))
                .isEqualTo("${KAFKA_SSL_TRUSTSTORE_TYPE:JKS}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.keystore.location"))
                .isEqualTo("${KAFKA_SSL_KEYSTORE_LOCATION:}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.keystore.password"))
                .isEqualTo("${KAFKA_SSL_KEYSTORE_PASSWORD:}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.keystore.type"))
                .isEqualTo("${KAFKA_SSL_KEYSTORE_TYPE:JKS}");
        assertThat(properties.getProperty("spring.kafka.properties.ssl.key.password"))
                .isEqualTo("${KAFKA_SSL_KEY_PASSWORD:}");
        assertThat(properties.getProperty("app.rabbitmq.matches.exchange")).isNotBlank();

        assertThat(properties.getProperty("app.rabbitmq.results.exchange")).isNotBlank();
        assertThat(properties.getProperty("app.kafka.matches-topic")).isNotBlank();
        assertThat(properties.getProperty("app.kafka.results-topic")).isNotBlank();
    }

    private String resolvedKafkaHostnameVerification(String profile, String environmentTemplate) throws IOException {
 Properties environment = new Properties();
 try (var input = Files.newInputStream(Path.of(environmentTemplate))) {
 environment.load(input);
 }

 StandardEnvironment springEnvironment = new StandardEnvironment();
 springEnvironment.getPropertySources().addFirst(new PropertiesPropertySource("example-template", environment));
 return springEnvironment.resolveRequiredPlaceholders(
 propertiesFrom(profile).getProperty("spring.kafka.properties.ssl.endpoint.identification.algorithm")
 );
 }

 private Properties propertiesFrom(String resource) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(resource));
        factory.afterPropertiesSet();
        return factory.getObject();
    }
}
