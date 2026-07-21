package com.bixx.rapid_engine.producer;

import com.bixx.rapid_engine.config.RundownConfig;
import com.bixx.rapid_engine.messaging.EventChannel;
import com.bixx.rapid_engine.messaging.EventPublishException;
import com.bixx.rapid_engine.messaging.EventPublisher;
import com.bixx.rapid_engine.models.Event;
import com.bixx.rapid_engine.rabbitmq.RabbitEventPublisher;
import com.bixx.rapid_engine.rabbitmq.RabbitMQConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Testcontainers(disabledWithoutDocker = true)
class EventProducerCursorIntegrationTest {

    private static final String CURSOR_KEY = "rundown:delta_last_id:1";
    private static final String RESPONSE = """
            {"meta":{"deltaLastId":"new-cursor"},"events":[{"eventId":"event-1"},{"eventId":"event-2"}]}
            """;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    private static final RabbitMQContainer RABBIT_MQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management"));

    @Test
    void retriesThroughRabbitMqWithoutAdvancingCursorAfterPartialPublish() throws Exception {
        LettuceConnectionFactory redisConnectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        redisConnectionFactory.afterPropertiesSet();
        RedisTemplate<String, String> redisTemplate = redisTemplate(redisConnectionFactory);
        redisTemplate.opsForValue().set(CURSOR_KEY, "old-cursor");

        CachingConnectionFactory rabbitConnectionFactory = new CachingConnectionFactory(
                RABBIT_MQ.getHost(), RABBIT_MQ.getAmqpPort());
        rabbitConnectionFactory.setUsername(RABBIT_MQ.getAdminUsername());
        rabbitConnectionFactory.setPassword(RABBIT_MQ.getAdminPassword());
        rabbitConnectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        rabbitConnectionFactory.setPublisherReturns(true);

        String suffix = UUID.randomUUID().toString();
        RabbitMQConfig rabbitConfig = rabbitConfig(suffix);
        RabbitTemplate rabbitTemplate = new RabbitTemplate(rabbitConnectionFactory);
        rabbitTemplate.setMandatory(true);
        declareMatchesTopology(rabbitConnectionFactory, rabbitConfig);

        ObjectMapper objectMapper = new ObjectMapper();
        EventPublisher realPublisher = new RabbitEventPublisher(rabbitTemplate, rabbitConfig, objectMapper);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(manyTimes(), requestTo(org.hamcrest.Matchers.containsString("/sports/1/events/")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        AtomicInteger attempts = new AtomicInteger();
        EventPublisher failsOnSecondCall = (channel, event) -> {
            if (attempts.incrementAndGet() == 2) {
                throw new EventPublishException("controlled second publish failure");
            }
            realPublisher.publish(channel, event);
        };
        EventProducer producer = new EventProducer(
                rundownConfig(), restTemplate, failsOnSecondCall, objectMapper, redisTemplate);

        assertThat(producer.fetchEvents(1)).isZero();
        assertThat(redisTemplate.opsForValue().get(CURSOR_KEY)).isEqualTo("old-cursor");

        EventProducer retry = new EventProducer(
                rundownConfig(), restTemplate, realPublisher, objectMapper, redisTemplate);
        assertThat(retry.fetchEvents(1)).isEqualTo(4);
        assertThat(redisTemplate.opsForValue().get(CURSOR_KEY)).isEqualTo("new-cursor");

        List<String> eventIds = receiveEventIds(
                rabbitTemplate, rabbitConfig.getMatches().getQueue(), objectMapper, 5);
        assertThat(eventIds).contains("event-2");
        assertThat(eventIds.stream().filter("event-1"::equals).count()).isGreaterThan(1);

        server.verify();
        rabbitConnectionFactory.destroy();
        redisConnectionFactory.destroy();
    }

    private List<String> receiveEventIds(
            RabbitTemplate rabbitTemplate,
            String queue,
            ObjectMapper objectMapper,
            int expectedCount) throws Exception {
        List<String> eventIds = new ArrayList<>();
        for (int index = 0; index < expectedCount; index++) {
            Message message = rabbitTemplate.receive(queue, Duration.ofSeconds(10).toMillis());
            assertThat(message).as("RabbitMQ message %s", index + 1).isNotNull();
            JsonNode body = objectMapper.readTree(message.getBody());
            eventIds.add(body.get("eventId").asText());
        }
        return eventIds;
    }

    private void declareMatchesTopology(
            CachingConnectionFactory connectionFactory,
            RabbitMQConfig config) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        Queue queue = new Queue(config.getMatches().getQueue());
        TopicExchange exchange = new TopicExchange(config.getMatches().getExchange());
        admin.declareQueue(queue);
        admin.declareExchange(exchange);
        admin.declareBinding(BindingBuilder.bind(queue)
                .to(exchange)
                .with(config.getMatches().getRoutingKey()));
    }

    private RabbitMQConfig rabbitConfig(String suffix) {
        RabbitMQConfig.QueueConfig matches = new RabbitMQConfig.QueueConfig();
        matches.setExchange("cursor.matches.exchange." + suffix);
        matches.setQueue("cursor.matches.queue." + suffix);
        matches.setRoutingKey("matches");
        RabbitMQConfig config = new RabbitMQConfig();
        config.setMatches(matches);
        return config;
    }

    private RedisTemplate<String, String> redisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    private RundownConfig rundownConfig() {
        RundownConfig config = new RundownConfig();
        config.setHost("https://upstream.example");
        config.setKey("test-key");
        config.setAffiliateId("23");
        return config;
    }
}
