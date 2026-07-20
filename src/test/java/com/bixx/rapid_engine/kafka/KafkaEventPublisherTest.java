package com.bixx.rapid_engine.kafka;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

@Mock private KafkaTemplate<String, String> kafkaTemplate;

private KafkaConfig kafkaConfig;
private ObjectMapper objectMapper;
private KafkaEventPublisher publisher;

@BeforeEach
void setUp() {
kafkaConfig = configuredKafkaConfig();
objectMapper = new JacksonConfig().objectMapper();
publisher = new KafkaEventPublisher(kafkaTemplate, kafkaConfig, objectMapper, Duration.ofSeconds(5));
}

@AfterEach
void clearInterruptFlag() {
Thread.interrupted();
}

@Test
void publishesMatchesJsonWithoutKey() throws Exception {
Event event = Event.builder().eventId("event-1").build();
CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
when(kafkaTemplate.send(anyString(), anyString())).thenReturn(future);

publisher.publish(EventChannel.MATCHES, event);

verify(kafkaTemplate).send("matches.events", objectMapper.writeValueAsString(event));
verify(kafkaTemplate, never()).send(anyString(), isNull(), anyString());
}

@Test
void publishesResultsJsonToResultsTopic() throws Exception {
Event event = Event.builder().eventId("result-1").build();
CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
when(kafkaTemplate.send(anyString(), anyString())).thenReturn(future);

publisher.publish(EventChannel.RESULTS, event);

verify(kafkaTemplate).send("results.events", objectMapper.writeValueAsString(event));
}

@Test
void throwsWhenKafkaAcknowledgementFails() {
CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
future.completeExceptionally(new RuntimeException("broker unavailable"));
when(kafkaTemplate.send(anyString(), anyString())).thenReturn(future);

assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
.isInstanceOf(EventPublishException.class)
.hasMessageContaining("Kafka publish failed");
}

@Test
void throwsWhenKafkaAcknowledgementTimesOut() {
when(kafkaTemplate.send(anyString(), anyString())).thenReturn(new CompletableFuture<>());
publisher = new KafkaEventPublisher(kafkaTemplate, kafkaConfig, objectMapper, Duration.ofMillis(1));

assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
.isInstanceOf(EventPublishException.class)
.hasMessageContaining("timed out");
}

@Test
void throwsBeforeSendingWhenSerializationFails() throws Exception {
ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
when(failingMapper.writeValueAsString(any(Event.class)))
.thenThrow(new JsonProcessingException("bad JSON") { });
publisher = new KafkaEventPublisher(kafkaTemplate, kafkaConfig, failingMapper, Duration.ofSeconds(5));

assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
.isInstanceOf(EventPublishException.class)
.hasMessageContaining("serialize");
verifyNoInteractions(kafkaTemplate);
}

@Test
void restoresInterruptFlagWhenInterruptedWaitingForAcknowledgement() {
when(kafkaTemplate.send(anyString(), anyString())).thenReturn(new CompletableFuture<>());
Thread.currentThread().interrupt();

assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
.isInstanceOf(EventPublishException.class)
.hasMessageContaining("Interrupted");
assertThat(Thread.currentThread().isInterrupted()).isTrue();
}

private KafkaConfig configuredKafkaConfig() {
KafkaConfig config = new KafkaConfig();
config.setMatches(topic("matches.events"));
config.setResults(topic("results.events"));
return config;
}

private KafkaConfig.TopicConfig topic(String name) {
KafkaConfig.TopicConfig topic = new KafkaConfig.TopicConfig();
topic.setTopic(name);
return topic;
}
}
