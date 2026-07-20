package com.bixx.rapid_engine.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RabbitMQBeans}.
 *
 * <p>Each {@code @Bean} method is invoked directly with a hand-built
 * {@link RabbitMQConfig} so the wiring is verified end-to-end without
 * needing a real broker connection.
 */
@ExtendWith(MockitoExtension.class)
class RabbitMQBeansTest {

 private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
 .withUserConfiguration(RabbitMQBeans.class);

 @Mock private ConnectionFactory connectionFactory;

    private RabbitMQConfig config;
    private RabbitMQBeans beans;

    @BeforeEach
    void setUp() {
        config = new RabbitMQConfig();

        RabbitMQConfig.QueueConfig matches = new RabbitMQConfig.QueueConfig();
        matches.setExchange("matches.exchange");
        matches.setQueue("matches.queue");
        matches.setRoutingKey("matches.routing.key");
        config.setMatches(matches);

        RabbitMQConfig.QueueConfig results = new RabbitMQConfig.QueueConfig();
        results.setExchange("results.exchange");
        results.setQueue("results.queue");
        results.setRoutingKey("results.routing.key");
        config.setResults(results);

        beans = new RabbitMQBeans(config);
    }

 @Test
 @DisplayName("Rabbit topology: is inactive when Kafka is selected")
 void rabbitTopology_isInactiveWhenKafkaIsSelected() {
 contextRunner.withPropertyValues("app.messaging.broker=kafka")
 .run(context -> assertThat(context).doesNotHaveBean(RabbitMQBeans.class));
 }

// ============================ matches wiring ============================

@Test
    @DisplayName("matchesQueue: uses the queue name from config")
    void matchesQueue_hasCorrectName() {
        Queue queue = beans.matchesQueue();

        assertThat(queue).isNotNull();
        assertThat(queue.getName()).isEqualTo("matches.queue");
    }

    @Test
    @DisplayName("matchesExchange: uses the exchange name from config")
    void matchesExchange_hasCorrectName() {
        TopicExchange exchange = beans.matchesExchange();

        assertThat(exchange).isNotNull();
        assertThat(exchange.getName()).isEqualTo("matches.exchange");
    }

    @Test
    @DisplayName("matchesBinding: binds matches.queue to matches.exchange with the right routing key")
    void matchesBinding_hasCorrectDestination() {
        Queue queue = beans.matchesQueue();
        TopicExchange exchange = beans.matchesExchange();

        Binding binding = beans.matchesBinding(queue, exchange);

        assertThat(binding).isNotNull();
        assertThat(binding.getExchange()).isEqualTo("matches.exchange");
        assertThat(binding.getDestination()).isEqualTo("matches.queue");
        assertThat(binding.getRoutingKey()).isEqualTo("matches.routing.key");
    }

    // ============================ results wiring ============================

    @Test
    @DisplayName("resultsQueue: uses the queue name from config")
    void resultsQueue_hasCorrectName() {
        Queue queue = beans.resultsQueue();

        assertThat(queue).isNotNull();
        assertThat(queue.getName()).isEqualTo("results.queue");
    }

    @Test
    @DisplayName("resultsExchange: uses the exchange name from config")
    void resultsExchange_hasCorrectName() {
        TopicExchange exchange = beans.resultsExchange();

        assertThat(exchange).isNotNull();
        assertThat(exchange.getName()).isEqualTo("results.exchange");
    }

    @Test
    @DisplayName("resultsBinding: binds results.queue to results.exchange with the right routing key")
    void resultsBinding_hasCorrectDestination() {
        Queue queue = beans.resultsQueue();
        TopicExchange exchange = beans.resultsExchange();

        Binding binding = beans.resultsBinding(queue, exchange);

        assertThat(binding).isNotNull();
        assertThat(binding.getExchange()).isEqualTo("results.exchange");
        assertThat(binding.getDestination()).isEqualTo("results.queue");
        assertThat(binding.getRoutingKey()).isEqualTo("results.routing.key");
    }

    // ============================ message converter / template ============================

    @Test
    @DisplayName("messageConverter: returns a Jackson2JsonMessageConverter")
    void messageConverter_isJacksonJsonConverter() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        MessageConverter converter = beans.messageConverter(objectMapper);

        assertThat(converter).isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    @Test
    @DisplayName("rabbitTemplate: uses the supplied connection factory and the supplied message converter")
    void rabbitTemplate_usesFactoryAndConverter() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MessageConverter converter = beans.messageConverter(objectMapper);

        RabbitTemplate template = beans.rabbitTemplate(connectionFactory, converter);

        assertThat(template).isNotNull();
 assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
 assertThat(template.getMessageConverter()).isSameAs(converter);
 assertThat(template.isMandatoryFor(new Message(new byte[0]))).isTrue();
    }
}
