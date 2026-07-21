package com.bixx.rapid_engine.rabbitmq;

import com.bixx.rapid_engine.Main;
import com.bixx.rapid_engine.messaging.EventChannel;
import com.bixx.rapid_engine.messaging.EventPublisher;
import com.bixx.rapid_engine.models.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = Main.class,
        properties = {
                "app.messaging.broker=rabbitmq",
                "app.rabbitmq.matches.exchange=integration.matches.exchange",
                "app.rabbitmq.matches.queue=integration.matches.queue",
                "app.rabbitmq.matches.routing-key=integration.matches",
                "app.rabbitmq.results.exchange=integration.results.exchange",
                "app.rabbitmq.results.queue=integration.results.queue",
                "app.rabbitmq.results.routing-key=integration.results",
                "spring.rabbitmq.publisher-confirm-type=correlated",
                "spring.rabbitmq.publisher-returns=true",
                "spring.kafka.admin.auto-create=false",
"spring.task.scheduling.enabled=false",
"logging.level.org.springframework.jdbc=INFO",
"server.port=0",
"app.rundown-api.sports-id="
        }
)
class RabbitEventPublisherIntegrationTest {

    @Container
    private static final RabbitMQContainer RABBIT_MQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management"));

    @Autowired
    private EventPublisher eventPublisher;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RabbitMQConfig rabbitMQConfig;

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry){
        registry.add("spring.rabbitmq.host", RABBIT_MQ::getHost);
        registry.add("spring.rabbitmq.port", RABBIT_MQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT_MQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT_MQ::getAdminPassword);
    }

    @Test
    void declaresBothTopologiesAndPublishesMatchesAsSharedMapperJson() throws Exception{
        Object matchesQueue = rabbitTemplate.execute(
                channel -> channel.queueDeclarePassive(rabbitMQConfig.getMatches().getQueue()));
        Object resultsQueue = rabbitTemplate.execute(
                channel -> channel.queueDeclarePassive(rabbitMQConfig.getResults().getQueue()));
        assertThat(matchesQueue).isNotNull();
        assertThat(resultsQueue).isNotNull();

        Event event = Event.builder().eventId("rabbit-event-1").sportId(1).build();
        eventPublisher.publish(EventChannel.MATCHES, event);

        Message message = rabbitTemplate.receive(rabbitMQConfig.getMatches().getQueue(), Duration.ofSeconds(5).toMillis());
        assertThat(message).isNotNull();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .isEqualTo(objectMapper.writeValueAsString(event));
    }
}
