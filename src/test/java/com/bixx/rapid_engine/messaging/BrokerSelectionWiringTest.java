package com.bixx.rapid_engine.messaging;

import com.bixx.rapid_engine.config.JacksonConfig;
import com.bixx.rapid_engine.kafka.KafkaConfig;
import com.bixx.rapid_engine.kafka.KafkaEventPublisher;
import com.bixx.rapid_engine.kafka.KafkaTopicConfiguration;
import com.bixx.rapid_engine.rabbitmq.RabbitEventPublisher;
import com.bixx.rapid_engine.rabbitmq.RabbitMQBeans;
import com.bixx.rapid_engine.rabbitmq.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BrokerSelectionWiringTest {

private final ApplicationContextRunner productionContextRunner = new ApplicationContextRunner()
 .withInitializer(new ConfigDataApplicationContextInitializer())
 .withUserConfiguration(
 MessagingTestConfiguration.class,
 JacksonConfig.class,
 RabbitMQConfig.class,
 RabbitMQBeans.class,
 RabbitEventPublisher.class,
 KafkaConfig.class,
 KafkaTopicConfiguration.class,
 KafkaEventPublisher.class)
 .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
 .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class));

 private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
 .withUserConfiguration(
            MessagingTestConfiguration.class,
            JacksonConfig.class,
            RabbitMQConfig.class,
            RabbitMQBeans.class,
 RabbitEventPublisher.class,
 KafkaConfig.class,
 KafkaTopicConfiguration.class,
            KafkaEventPublisher.class)
        .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
        .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class));

    @Test
    void rabbitmqSelectionCreatesOnlyRabbitPublisherAndTopology() {
        contextRunner.withPropertyValues(
                "app.messaging.broker=rabbitmq",
                "app.rabbitmq.matches.exchange=matches.exchange",
                "app.rabbitmq.matches.queue=matches.queue",
                "app.rabbitmq.matches.routing-key=matches.routing.key",
                "app.rabbitmq.results.exchange=results.exchange",
                "app.rabbitmq.results.queue=results.queue",
                "app.rabbitmq.results.routing-key=results.routing.key")
            .run(context -> {
                assertThat(context).hasSingleBean(EventPublisher.class);
                assertThat(context).hasSingleBean(RabbitEventPublisher.class);
                assertThat(context).doesNotHaveBean(KafkaEventPublisher.class);
                assertThat(context).hasBean("matchesQueue");
                assertThat(context).hasBean("matchesExchange");
                assertThat(context).hasBean("matchesBinding");
                assertThat(context).hasBean("resultsQueue");
                assertThat(context).hasBean("resultsExchange");
                assertThat(context).hasBean("resultsBinding");
                assertThat(context).doesNotHaveBean("matchesTopic");
                assertThat(context).doesNotHaveBean("resultsTopic");
            });
    }

    @Test
    void kafkaSelectionCreatesOnlyKafkaPublisherAndTopics() {
        contextRunner.withPropertyValues(
                "app.messaging.broker=kafka",
                "app.kafka.matches.topic=matches.events",
                "app.kafka.results.topic=results.events")
            .run(context -> {
                assertThat(context).hasSingleBean(EventPublisher.class);
                assertThat(context).hasSingleBean(KafkaEventPublisher.class);
                assertThat(context).doesNotHaveBean(RabbitEventPublisher.class);
                assertThat(context).hasBean("matchesTopic");
                assertThat(context).hasBean("resultsTopic");
                assertThat(context).doesNotHaveBean("matchesQueue");
                assertThat(context).doesNotHaveBean("matchesExchange");
                assertThat(context).doesNotHaveBean("matchesBinding");
                assertThat(context).doesNotHaveBean("resultsQueue");
                assertThat(context).doesNotHaveBean("resultsExchange");
                assertThat(context).doesNotHaveBean("resultsBinding");
            });
    }

@Test
 void productionRabbitmqSelectionStartsWithoutKafkaProperties() {
 productionContextRunner.withPropertyValues(
 "spring.profiles.active=prod",
 "MESSAGING_BROKER=rabbitmq",
 "RABBITMQ_HOST=rabbitmq.example",
 "RABBITMQ_USERNAME=user",
 "RABBITMQ_PASSWORD=password",
 "RABBITMQ_MATCHES_EXCHANGE=matches.exchange",
 "RABBITMQ_MATCHES_QUEUE=matches.queue",
 "RABBITMQ_MATCHES_ROUTING_KEY=matches.routing.key",
 "RABBITMQ_RESULTS_EXCHANGE=results.exchange",
 "RABBITMQ_RESULTS_QUEUE=results.queue",
 "RABBITMQ_RESULTS_ROUTING_KEY=results.routing.key")
 .run(context -> {
 assertThat(context).hasNotFailed();
 assertThat(context).hasSingleBean(RabbitEventPublisher.class);
 assertThat(context).doesNotHaveBean(KafkaEventPublisher.class);
 assertThat(context).hasBean("matchesQueue");
 assertThat(context).doesNotHaveBean("matchesTopic");
 assertThat(context).doesNotHaveBean(KafkaConfig.class);
 });
 }

 @Test
 void productionKafkaSelectionStartsWithoutRabbitmqProperties() {
 productionContextRunner.withPropertyValues(
 "spring.profiles.active=prod",
 "MESSAGING_BROKER=kafka",
 "KAFKA_BOOTSTRAP_SERVERS=kafka.example:9092",
 "KAFKA_MATCHES_TOPIC=matches.events",
 "KAFKA_RESULTS_TOPIC=results.events",
 "KAFKA_TOPIC_PARTITIONS=1",
 "KAFKA_TOPIC_REPLICATION_FACTOR=1")
 .run(context -> {
 assertThat(context).hasNotFailed();
 assertThat(context).hasSingleBean(KafkaEventPublisher.class);
 assertThat(context).doesNotHaveBean(RabbitEventPublisher.class);
 assertThat(context).hasBean("matchesTopic");
 assertThat(context).doesNotHaveBean("matchesQueue");
 assertThat(context).doesNotHaveBean(RabbitMQConfig.class);
 });
 }

 @Test
 void missingBrokerFailsWithPropertyName() {
        contextRunner.run(context -> assertThat(context.getStartupFailure())
            .hasStackTraceContaining("app.messaging.broker"));
    }

    @Test
    void unknownBrokerFailsWithPropertyName() {
        contextRunner.withPropertyValues("app.messaging.broker=unknown")
            .run(context -> assertThat(context.getStartupFailure())
                .hasStackTraceContaining("app.messaging.broker"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MessagingProperties.class)
    static class MessagingTestConfiguration {
    }
}
