package com.bixx.rapid_engine.kafka;

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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KafkaConfig config;
    private KafkaEventPublisher publisher;

    @BeforeEach
    void setUp() {
        config = new KafkaConfig();
        config.setMatchesTopic("matches.events");
        config.setResultsTopic("results.events");
        publisher = new KafkaEventPublisher(kafkaTemplate, config, objectMapper);
    }

    @Test
    void publishesMatchesToConfiguredTopicWithRawJsonAndNoKey() throws Exception {
        Event event = Event.builder().eventId("match-42").sportId(1).build();
        successfulSend();

        publisher.publish(EventChannel.MATCHES, event);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq("matches.events"), payload.capture());
        assertThat(payload.getValue()).isEqualTo(objectMapper.writeValueAsString(event));
    }

    @Test
    void publishesResultsToConfiguredTopic() {
        successfulSend();

        publisher.publish(EventChannel.RESULTS, Event.builder().eventId("result-42").build());

        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq("results.events"), anyString());
    }

    @Test
    void wrapsExceptionalSendFuture() {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(future);

        assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasCauseInstanceOf(ExecutionException.class);
    }

    @Test
    void wrapsImmediateTemplateFailure() {
        doThrow(new IllegalStateException("connection unavailable"))
                .when(kafkaTemplate).send(anyString(), anyString());

        assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrapsSendTimeout() {
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(new CompletableFuture<>());
        KafkaEventPublisher timeoutPublisher = new KafkaEventPublisher(
                kafkaTemplate, config, objectMapper, Duration.ofMillis(1));

        assertThatThrownBy(() -> timeoutPublisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("Timed out");
    }

    @Test
    void wrapsSerializationFailure() throws Exception {
        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any(Event.class)))
                .thenThrow(new JsonProcessingException("cannot serialize") { });
        KafkaEventPublisher failingPublisher = new KafkaEventPublisher(kafkaTemplate, config, failingMapper);

        assertThatThrownBy(() -> failingPublisher.publish(EventChannel.MATCHES, Event.builder().build()))
                .isInstanceOf(EventPublishException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void restoresInterruptStatusWhenSendWaitIsInterrupted() {
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(new CompletableFuture<>());
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

    private void successfulSend() {
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }
}
