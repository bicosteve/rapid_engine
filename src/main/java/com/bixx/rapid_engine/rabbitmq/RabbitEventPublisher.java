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
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final Duration DEFAULT_CONFIRM_TIMEOUT = Duration.ofSeconds(10);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;
    private final ObjectMapper objectMapper;
    private final Duration confirmTimeout;

    @Autowired
    public RabbitEventPublisher(
            RabbitTemplate rabbitTemplate,
            RabbitMQConfig rabbitMQConfig,
            ObjectMapper objectMapper){
        this(rabbitTemplate, rabbitMQConfig, objectMapper, DEFAULT_CONFIRM_TIMEOUT);
    }

    RabbitEventPublisher(
            RabbitTemplate rabbitTemplate,
            RabbitMQConfig rabbitMQConfig,
            ObjectMapper objectMapper,
            Duration confirmTimeout){
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMQConfig = rabbitMQConfig;
        this.objectMapper = objectMapper;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(EventChannel channel, Event event){
        RabbitMQConfig.QueueConfig destination = this.destinationFor(channel);
        Message message = this.messageFor(event);
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());

        try {
            rabbitTemplate.send(destination.getExchange(), destination.getRoutingKey(), message, correlationData);
            CorrelationData.Confirm confirmation = correlationData.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if(!confirmation.isAck()) {
                throw new EventPublishException("RabbitMQ publisher confirm nack: " + confirmation.getReason());
            }
            if(correlationData.getReturned() != null) {
                throw new EventPublishException("RabbitMQ message was returned as unroutable");
            }
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Interrupted while awaiting RabbitMQ publisher confirmation", exception);
        } catch(TimeoutException exception) {
            throw new EventPublishException("Timed out awaiting RabbitMQ publisher confirmation", exception);
        } catch(ExecutionException exception) {
            throw new EventPublishException("RabbitMQ publisher confirmation failed", exception);
        } catch(EventPublishException exception) {
            throw exception;
        } catch(RuntimeException exception) {
            throw new EventPublishException("Failed to publish RabbitMQ message", exception);
        }
    }

    private RabbitMQConfig.QueueConfig destinationFor(EventChannel channel){
        return switch(channel) {
            case MATCHES ->
                    rabbitMQConfig.getMatches();
            case RESULTS ->
                    rabbitMQConfig.getResults();
        };
    }

    private Message messageFor(Event event){
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            return new Message(objectMapper.writeValueAsBytes(event), properties);
        } catch(JsonProcessingException exception) {
            throw new EventPublishException("Failed to serialize event for RabbitMQ", exception);
        }
    }
}
