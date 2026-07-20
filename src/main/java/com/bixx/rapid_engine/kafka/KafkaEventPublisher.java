package com.bixx.rapid_engine.kafka;

import com.bixx.rapid_engine.messaging.EventChannel;
import com.bixx.rapid_engine.messaging.EventPublishException;
import com.bixx.rapid_engine.messaging.EventPublisher;
import com.bixx.rapid_engine.models.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "kafka")
public class KafkaEventPublisher implements EventPublisher {
private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

private final KafkaTemplate<String, String> kafkaTemplate;
private final KafkaConfig kafkaConfig;
private final ObjectMapper objectMapper;
private final Duration sendTimeout;

@Autowired
public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, KafkaConfig kafkaConfig,
ObjectMapper objectMapper) {
this(kafkaTemplate, kafkaConfig, objectMapper, SEND_TIMEOUT);
}

KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, KafkaConfig kafkaConfig,
ObjectMapper objectMapper, Duration sendTimeout) {
this.kafkaTemplate = kafkaTemplate;
this.kafkaConfig = kafkaConfig;
this.objectMapper = objectMapper;
this.sendTimeout = sendTimeout;
}

@Override
public void publish(EventChannel channel, Event event) {
String payload = serialize(event);
try {
kafkaTemplate.send(topicFor(channel), payload)
.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
} catch (InterruptedException exception) {
Thread.currentThread().interrupt();
throw new EventPublishException("Interrupted while waiting for Kafka acknowledgement", exception);
} catch (ExecutionException exception) {
throw new EventPublishException("Kafka publish failed", exception.getCause());
} catch (TimeoutException exception) {
throw new EventPublishException("Kafka publish acknowledgement timed out", exception);
}
}

private String topicFor(EventChannel channel) {
return switch (channel) {
case MATCHES -> kafkaConfig.getMatches().getTopic();
case RESULTS -> kafkaConfig.getResults().getTopic();
};
}

private String serialize(Event event) {
try {
return objectMapper.writeValueAsString(event);
} catch (JsonProcessingException exception) {
throw new EventPublishException("Could not serialize event for Kafka", exception);
}
}
}
