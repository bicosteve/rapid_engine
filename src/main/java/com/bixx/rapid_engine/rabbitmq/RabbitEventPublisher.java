package com.bixx.rapid_engine.rabbitmq;

import com.bixx.rapid_engine.messaging.EventChannel;
import com.bixx.rapid_engine.messaging.EventPublishException;
import com.bixx.rapid_engine.messaging.EventPublisher;
import com.bixx.rapid_engine.models.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "rabbitmq")
public class RabbitEventPublisher implements EventPublisher {

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;
    private final ObjectMapper objectMapper;
    private final Duration confirmTimeout;

    public RabbitEventPublisher(
            RabbitTemplate rabbitTemplate,
            RabbitMQConfig rabbitMQConfig,
            ObjectMapper objectMapper) {
        this(rabbitTemplate, rabbitMQConfig, objectMapper, CONFIRM_TIMEOUT);
    }

    RabbitEventPublisher(
            RabbitTemplate rabbitTemplate,
            RabbitMQConfig rabbitMQConfig,
            ObjectMapper objectMapper,
            Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMQConfig = rabbitMQConfig;
        this.objectMapper = objectMapper;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(EventChannel channel, Event event) {
        RabbitMQConfig.QueueConfig destination = destinationFor(channel);
        Message message = toMessage(event);
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());

        rabbitTemplate.convertAndSend(
                destination.getExchange(), destination.getRoutingKey(), message, correlationData);

        awaitConfirmation(correlationData);
    }

    private void awaitConfirmation(CorrelationData correlationData) {
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new EventPublishException(
                        "RabbitMQ publish was negatively acknowledged: " + confirm.getReason());
            }
            if (correlationData.getReturned() != null) {
                throw new EventPublishException("RabbitMQ message was returned as unroutable");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublishException(
                    "Interrupted while waiting for RabbitMQ publish confirmation", exception);
        } catch (ExecutionException exception) {
            throw new EventPublishException(
                    "RabbitMQ publish confirmation failed", exception.getCause());
        } catch (TimeoutException exception) {
            throw new EventPublishException("RabbitMQ publish confirmation timed out", exception);
        }
    }

    private RabbitMQConfig.QueueConfig destinationFor(EventChannel channel) {
        return switch (channel) {
            case MATCHES -> rabbitMQConfig.getMatches();
            case RESULTS -> rabbitMQConfig.getResults();
        };
    }

    private Message toMessage(Event event) {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            return new Message(objectMapper.writeValueAsBytes(event), properties);
        } catch (JsonProcessingException exception) {
            throw new EventPublishException("Could not serialize event for RabbitMQ", exception);
        }
    }
}
