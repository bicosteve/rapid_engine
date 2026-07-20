package com.bixx.rapid_engine.rabbitmq;

import com.bixx.rapid_engine.messaging.EventChannel;
import com.bixx.rapid_engine.messaging.EventPublishException;
import com.bixx.rapid_engine.models.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitEventPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RabbitMQConfig config;
    private RabbitEventPublisher publisher;

    @BeforeEach
    void setUp() {
        config = config();
        publisher = new RabbitEventPublisher(rabbitTemplate, config, objectMapper);
    }

    @Test
    void publishesMatchesToConfiguredExchangeAndRoutingKeyWithRawJsonBody() throws Exception {
        confirmAcknowledgement();
        Event event = Event.builder().eventId("match-42").sportId(1).build();

        publisher.publish(EventChannel.MATCHES, event);

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(org.mockito.ArgumentMatchers.eq("matches.exchange"),
                org.mockito.ArgumentMatchers.eq("matches.key"), message.capture(), any(CorrelationData.class));
        assertThat(message.getValue().getBody()).isEqualTo(objectMapper.writeValueAsBytes(event));
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8))
                .isEqualTo(objectMapper.writeValueAsString(event));
        assertThat(message.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
    }

    @Test
    void publishesResultsToConfiguredExchangeAndRoutingKey() {
        confirmAcknowledgement();
        publisher.publish(EventChannel.RESULTS, Event.builder().eventId("result-42").build());

        verify(rabbitTemplate).send(org.mockito.ArgumentMatchers.eq("results.exchange"),
                org.mockito.ArgumentMatchers.eq("results.key"), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void rejectsNegativePublisherConfirmation() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker nacked"));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("nack");
    }

    @Test
    void rejectsReturnedUnroutableMessage() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE", "matches.exchange", "matches.key"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("returned");
    }

    @Test
    void wrapsExceptionalPublisherConfirmation() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().completeExceptionally(new IllegalStateException("confirm failed"));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasCauseInstanceOf(ExecutionException.class);
    }

    @Test
    void rejectsConfirmationTimeout() {
        RabbitEventPublisher timeoutPublisher = new RabbitEventPublisher(
                rabbitTemplate, config, objectMapper, Duration.ofMillis(1));
        doAnswer(invocation -> null)
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> timeoutPublisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("Timed out");
    }

    @Test
    void wrapsSerializationFailure() throws Exception {
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        org.mockito.Mockito.when(failingMapper.writeValueAsBytes(any(Event.class)))
                .thenThrow(new JsonProcessingException("cannot serialize") { });
        RabbitEventPublisher failingPublisher = new RabbitEventPublisher(rabbitTemplate, config, failingMapper);

        assertThatThrownBy(() -> failingPublisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void wrapsImmediateTemplateFailure() {
        doThrow(new IllegalStateException("connection unavailable"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void restoresInterruptStatusWhenConfirmationWaitIsInterrupted() {
        doAnswer(invocation -> null)
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
                    .isInstanceOf(EventPublishException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private void confirmAcknowledgement() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private RabbitMQConfig config() {
        RabbitMQConfig rabbitConfig = new RabbitMQConfig();
        rabbitConfig.setMatches(queue("matches.exchange", "matches.queue", "matches.key"));
        rabbitConfig.setResults(queue("results.exchange", "results.queue", "results.key"));
        return rabbitConfig;
    }

    private RabbitMQConfig.QueueConfig queue(String exchange, String queue, String routingKey) {
        RabbitMQConfig.QueueConfig config = new RabbitMQConfig.QueueConfig();
        config.setExchange(exchange);
        config.setQueue(queue);
        config.setRoutingKey(routingKey);
        return config;
    }
}
