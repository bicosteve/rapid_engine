package com.bixx.rapid_engine.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {

    @Test
    void usesTopicAndBrokerDefaults() {
        KafkaConfig config = new KafkaConfig();

        assertThat(config.getMatchesTopic()).isEqualTo("matches");
        assertThat(config.getResultsTopic()).isEqualTo("results");
        assertThat(config.getPartitions()).isEqualTo(1);
        assertThat(config.getReplicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void declaresMatchesTopicFromConfiguration() {
        KafkaConfig config = configuredConfig();

        NewTopic topic = new KafkaTopicConfiguration(config).matchesTopic();

        assertThat(topic.name()).isEqualTo("matches.events");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 2);
    }

    @Test
    void declaresResultsTopicFromConfiguration() {
        KafkaConfig config = configuredConfig();

        NewTopic topic = new KafkaTopicConfiguration(config).resultsTopic();

        assertThat(topic.name()).isEqualTo("results.events");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 2);
    }

 @Test
 void topologyIsInactiveUnlessKafkaIsSelected() {
 new ApplicationContextRunner()
 .withUserConfiguration(KafkaTopicConfiguration.class)
 .withPropertyValues("app.messaging.broker=rabbitmq")
 .run(context -> {
 assertThat(context).hasNotFailed();
 assertThat(context).doesNotHaveBean(KafkaTopicConfiguration.class);
 assertThat(context).doesNotHaveBean(NewTopic.class);
 });
 }

 @Test
 void omitsBlankOptionalSslStoresFromProducerAndAdminProperties() {
 new ApplicationContextRunner()
 .withInitializer(new ConfigDataApplicationContextInitializer())
 .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
 .withUserConfiguration(KafkaClientPropertiesConfiguration.class)
 .withPropertyValues(
 "spring.profiles.active=dev",
 "app.messaging.broker=kafka",
 "spring.kafka.bootstrap-servers=localhost:9092",
 "spring.kafka.security.protocol=SASL_SSL",
 "spring.kafka.properties.sasl.mechanism=PLAIN",
 "spring.kafka.properties.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username=\"user\" password=\"secret\";",
 "spring.kafka.admin.auto-create=false")
 .run(context -> {
 assertThat(context).hasNotFailed();

 DefaultKafkaProducerFactory<?, ?> producerFactory = context.getBean(DefaultKafkaProducerFactory.class);
 KafkaAdmin kafkaAdmin = context.getBean(KafkaAdmin.class);

 assertSaslPropertiesWithoutBlankSslStores(producerFactory.getConfigurationProperties());
 assertSaslPropertiesWithoutBlankSslStores(kafkaAdmin.getConfigurationProperties());
 });
 }

 private void assertSaslPropertiesWithoutBlankSslStores(Map<String, Object> properties) {
 assertThat(properties)
 .containsEntry("security.protocol", "SASL_SSL")
 .containsEntry("sasl.mechanism", "PLAIN")
 .containsEntry("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"user\" password=\"secret\";")
 .doesNotContainKeys(
 "ssl.truststore.location",
 "ssl.truststore.password",
 "ssl.keystore.location",
 "ssl.keystore.password",
 "ssl.key.password");
 }

 private KafkaConfig configuredConfig() {
        KafkaConfig config = new KafkaConfig();
        config.setMatchesTopic("matches.events");
        config.setResultsTopic("results.events");
        config.setPartitions(3);
        config.setReplicationFactor((short) 2);
        return config;
    }
}
