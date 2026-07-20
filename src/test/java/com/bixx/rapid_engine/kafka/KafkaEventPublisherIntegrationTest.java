package com.bixx.rapid_engine.kafka;

import com.bixx.rapid_engine.Main;
import com.bixx.rapid_engine.messaging.EventChannel;
import com.bixx.rapid_engine.messaging.EventPublisher;
import com.bixx.rapid_engine.models.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = Main.class,
        properties = {
                "app.messaging.broker=kafka",
                "app.kafka.matches-topic=integration.matches",
                "app.kafka.results-topic=integration.results",
                "app.kafka.partitions=1",
                "app.kafka.replication-factor=1",
                "spring.kafka.admin.auto-create=true",
                "spring.task.scheduling.enabled=false",
                "app.rundown-api.sports-id="
        }
)
class KafkaEventPublisherIntegrationTest {

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired
    private EventPublisher eventPublisher;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private KafkaConfig kafkaConfig;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry){
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Test
    void provisionsBothTopicsAndPublishesMatchesWithNullKeyAndSharedMapperJson() throws Exception{
        try(AdminClient adminClient = AdminClient.create(Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers()))) {
            Set<String> topicNames = adminClient.listTopics().names().get(Duration.ofSeconds(10).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            assertThat(topicNames)
                    .contains(kafkaConfig.getMatchesTopic(), kafkaConfig.getResultsTopic());
        }

        try(KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "integration-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()))) {
            consumer.subscribe(Set.of(kafkaConfig.getMatchesTopic()));
            consumer.poll(Duration.ofMillis(100));

            Event event = Event.builder().eventId("kafka-event-1").sportId(1).build();
            eventPublisher.publish(EventChannel.MATCHES, event);

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.count()).isEqualTo(1);
            var record = records.iterator().next();
            assertThat(record.key()).isNull();
            assertThat(record.value()).isEqualTo(objectMapper.writeValueAsString(event));
        }
    }
}
