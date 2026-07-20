package com.bixx.rapid_engine.rabbitmq;

import com.bixx.rapid_engine.config.JacksonConfig;
import com.bixx.rapid_engine.messaging.EventChannel;
import com.bixx.rapid_engine.messaging.EventPublishException;
import com.bixx.rapid_engine.models.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitEventPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;

    private RabbitMQConfig rabbitMQConfig;
    private ObjectMapper objectMapper;
    private RabbitEventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitMQConfig = new RabbitMQConfig();
        rabbitMQConfig.setMatches(destination("matches.exchange", "matches.queue", "matches.routing.key"));
        rabbitMQConfig.setResults(destination("results.exchange", "results.queue", "results.routing.key"));
        objectMapper = new JacksonConfig().objectMapper();
        publisher = new RabbitEventPublisher(rabbitTemplate, rabbitMQConfig, objectMapper, Duration.ofSeconds(5));
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void publishesMatchesAsSharedJsonToConfiguredExchangeAndRoutingKey() throws Exception {
        Event event = Event.builder().eventId("event-1").build();
        arrangeConfirm(true, null, null);

        publisher.publish(EventChannel.MATCHES, event);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).convertAndSend(
                eq("matches.exchange"),
                eq("matches.routing.key"),
                messageCaptor.capture(),
                any(CorrelationData.class));
        assertThat(new String(messageCaptor.getValue().getBody(), StandardCharsets.UTF_8))
                .isEqualTo(objectMapper.writeValueAsString(event));
        assertThat(messageCaptor.getValue().getMessageProperties().getContentType())
                .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    }

    @Test
    void publishesResultsToResultsDestination() {
        arrangeConfirm(true, null, null);

        publisher.publish(EventChannel.RESULTS, Event.builder().eventId("result-1").build());

        verify(rabbitTemplate).convertAndSend(
                eq("results.exchange"),
                eq("results.routing.key"),
                any(Message.class),
                any(CorrelationData.class));
    }

    @Test
    void throwsWhenBrokerNegativelyAcknowledgesMessage() {
        arrangeConfirm(false, "broker nacked publish", null);

        assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("negatively acknowledged");
    }

    @Test
    void throwsWhenBrokerReturnsUnroutableMessage() {
        Message returned = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        arrangeConfirm(true, null, new ReturnedMessage(returned, 312, "NO_ROUTE", "matches.exchange", "missing.key"));

        assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("returned");
    }

    @Test
    void throwsWhenConfirmDoesNotArriveBeforeTimeout() {
        publisher = new RabbitEventPublisher(rabbitTemplate, rabbitMQConfig, objectMapper, Duration.ofMillis(1));

        assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void throwsBeforeSendingWhenSerializationFails() throws Exception {
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failingMapper.writeValueAsBytes(any(Event.class)))
                .thenThrow(new JsonProcessingException("bad JSON") { });
        publisher = new RabbitEventPublisher(rabbitTemplate, rabbitMQConfig, failingMapper, Duration.ofSeconds(5));

        assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("serialize");
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void restoresInterruptFlagWhenInterruptedWaitingForConfirmation() {
        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("Interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private RabbitMQConfig.QueueConfig destination(String exchange, String queue, String routingKey) {
        RabbitMQConfig.QueueConfig destination = new RabbitMQConfig.QueueConfig();
        destination.setExchange(exchange);
        destination.setQueue(queue);
        destination.setRoutingKey(routingKey);
        return destination;
    }

    private void arrangeConfirm(boolean acknowledged, String reason, ReturnedMessage returned) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            if (returned != null) {
                correlationData.setReturned(returned);
            }
            correlationData.getFuture().complete(new CorrelationData.Confirm(acknowledged, reason));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }
}
