# Configurable Kafka and RabbitMQ Broker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:
> subagent-driven-development (recommended) or superpowers:executing-plans to
> implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for
> tracking.

**Goal:** Publish sports events to either RabbitMQ or Kafka by changing the
required `app.messaging.broker` property, while preserving shared JSON payloads
and providing at-least-once delivery per fetched response batch.

**Architecture:** `EventProducer` publishes through a broker-neutral
`EventPublisher` port and advances its Redis cursor only after every event in
the fetched response is acknowledged. Conditional RabbitMQ and Kafka adapters
own their destination mapping, serialization, acknowledgement semantics, and
selected-broker topology provisioning.

**Tech Stack:** Java 21, Spring Boot 3.5.11, Spring AMQP, Spring Kafka, Spring
Data Redis, Jackson, JUnit 5, Mockito, Testcontainers, Maven, Docker Compose.

## Global Constraints

- `app.messaging.broker` is required and accepts only `rabbitmq` or `kafka`;
  missing or invalid values must fail startup.
- Exactly one `EventPublisher` implementation and its topology configuration may
  be active for an application instance.
- Kafka records use no key and send the shared Jackson snake-case JSON string as
  their value.
- RabbitMQ payload bodies must use that same JSON string encoded as UTF-8;
  broker metadata may differ.
- Publish failures must leave the Redis cursor unchanged; duplicate messages
  after partial batch failures are expected and documented.
- Preserve the existing poll structure. The cursor commit boundary is one
  successful fetched API response batch, not the entire yesterday-and-today
  `fetchEvents` invocation.
- RabbitMQ declares matches/results queue, exchange, and binding. Kafka declares
  matches/results topics.
- Local Compose starts Redis, RabbitMQ, and Kafka; switching broker requires
  only `MESSAGING_BROKER`, not Compose edits.
- Do not add consumers, broker fan-out, an outbox, or Redis/broker distributed
  transactions.

---

## File Structure

### New production files

| File                                                                       | Responsibility                                                                    |
|----------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `src/main/java/com/bixx/rapid_engine/messaging/EventChannel.java`          | Logical destinations: `MATCHES` and `RESULTS`.                                    |
| `src/main/java/com/bixx/rapid_engine/messaging/EventPublisher.java`        | Broker-neutral synchronous publishing port.                                       |
| `src/main/java/com/bixx/rapid_engine/messaging/EventPublishException.java` | Unchecked failure type that preserves broker/serialization causes.                |
| `src/main/java/com/bixx/rapid_engine/messaging/MessagingProperties.java`   | Validated selected-broker configuration.                                          |
| `src/main/java/com/bixx/rapid_engine/rabbitmq/RabbitEventPublisher.java`   | Rabbit channel mapping, exact JSON body, confirms, and returned-message handling. |
| `src/main/java/com/bixx/rapid_engine/kafka/KafkaConfig.java`               | Kafka topic and topic-default configuration.                                      |
| `src/main/java/com/bixx/rapid_engine/kafka/KafkaEventPublisher.java`       | Kafka channel mapping and acknowledged string sends.                              |
| `src/main/java/com/bixx/rapid_engine/kafka/KafkaTopicConfiguration.java`   | Selected-broker `NewTopic` bean declarations.                                     |

### Modified production/configuration files

| File                                                              | Change                                                                                         |
|-------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `pom.xml`                                                         | Add Spring Kafka, validation, and Testcontainers test dependencies.                            |
| `src/main/java/com/bixx/rapid_engine/producer/EventProducer.java` | Replace direct Rabbit calls with `EventPublisher`; write Redis cursor after all sends succeed. |
| `src/main/java/com/bixx/rapid_engine/rabbitmq/RabbitMQBeans.java` | Make Rabbit topology conditional and require mandatory returns.                                |
| `src/main/java/com/bixx/rapid_engine/RapidEngineApplication.java` | Make the configuration-properties scan comment broker-neutral.                                 |
| `src/main/resources/application-dev.yaml`                         | Add selected broker, Kafka producer/topic settings, and Rabbit confirmation settings.          |
| `src/main/resources/application-prod.yaml`                        | Add the equivalent production settings and environment bindings.                               |
| `.env-example`, `env.docker.example`                              | Add broker selector and Kafka environment values.                                              |
| `docker-compose.yml`                                              | Start a healthy single-node Kafka service beside RabbitMQ and Redis.                           |
| `docker-compose.prod.yml`                                         | Document/select externally managed Kafka without provisioning a local production broker.       |
| `README.md`, `DEPLOYMENT.md`                                      | Replace Rabbit-only guidance with selected-broker configuration and delivery contract.         |

### New test files

| File                                                                                      | Responsibility                                                                     |
|-------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| `src/test/java/com/bixx/rapid_engine/messaging/MessagingPropertiesTest.java`              | Validate broker binding rules.                                                     |
| `src/test/java/com/bixx/rapid_engine/messaging/BrokerSelectionWiringTest.java`            | Assert selected publisher/topology and invalid configuration failure.              |
| `src/test/java/com/bixx/rapid_engine/rabbitmq/RabbitEventPublisherTest.java`              | Verify Rabbit routing, JSON payload, confirm, return, and timeout behavior.        |
| `src/test/java/com/bixx/rapid_engine/kafka/KafkaConfigTest.java`                          | Verify Kafka configuration defaults.                                               |
| `src/test/java/com/bixx/rapid_engine/kafka/KafkaTopicConfigurationTest.java`              | Verify `NewTopic` definitions.                                                     |
| `src/test/java/com/bixx/rapid_engine/kafka/KafkaEventPublisherTest.java`                  | Verify no-key topic sends, JSON, failure, and timeout behavior.                    |
| `src/test/java/com/bixx/rapid_engine/integration/RabbitMqPublishingIntegrationTest.java`  | Verify live Rabbit delivery and raw JSON payload.                                  |
| `src/test/java/com/bixx/rapid_engine/integration/KafkaPublishingIntegrationTest.java`     | Verify live Kafka delivery, null key, and raw JSON payload.                        |
| `src/test/java/com/bixx/rapid_engine/integration/EventProducerCursorIntegrationTest.java` | Verify failed publication leaves a real Redis cursor unchanged and retry succeeds. |

---

### Task 1: Establish the broker-neutral publishing port and make cursor advancement safe

**Files:**

- Create: `src/main/java/com/bixx/rapid_engine/messaging/EventChannel.java`
- Create: `src/main/java/com/bixx/rapid_engine/messaging/EventPublisher.java`
- Create:
  `src/main/java/com/bixx/rapid_engine/messaging/EventPublishException.java`
- Modify:
  `src/main/java/com/bixx/rapid_engine/producer/EventProducer.java:3-150`
- Modify:
  `src/test/java/com/bixx/rapid_engine/producer/EventProducerTest.java:3-347`

**Interfaces:**

- Produces `EventPublisher.publish(EventChannel channel, Event event): void`.
- Produces `EventPublishException(String message, Throwable cause)` for adapters
  in Tasks 2 and 3.
- `EventProducer` consumes `EventPublisher` and invokes
  `publish(EventChannel.MATCHES, event)` once per outgoing event.

- [ ] **Step 1: Replace the direct Rabbit test fixture with a failing
  broker-neutral publication test**

Replace the Rabbit mocks/imports in `EventProducerTest` with `EventPublisher`,
construct the producer using it, and add this test:

```java

@Test
@DisplayName("fetchEventsForADate: publishes every event through the matches channel before saving its cursor")
void fetchEventsForADate_publishesEventsBeforeSavingCursor() throws Exception{
    when(valueOps.get(anyString())).thenReturn(null);
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{}"));
    when(objectMapper.readValue(eq("{}"), eq(RundownResponse.class)))
            .thenReturn(responseWithDelta("new-delta-42", 2));

    int result = invokeFetchForADate(1, LocalDate.now());

    assertThat(result).isEqualTo(2);
    InOrder inOrder = inOrder(eventPublisher, valueOps);
    inOrder.verify(eventPublisher).publish(eq(EventChannel.MATCHES), any(Event.class));
    inOrder.verify(eventPublisher).publish(eq(EventChannel.MATCHES), any(Event.class));
    inOrder.verify(valueOps).set(
            "rundown:delta_last_id:1", "new-delta-42", Duration.ofHours(24));
}

@Test
@DisplayName("fetchEventsForADate: leaves cursor unchanged and stops when publishing fails")
void fetchEventsForADate_doesNotAdvanceCursorAfterPublishFailure(){
    when(valueOps.get(anyString())).thenReturn(null);
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{}"));
    when(objectMapper.readValue(eq("{}"), eq(RundownResponse.class)))
            .thenReturn(responseWithDelta("new-delta-42", 2));
    doThrow(new EventPublishException("broker unavailable"))
            .when(eventPublisher).publish(eq(EventChannel.MATCHES), any(Event.class));

    assertThatThrownBy(() -> invokeFetchForADate(1, LocalDate.now()))
            .hasCauseInstanceOf(EventPublishException.class);

    verify(eventPublisher, times(1)).publish(eq(EventChannel.MATCHES), any(Event.class));
    verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
}
```

Import `org.mockito.InOrder`, `static org.mockito.Mockito.inOrder`,
`static org.mockito.Mockito.doThrow`,
`static org.assertj.core.api.Assertions.assertThatThrownBy`, `EventChannel`,
`EventPublisher`, and `EventPublishException`. Replace every existing
verification of `rabbitTemplate.convertAndSend(...)` with
`eventPublisher.publish(...)` equivalents.

- [ ] **Step 2: Run the focused test to verify it fails because the abstraction
  does not exist**

Run:

```bash
./mvnw -Dtest=EventProducerTest test
```

Expected: compilation failure for missing `com.bixx.rapid_engine.messaging`
types and the changed `EventProducer` constructor.

- [ ] **Step 3: Create the port, channel, and exception types**

Create `EventChannel.java`:

```java
package com.bixx.rapid_engine.messaging;

public enum EventChannel {
    MATCHES,
    RESULTS
}
```

Create `EventPublisher.java`:

```java
package com.bixx.rapid_engine.messaging;

import com.bixx.rapid_engine.models.Event;

public interface EventPublisher {
    void publish(EventChannel channel, Event event);
}
```

Create `EventPublishException.java`:

```java
package com.bixx.rapid_engine.messaging;

public class EventPublishException extends RuntimeException {
    public EventPublishException(String message){
        super(message);
    }

    public EventPublishException(String message, Throwable cause){
        super(message, cause);
    }
}
```

- [ ] **Step 4: Refactor `EventProducer` to publish first and persist cursor
  last**

In `EventProducer.java`:

1. Remove imports/fields for `RabbitMQConfig` and `RabbitTemplate`.
2. Add imports for `EventChannel` and `EventPublisher`.
3. Keep `ObjectMapper` only for deserializing the Rundown response.
4. Replace the two removed constructor fields with:

```java
private final EventPublisher eventPublisher;
```

5. Replace lines 126-147 with this ordered batch logic:

```java
Meta meta = rundownResponse.getMeta();
String newDeltaLastId = meta == null ? null : meta.getDeltaLastId();

for (Event event : events) {
    eventPublisher.publish(EventChannel.MATCHES, event);
}

if (newDeltaLastId != null) {
    stringRedisTemplate.opsForValue().set(redisKey, newDeltaLastId, Duration.ofHours(24));
    log.info("Sport Id {} - saved new delta {}", sportsId, newDeltaLastId);
}
```

Do not catch publish exceptions in `fetchEventsForADate`; they must abort the
response batch before the cursor write. Retain the existing public `fetchEvents`
catch-and-return-zero scheduler boundary.

- [ ] **Step 5: Run the producer tests to verify cursor ordering and failure
  behavior pass**

Run:

```bash
./mvnw -Dtest=EventProducerTest test
```

Expected: PASS. Existing URL/header, non-200, null/empty event, Redis key, and
TTL tests remain green after their Rabbit-specific verifications are updated.

- [ ] **Step 6: Commit the port and cursor-safe producer refactor**

```bash
git add src/main/java/com/bixx/rapid_engine/messaging src/main/java/com/bixx/rapid_engine/producer/EventProducer.java src/test/java/com/bixx/rapid_engine/producer/EventProducerTest.java
git commit -m "refactor: add broker-neutral event publisher"
```

### Task 2: Add strict broker selection and conditionally activate RabbitMQ topology

**Files:**

- Modify: `pom.xml:32-87`
- Create:
  `src/main/java/com/bixx/rapid_engine/messaging/MessagingProperties.java`
- Modify:
  `src/main/java/com/bixx/rapid_engine/rabbitmq/RabbitMQBeans.java:16-83`
- Modify: `src/main/java/com/bixx/rapid_engine/RapidEngineApplication.java:9-13`
- Create:
  `src/test/java/com/bixx/rapid_engine/messaging/MessagingPropertiesTest.java`
- Modify: `src/test/java/com/bixx/rapid_engine/rabbitmq/RabbitMQBeansTest.java`

**Interfaces:**

- Produces `MessagingProperties.getBroker(): MessagingProperties.Broker` where
  enum values are `RABBITMQ` and `KAFKA`.
- Rabbit beans activate only for `app.messaging.broker=rabbitmq`.
- Task 4 uses this selection to assert one publisher/topology path.

- [ ] **Step 1: Write failing configuration and Rabbit-template tests**

Create `MessagingPropertiesTest.java` using `ApplicationContextRunner` and an
explicit properties-registration test configuration:

```java

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MessagingProperties.class)
static class PropertiesConfiguration {
}

class MessagingPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsLowercaseKafkaToBrokerEnum(){
        contextRunner.withPropertyValues("app.messaging.broker=kafka")
                .run(context -> assertThat(context.getBean(MessagingProperties.class).getBroker())
                        .isEqualTo(MessagingProperties.Broker.KAFKA));
    }

    @Test
    void failsWhenBrokerIsMissing(){
        contextRunner.run(context -> assertThat(context.getStartupFailure())
                .hasMessageContaining("app.messaging.broker"));
    }
}
```

Add this assertion to `RabbitMQBeansTest` after creating the template:

```java
assertThat(template.isMandatory()).isTrue();
```

- [ ] **Step 2: Run the focused tests to verify they fail**

Run:

```bash
./mvnw -Dtest=MessagingPropertiesTest,RabbitMQBeansTest test
```

Expected: compilation failure because `MessagingProperties` does not exist; the
Rabbit mandatory assertion fails after the class compiles.

- [ ] **Step 3: Add validation support and broker properties**

Add this dependency to `pom.xml` after `spring-boot-starter-web`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Create `MessagingProperties.java`:

```java
package com.bixx.rapid_engine.messaging;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.messaging")
public class MessagingProperties {
    @NotNull
    private Broker broker;

    public enum Broker {
        RABBITMQ,
        KAFKA
    }
}
```

Keep `@ConfigurationPropertiesScan` as the registration mechanism. Lowercase
property values bind through Spring Boot relaxed enum conversion.

- [ ] **Step 4: Gate the Rabbit configuration and enable mandatory returns**

Add this annotation directly above `@RequiredArgsConstructor` in
`RabbitMQBeans.java`:

```java
@ConditionalOnProperty(
    prefix = "app.messaging",
    name = "broker",
    havingValue = "rabbitmq"
)
```

Import `org.springframework.boot.autoconfigure.condition.ConditionalOnProperty`.

In `rabbitTemplate(...)`, keep the shared `Jackson2JsonMessageConverter` for
general AMQP infrastructure and add:

```java
RabbitTemplate template = new RabbitTemplate(factory);
template.setMessageConverter(messageConverter);
template.setMandatory(true);
return template;
```

Update the scan comment in `RapidEngineApplication.java` to:

```java
@ConfigurationPropertiesScan // Scans application configuration property classes.
```

- [ ] **Step 5: Run tests and inspect dependency resolution**

Run:

```bash
./mvnw -Dtest=MessagingPropertiesTest,RabbitMQBeansTest test
./mvnw dependency:tree -Dincludes=org.springframework.boot:spring-boot-starter-validation
```

Expected: focused tests PASS and the dependency tree includes
`spring-boot-starter-validation`.

- [ ] **Step 6: Commit strict selection support**

```bash
git add pom.xml src/main/java/com/bixx/rapid_engine/messaging/MessagingProperties.java src/main/java/com/bixx/rapid_engine/rabbitmq/RabbitMQBeans.java src/main/java/com/bixx/rapid_engine/Main.java src/test/java/com/bixx/rapid_engine/messaging/MessagingPropertiesTest.java src/test/java/com/bixx/rapid_engine/rabbitmq/RabbitMQBeansTest.java
git commit -m "feat: validate selected messaging broker"
```

### Task 3: Implement the RabbitMQ publishing adapter with confirmed routing

**Files:**

- Create:
  `src/main/java/com/bixx/rapid_engine/rabbitmq/RabbitEventPublisher.java`
- Create:
  `src/test/java/com/bixx/rapid_engine/rabbitmq/RabbitEventPublisherTest.java`

**Interfaces:**

- Consumes `EventPublisher`, `EventChannel`, `EventPublishException`,
  `RabbitMQConfig`, shared `ObjectMapper`, and `RabbitTemplate`.
- Produces a selected `EventPublisher` bean for `app.messaging.broker=rabbitmq`.
- Sends `Message` bodies made from `objectMapper.writeValueAsBytes(event)` and
  waits for `CorrelationData` confirmation.

- [ ] **Step 1: Write failing Rabbit adapter tests for mapping and exact
  payload**

Create `RabbitEventPublisherTest.java` with Mockito mocks for `RabbitTemplate`,
`RabbitMQConfig`, and an `ObjectMapper` from
`new JacksonConfig().objectMapper()`. Configure `matches` and `results` queue
configs in `@BeforeEach`.

Add this test, capturing the outbound AMQP message:

```java

@Test
void publishesMatchesAsSharedJsonToConfiguredExchangeAndRoutingKey() throws Exception{
    Event event = Event.builder().eventId("event-1").build();
    arrangeAcknowledgedConfirm();

    publisher.publish(EventChannel.MATCHES, event);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(rabbitTemplate).convertAndSend(
            eq("matches.exchange"),
            eq("matches.routing.key"),
            messageCaptor.capture(),
            any(CorrelationData.class));
    assertThat(new String(messageCaptor.getValue().getBody(), StandardCharsets.UTF_8))
            .isEqualTo(objectMapper.writeValueAsString(event));
}

@Test
void publishesResultsToResultsDestination(){
    arrangeAcknowledgedConfirm();

    publisher.publish(EventChannel.RESULTS, Event.builder().eventId("result-1").build());

    verify(rabbitTemplate).convertAndSend(
            eq("results.exchange"), eq("results.routing.key"), any(Message.class), any(CorrelationData.class));
}
```

Use a helper that intercepts the `CorrelationData` argument and completes its
future:

```java
private void arrangeAcknowledgedConfirm() {
    doAnswer(invocation -> {
        CorrelationData correlationData = invocation.getArgument(3);
        correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
        return null;
    }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
}
```

- [ ] **Step 2: Add failing negative-confirm, returned-message, timeout, and
  serialization tests**

Add each case with these assertions:

```java

@Test
void throwsWhenBrokerNegativelyAcknowledgesMessage(){
    arrangeConfirm(false, "broker nacked publish", null);

    assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
            .isInstanceOf(EventPublishException.class)
            .hasMessageContaining("negatively acknowledged");
}

@Test
void throwsWhenBrokerReturnsUnroutableMessage(){
    Message returned = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
    arrangeConfirm(true, null, new ReturnedMessage(returned, 312, "NO_ROUTE", "matches.exchange", "missing.key"));

    assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
            .isInstanceOf(EventPublishException.class)
            .hasMessageContaining("returned");
}

@Test
void throwsWhenConfirmDoesNotArriveBeforeTimeout(){
    publisher = new RabbitEventPublisher(rabbitTemplate, rabbitMQConfig, objectMapper, Duration.ofMillis(1));

    assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
            .isInstanceOf(EventPublishException.class)
            .hasMessageContaining("timed out");
}

@Test
void throwsBeforeSendingWhenSerializationFails() throws Exception{
    ObjectMapper failingMapper = mock(ObjectMapper.class);
    when(failingMapper.writeValueAsBytes(any(Event.class))).thenThrow(new JsonProcessingException("bad JSON") {
    });
    publisher = new RabbitEventPublisher(rabbitTemplate, rabbitMQConfig, failingMapper, Duration.ofSeconds(5));

    assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
            .isInstanceOf(EventPublishException.class)
            .hasMessageContaining("serialize");
    verifyNoInteractions(rabbitTemplate);
}
```

- [ ] **Step 3: Run the adapter tests to verify they fail**

Run:

```bash
./mvnw -Dtest=RabbitEventPublisherTest test
```

Expected: compilation failure because `RabbitEventPublisher` does not exist.

- [ ] **Step 4: Implement synchronous confirmed Rabbit publishing**

Create `RabbitEventPublisher.java`:

```java
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
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "rabbitmq")
public class RabbitEventPublisher implements EventPublisher {
    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;
    private final ObjectMapper objectMapper;
    private final Duration confirmTimeout;

    public RabbitEventPublisher(RabbitTemplate rabbitTemplate, RabbitMQConfig rabbitMQConfig, ObjectMapper objectMapper){
        this(rabbitTemplate, rabbitMQConfig, objectMapper, CONFIRM_TIMEOUT);
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
        RabbitMQConfig.QueueConfig destination = destinationFor(channel);
        Message message = toMessage(event);
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());

        rabbitTemplate.convertAndSend(destination.getExchange(), destination.getRoutingKey(), message, correlationData);

        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if(!confirm.isAck()) {
                throw new EventPublishException("RabbitMQ publish was negatively acknowledged: " + confirm.getReason());
            }
            if(correlationData.getReturned() != null) {
                throw new EventPublishException("RabbitMQ message was returned as unroutable");
            }
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Interrupted while waiting for RabbitMQ publish confirmation", exception);
        } catch(ExecutionException exception) {
            throw new EventPublishException("RabbitMQ publish confirmation failed", exception.getCause());
        } catch(TimeoutException exception) {
            throw new EventPublishException("RabbitMQ publish confirmation timed out", exception);
        }
    }

    private RabbitMQConfig.QueueConfig destinationFor(EventChannel channel){
        return switch(channel) {
            case MATCHES -> rabbitMQConfig.getMatches();
            case RESULTS -> rabbitMQConfig.getResults();
        };
    }

    private Message toMessage(Event event){
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            return new Message(objectMapper.writeValueAsBytes(event), properties);
        } catch(JsonProcessingException exception) {
            throw new EventPublishException("Could not serialize event for RabbitMQ", exception);
        }
    }
}
```

Keep the three-argument constructor public so Spring injects it. Keep the
four-argument timeout constructor package-private for unit tests; Spring does
not select it because it is not public.

- [ ] **Step 5: Run Rabbit unit tests and the producer regression test**

Run:

```bash
./mvnw -Dtest=RabbitEventPublisherTest,RabbitMQBeansTest,EventProducerTest test
```

Expected: PASS. If an AMQP API differs in the resolved Spring AMQP version,
inspect it with `javap` or IDE documentation and preserve the behavior: explicit
JSON bytes, correlated confirm wait, nack/return/timeout errors, interrupt
restoration.

- [ ] **Step 6: Commit the Rabbit adapter**

```bash
git add src/main/java/com/bixx/rapid_engine/rabbitmq/RabbitEventPublisher.java src/test/java/com/bixx/rapid_engine/rabbitmq/RabbitEventPublisherTest.java
git commit -m "feat: publish events through confirmed rabbitmq adapter"
```

### Task 4: Implement Kafka topics and acknowledged Kafka publishing

**Files:**

- Modify: `pom.xml:32-87`
- Create: `src/main/java/com/bixx/rapid_engine/kafka/KafkaConfig.java`
- Create:
  `src/main/java/com/bixx/rapid_engine/kafka/KafkaTopicConfiguration.java`
- Create: `src/main/java/com/bixx/rapid_engine/kafka/KafkaEventPublisher.java`
- Create: `src/test/java/com/bixx/rapid_engine/kafka/KafkaConfigTest.java`
- Create:
  `src/test/java/com/bixx/rapid_engine/kafka/KafkaTopicConfigurationTest.java`
- Create:
  `src/test/java/com/bixx/rapid_engine/kafka/KafkaEventPublisherTest.java`

**Interfaces:**

- Consumes `EventPublisher.publish(EventChannel, Event)` from Task 1.
- Produces `KafkaConfig.getMatches().getTopic()` and `getResults().getTopic()`.
- Produces `NewTopic matchesTopic(KafkaConfig)` and
  `NewTopic resultsTopic(KafkaConfig)` only in Kafka mode.

- [ ] **Step 1: Add Spring Kafka and write failing configuration/topic tests**

Add this managed dependency to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

Create `KafkaConfigTest.java`:

```java

@Test
void topicDefaultsUseOnePartitionAndReplica(){
    KafkaConfig.TopicDefaults defaults = new KafkaConfig.TopicDefaults();

    assertThat(defaults.getPartitions()).isEqualTo(1);
    assertThat(defaults.getReplicationFactor()).isEqualTo((short) 1);
}
```

Create `KafkaTopicConfigurationTest.java`:

```java

@Test
void createsMatchesTopicFromConfiguredNameAndDefaults(){
    KafkaConfig config = configuredKafkaConfig();

    NewTopic topic = new KafkaTopicConfiguration(config).matchesTopic();

    assertThat(topic.name()).isEqualTo("matches.events");
    assertThat(topic.numPartitions()).isEqualTo(1);
    assertThat(topic.replicationFactor()).isEqualTo((short) 1);
}
```

- [ ] **Step 2: Run tests to verify missing Kafka implementation fails**

Run:

```bash
./mvnw -Dtest=KafkaConfigTest,KafkaTopicConfigurationTest test
```

Expected: compilation failure because `KafkaConfig` and
`KafkaTopicConfiguration` do not exist.

- [ ] **Step 3: Create Kafka configuration and selected topic declarations**

Create `KafkaConfig.java`:

```java
package com.bixx.rapid_engine.kafka;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaConfig {
    private TopicConfig matches;
    private TopicConfig results;
    private TopicDefaults topicDefaults = new TopicDefaults();

    @Data
    public static class TopicConfig {
        private String topic;
    }

    @Data
    public static class TopicDefaults {
        private int partitions = 1;
        private short replicationFactor = 1;
    }
}
```

Create `KafkaTopicConfiguration.java`:

```java
package com.bixx.rapid_engine.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "kafka")
public class KafkaTopicConfiguration {
    private final KafkaConfig kafkaConfig;

    @Bean
    public NewTopic matchesTopic(){
        return topic(kafkaConfig.getMatches().getTopic());
    }

    @Bean
    public NewTopic resultsTopic(){
        return topic(kafkaConfig.getResults().getTopic());
    }

    private NewTopic topic(String name){
        KafkaConfig.TopicDefaults defaults = kafkaConfig.getTopicDefaults();
        return new NewTopic(name, defaults.getPartitions(), defaults.getReplicationFactor());
    }
}
```

- [ ] **Step 4: Write failing Kafka publisher tests**

Create `KafkaEventPublisherTest.java` using a mocked
`KafkaTemplate<String, String>` and shared `ObjectMapper`.

```java

@Test
void publishesMatchesJsonWithoutKey() throws Exception{
    Event event = Event.builder().eventId("event-1").build();
    CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
    when(kafkaTemplate.send(anyString(), anyString())).thenReturn(future);

    publisher.publish(EventChannel.MATCHES, event);

    verify(kafkaTemplate).send("matches.events", objectMapper.writeValueAsString(event));
    verify(kafkaTemplate, never()).send(anyString(), isNull(), anyString());
}

@Test
void throwsWhenKafkaAcknowledgementFails(){
    CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
    future.completeExceptionally(new RuntimeException("broker unavailable"));
    when(kafkaTemplate.send(anyString(), anyString())).thenReturn(future);

    assertThatThrownBy(() -> publisher.publish(EventChannel.MATCHES, Event.builder().build()))
            .isInstanceOf(EventPublishException.class)
            .hasMessageContaining("Kafka publish failed");
}
```

Also add a `RESULTS` mapping test, a timeout test using a non-completed future
and a 1ms injected timeout, and a failing-`ObjectMapper` serialization test that
verifies no `KafkaTemplate` interaction.

- [ ] **Step 5: Run Kafka publisher tests to verify they fail**

Run:

```bash
./mvnw -Dtest=KafkaEventPublisherTest test
```

Expected: compilation failure because `KafkaEventPublisher` does not exist.

- [ ] **Step 6: Implement synchronous no-key Kafka publishing**

Create `KafkaEventPublisher.java`:

```java
package com.bixx.rapid_engine.kafka;

import com.bixx.rapid_engine.messaging.EventChannel;
import com.bixx.rapid_engine.messaging.EventPublishException;
import com.bixx.rapid_engine.messaging.EventPublisher;
import com.bixx.rapid_engine.models.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, KafkaConfig kafkaConfig, ObjectMapper objectMapper){
        this(kafkaTemplate, kafkaConfig, objectMapper, SEND_TIMEOUT);
    }

    KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, KafkaConfig kafkaConfig, ObjectMapper objectMapper, Duration sendTimeout){
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaConfig = kafkaConfig;
        this.objectMapper = objectMapper;
        this.sendTimeout = sendTimeout;
    }

    @Override
    public void publish(EventChannel channel, Event event){
        String payload = serialize(event);
        try {
            kafkaTemplate.send(topicFor(channel), payload)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Interrupted while waiting for Kafka acknowledgement", exception);
        } catch(ExecutionException exception) {
            throw new EventPublishException("Kafka publish failed", exception.getCause());
        } catch(TimeoutException exception) {
            throw new EventPublishException("Kafka publish acknowledgement timed out", exception);
        }
    }

    private String topicFor(EventChannel channel){
        return switch(channel) {
            case MATCHES -> kafkaConfig.getMatches().getTopic();
            case RESULTS -> kafkaConfig.getResults().getTopic();
        };
    }

    private String serialize(Event event){
        try {
            return objectMapper.writeValueAsString(event);
        } catch(JsonProcessingException exception) {
            throw new EventPublishException("Could not serialize event for Kafka", exception);
        }
    }
}
```

The two-argument `send(topic, payload)` overload deliberately writes a null
Kafka key.

- [ ] **Step 7: Run all Kafka unit tests**

Run:

```bash
./mvnw -Dtest=KafkaConfigTest,KafkaTopicConfigurationTest,KafkaEventPublisherTest test
```

Expected: PASS.

- [ ] **Step 8: Commit Kafka adapter and topic support**

```bash
git add pom.xml src/main/java/com/bixx/rapid_engine/kafka src/test/java/com/bixx/rapid_engine/kafka
git commit -m "feat: add kafka event publisher"
```

### Task 5: Prove conditional broker wiring and configure both broker paths

**Files:**

- Create:
  `src/test/java/com/bixx/rapid_engine/messaging/BrokerSelectionWiringTest.java`
- Modify: `src/main/resources/application-dev.yaml:1-49`
- Modify: `src/main/resources/application-prod.yaml:1-65`

**Interfaces:**

- Consumes `MessagingProperties`, `RabbitEventPublisher`, `KafkaEventPublisher`,
  Rabbit topology beans, and Kafka `NewTopic` beans.
- Produces profile configuration where `MESSAGING_BROKER` selects one adapter
  and native broker properties configure only its active client.

- [ ] **Step 1: Write failing selected-broker context tests**

Create `BrokerSelectionWiringTest.java` using `ApplicationContextRunner` with
only the configuration classes needed for messaging, plus mock infrastructure
beans. Test both selected values:

```java

@Test
void rabbitmqSelectionCreatesOnlyRabbitPublisherAndTopology(){
    contextRunner.withPropertyValues(
                    "app.messaging.broker=rabbitmq",
                    "app.rabbitmq.matches.exchange=matches.exchange",
                    "app.rabbitmq.matches.queue=matches.queue",
                    "app.rabbitmq.matches.routing-key=matches.routing.key",
                    "app.rabbitmq.results.exchange=results.exchange",
                    "app.rabbitmq.results.queue=results.queue",
                    "app.rabbitmq.results.routing-key=results.routing.key")
            .run(context -> {
                assertThat(context).hasSingleBean(EventPublisher.class);
                assertThat(context).hasSingleBean(RabbitEventPublisher.class);
                assertThat(context).doesNotHaveBean(KafkaEventPublisher.class);
                assertThat(context).hasBean("matchesQueue");
                assertThat(context).doesNotHaveBean("matchesTopic");
            });
}

@Test
void kafkaSelectionCreatesOnlyKafkaPublisherAndTopics(){
    contextRunner.withPropertyValues(
                    "app.messaging.broker=kafka",
                    "app.kafka.matches.topic=matches.events",
                    "app.kafka.results.topic=results.events")
            .run(context -> {
                assertThat(context).hasSingleBean(EventPublisher.class);
                assertThat(context).hasSingleBean(KafkaEventPublisher.class);
                assertThat(context).doesNotHaveBean(RabbitEventPublisher.class);
                assertThat(context).hasBean("matchesTopic");
                assertThat(context).doesNotHaveBean("matchesQueue");
            });
}
```

Also test missing broker and `app.messaging.broker=unknown` startup failures,
asserting the failure message names `app.messaging.broker`.

- [ ] **Step 2: Run wiring tests to verify they fail before profile
  configuration is supplied**

Run:

```bash
./mvnw -Dtest=BrokerSelectionWiringTest test
```

Expected: FAIL until the runner includes the required property registration and
required test infrastructure beans. Adjust only the test runner setup—not
production fallback behavior—until it tests exact selection conditions.

- [ ] **Step 3: Add complete dev profile configuration**

In `application-dev.yaml`, under `spring.rabbitmq`, add:

```yaml
    publisher-confirm-type: correlated
    publisher-returns: true
```

Under `spring`, add:

```yaml
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      acks: all
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      properties:
        enable.idempotence: true
```

Replace the current `app:` section with this exact structure, retaining
`rundown-api` values beneath it:

```yaml
app:
  messaging:
    broker: ${MESSAGING_BROKER:rabbitmq}

  rabbitmq:
    matches:
      exchange: ${RABBITMQ_MATCHES_EXCHANGE:matches.exchange}
      queue: ${RABBITMQ_MATCHES_QUEUE:matches.queue}
      routing-key: ${RABBITMQ_MATCHES_ROUTING_KEY:matches.routing.key}
    results:
      exchange: ${RABBITMQ_RESULTS_EXCHANGE:results.exchange}
      queue: ${RABBITMQ_RESULTS_QUEUE:results.queue}
      routing-key: ${RABBITMQ_RESULTS_ROUTING_KEY:results.routing.key}

  kafka:
    matches:
      topic: ${KAFKA_MATCHES_TOPIC:matches.events}
    results:
      topic: ${KAFKA_RESULTS_TOPIC:results.events}
    topic-defaults:
      partitions: ${KAFKA_TOPIC_PARTITIONS:1}
      replication-factor: ${KAFKA_TOPIC_REPLICATION_FACTOR:1}
```

- [ ] **Step 4: Add equivalent production profile settings**

In `application-prod.yaml`, add Rabbit `publisher-confirm-type: correlated` and
`publisher-returns: true` alongside its existing connection settings. Add Kafka
bootstrap and producer values:

```yaml
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      acks: all
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      properties:
        enable.idempotence: true
```

Add the same `app.messaging`, `app.rabbitmq`, and `app.kafka` hierarchy as
development, but use production environment placeholders without localhost
fallbacks where secrets or remote endpoints are required.

- [ ] **Step 5: Run wiring and full unit tests**

Run:

```bash
./mvnw -Dtest=BrokerSelectionWiringTest,MessagingPropertiesTest,RabbitEventPublisherTest,KafkaEventPublisherTest,EventProducerTest test
./mvnw test
```

Expected: PASS. The context test must prove only one `EventPublisher` exists for
each valid selected value.

- [ ] **Step 6: Commit conditional wiring and profile configuration**

```bash
git add src/test/java/com/bixx/rapid_engine/messaging/BrokerSelectionWiringTest.java src/main/resources/application-dev.yaml src/main/resources/application-prod.yaml
git commit -m "feat: select messaging broker through configuration"
```

### Task 6: Add local Docker and environment support for both brokers

**Files:**

- Modify: `.env-example:1-33`
- Modify: `env.docker.example:1-46`
- Modify: `docker-compose.yml:1-62`
- Modify: `docker-compose.prod.yml`

**Interfaces:**

- `MESSAGING_BROKER` selects `rabbitmq` or `kafka`.
- The application container resolves Kafka at `kafka:9092`; host tools resolve
  Kafka at `localhost:9092`.
- RabbitMQ and Kafka both become healthy before the local application starts.

- [ ] **Step 1: Add failing Compose validation as a pre-change baseline**

Copy `env.docker.example` to `.env.docker` only if it does not already exist and
is safe to replace with example values. Then run:

```bash
docker compose --env-file .env.docker config
```

Expected before implementation: valid current Compose output without a `kafka`
service. Record this only as a baseline; do not commit `.env.docker` if it is
ignored or contains secrets.

- [ ] **Step 2: Add broker and Kafka values to both environment templates**

Add to `.env-example`:

```dotenv
MESSAGING_BROKER=rabbitmq

KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_MATCHES_TOPIC=matches.events
KAFKA_RESULTS_TOPIC=results.events
KAFKA_TOPIC_PARTITIONS=1
KAFKA_TOPIC_REPLICATION_FACTOR=1
```

Add to `env.docker.example`, using the Compose hostname:

```dotenv
MESSAGING_BROKER=rabbitmq

KAFKA_BOOTSTRAP_SERVERS=kafka:9092
KAFKA_MATCHES_TOPIC=matches.events
KAFKA_RESULTS_TOPIC=results.events
KAFKA_TOPIC_PARTITIONS=1
KAFKA_TOPIC_REPLICATION_FACTOR=1
```

Retain existing RabbitMQ and Redis values so both connection paths can be
configured from one environment file.

- [ ] **Step 3: Add a dual-listener single-node Kafka Compose service**

Add this service to `docker-compose.yml` after `rabbitmq`:

```yaml
  kafka:
    image: apache/kafka:3.9.0
    container_name: kafka
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093,PLAINTEXT_HOST://:29092
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
    ports:
      - "9092:29092"
    healthcheck:
      test: ["CMD-SHELL", "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list"]
      interval: 10s
      timeout: 10s
      retries: 12
    networks:
      - rapid-engine-net
```

Verify the exact image-supported environment variable names against the image
documentation before finalizing; Apache Kafka images may change KRaft
environment requirements. Keep the required behavior: internal advertised
endpoint `kafka:9092`, host endpoint `localhost:9092`, single-node replication
values, and a command-based healthcheck.

Add this dependency to `rapid-engine.depends_on`:

```yaml
      kafka:
        condition: service_healthy
```

- [ ] **Step 4: Update production Compose expectations**

Do not add a Kafka service to `docker-compose.prod.yml`. Add `MESSAGING_BROKER`
and `KAFKA_BOOTSTRAP_SERVERS` to the application environment passthrough if that
file explicitly lists app variables. Add comments stating that production uses
externally managed RabbitMQ or Kafka and the selected broker must be reachable
at application startup.

- [ ] **Step 5: Validate Compose syntax and start both brokers**

Run:

```bash
docker compose --env-file .env.docker config
docker compose --env-file .env.docker up -d rabbitmq kafka redis
docker compose ps
```

Expected: `rabbitmq`, `kafka`, and `redis` report healthy. If Kafka fails health
checks, inspect `docker compose logs kafka`, fix listener/configuration values,
rerun, and do not proceed until the broker is healthy.

- [ ] **Step 6: Commit local broker environment and Compose support**

```bash
git add .env-example env.docker.example docker-compose.yml docker-compose.prod.yml
git commit -m "feat: run kafka alongside rabbitmq locally"
```

### Task 7: Prove live broker behavior and cursor retry safety with Testcontainers

**Files:**

- Modify: `pom.xml:32-87`
- Create:
  `src/test/java/com/bixx/rapid_engine/integration/RabbitMqPublishingIntegrationTest.java`
- Create:
  `src/test/java/com/bixx/rapid_engine/integration/KafkaPublishingIntegrationTest.java`
- Create:
  `src/test/java/com/bixx/rapid_engine/integration/EventProducerCursorIntegrationTest.java`

**Interfaces:**

- Verifies `EventPublisher` sends exact shared JSON to a real selected broker.
- Verifies Kafka records have a null key.
- Verifies real Redis cursor writes occur only after a successful response
  batch.

- [ ] **Step 1: Add Testcontainers dependencies and write the Rabbit integration
  test**

Add test-scoped dependencies managed by the Spring Boot parent:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>rabbitmq</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
<!-- Redis integration uses org.testcontainers.containers.GenericContainer with redis:7-alpine;
     no Redis-specific Testcontainers artifact is required. -->
```

Create `RabbitMqPublishingIntegrationTest` with a `RabbitMQContainer`,
`@DynamicPropertySource`, and
`@SpringBootTest(properties = "app.messaging.broker=rabbitmq")`. Supply the
Rabbit matches/results configuration properties. Autowire `EventPublisher`,
`ObjectMapper`, and `RabbitTemplate` or a plain AMQP consumer client.

Test behavior:

```java
@Test
void publishesSharedJsonToConfiguredMatchesQueue() throws Exception {
    Event event = Event.builder().eventId("rabbit-event").build();

    eventPublisher.publish(EventChannel.MATCHES, event);

    Message message = rabbitTemplate.receive("matches.queue", 5_000);
    assertThat(message).isNotNull();
    assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
        .isEqualTo(objectMapper.writeValueAsString(event));
}
```

- [ ] **Step 2: Run Rabbit integration test to verify it fails before the test
  setup is complete**

Run:

```bash
./mvnw -Dtest=RabbitMqPublishingIntegrationTest test
```

Expected initially: test context or container configuration failure until all
dynamic host/port/credentials and selected-broker properties are supplied.
Finish the setup until the test reaches the expected live broker assertion.

- [ ] **Step 3: Write Kafka integration test with null-key assertion**

Create `KafkaPublishingIntegrationTest` with `KafkaContainer` and dynamic
`spring.kafka.bootstrap-servers`. Start an isolated consumer with
`StringDeserializer`, subscribe to `matches.events`, publish through
`EventPublisher`, then poll.

```java
@Test
void publishesSharedJsonWithoutKafkaKey() throws Exception {
    Event event = Event.builder().eventId("kafka-event").build();

    eventPublisher.publish(EventChannel.MATCHES, event);

    ConsumerRecord<String, String> record = pollSingleRecord("matches.events");
    assertThat(record.key()).isNull();
    assertThat(record.value()).isEqualTo(objectMapper.writeValueAsString(event));
}
```

Set `app.messaging.broker=kafka`, matches/results topics, and one
partition/replica. Assert the `NewTopic` declarations provision both topics
before publishing.

- [ ] **Step 4: Run both broker integration tests**

Run:

```bash
./mvnw -Dtest=RabbitMqPublishingIntegrationTest,KafkaPublishingIntegrationTest test
```

Expected: PASS when Docker is available. If Docker is unavailable in CI,
configure the Maven build to skip only `*IntegrationTest` under an explicit
profile and document the exact profile; do not weaken unit coverage.

- [ ] **Step 5: Add an end-to-end Redis cursor failure/retry test**

Create `EventProducerCursorIntegrationTest` using a Redis Testcontainer and one
broker Testcontainer. Use `MockRestServiceServer` to return a two-event Rundown
API response and invoke the package-visible extraction of `fetchEventsForADate`;
if the method remains private, add a package-private `fetchEventsForDate`
wrapper solely for testability and call it from the private flow.

Test sequence:

1. Seed Redis key `rundown:delta_last_id:1` with `old-delta`.
2. Configure a spy `EventPublisher` that delegates the first publish but throws
   `EventPublishException` on the second.
3. Invoke the fetched response batch and assert the Redis key is still
   `old-delta`.
4. Reconfigure the publisher to succeed, invoke the same response again, and
   assert Redis now contains `new-delta`.
5. Assert the first event was attempted twice and the second at least once,
   proving at-least-once duplicate behavior.

Use an explicit JSON response body matching `RundownResponse` and the project
mapper’s snake-case fields. Do not rely on a mocked Redis template for this
test.

- [ ] **Step 6: Run all integration and full Maven suites**

Run:

```bash
./mvnw -Dtest='*IntegrationTest' test
./mvnw clean test
./mvnw -B clean package
```

Expected: all unit and integration tests PASS. Save the command output in the
task log; do not claim verification without successful output.

- [ ] **Step 7: Commit integration coverage**

```bash
git add pom.xml src/test/java/com/bixx/rapid_engine/integration
git commit -m "test: verify broker publishing and cursor retries"
```

### Task 8: Update runtime documentation and verify both configuration selections

**Files:**

- Modify: `README.md`
- Modify: `DEPLOYMENT.md`
- Modify:
  `docs/superpowers/specs/2026-07-20-kafka-rabbitmq-configurable-broker-design.md`
  only if it needs an explicit correction from implementation evidence;
  otherwise leave the approved spec unchanged.

**Interfaces:**

- Documents `MESSAGING_BROKER=rabbitmq|kafka`, broker-native destinations, and
  at-least-once response-batch delivery.
- Documents selected broker startup/provisioning and externally managed
  production broker requirements.

- [ ] **Step 1: Update the README architecture and configuration sections**

Replace Rabbit-only descriptions with:

- A broker-neutral
  `EventProducer → EventPublisher → RabbitEventPublisher | KafkaEventPublisher`
  architecture.
- The required switch:

```dotenv
MESSAGING_BROKER=rabbitmq
# or
MESSAGING_BROKER=kafka
```

- Rabbit destination table: queue, topic exchange, routing key for `matches` and
  `results`.
- Kafka destination table: `KAFKA_MATCHES_TOPIC` and `KAFKA_RESULTS_TOPIC`;
  records use no key.
- Serialization contract: both use Jackson snake-case JSON; headers differ by
  broker.
- Delivery contract: publish all events for one fetched response before
  persisting `delta_last_id`; failed batches leave the cursor unchanged;
  downstream consumers must deduplicate.
- Local instructions to start the stack once and switch only `MESSAGING_BROKER`.
- Accurate test commands including `./mvnw test` and
  `./mvnw -Dtest='*IntegrationTest' test`.

Remove stale generated-test language and stale roadmap claims that Docker/CI do
not exist.

- [ ] **Step 2: Update deployment guidance**

In `DEPLOYMENT.md`, replace “managed Redis and RabbitMQ” wording with selected
managed RabbitMQ or Kafka plus Redis. Document:

- Rabbit TLS variables already present.
- Kafka `KAFKA_BOOTSTRAP_SERVERS`, credentials/TLS properties if the deployment
  requires them, and Kafka topic administration privileges needed at startup.
- Production topic replication factor must not exceed cluster size; `1` is a
  local/single-broker default, not HA.
- `docker-compose.prod.yml` does not provision external brokers.
- The service fails at startup for missing/invalid `MESSAGING_BROKER` or
  unreachable selected broker topology provisioning.

- [ ] **Step 3: Verify configuration selection against local Compose**

With `.env.docker` set to RabbitMQ:

```bash
MESSAGING_BROKER=rabbitmq docker compose --env-file .env.docker up -d --build rapid-engine
docker compose logs --no-color rapid-engine
```

Expected: application starts and declares Rabbit resources without creating
Kafka publisher/topology beans.

Then switch only the environment variable:

```bash
MESSAGING_BROKER=kafka docker compose --env-file .env.docker up -d --build rapid-engine
docker compose logs --no-color rapid-engine
```

Expected: application starts, Kafka topics are provisioned, and no Rabbit
topology/publisher configuration is activated. Do not edit `docker-compose.yml`
between commands.

- [ ] **Step 4: Run final checks**

Run:

```bash
./mvnw -B clean test
docker compose --env-file .env.docker config
git status --short
git diff --check
```

Expected: tests pass, Compose renders, no whitespace errors exist, and only
intended documentation files are modified.

- [ ] **Step 5: Commit documentation**

```bash
git add README.md DEPLOYMENT.md
git commit -m "docs: describe kafka and rabbitmq broker selection"
```

## Final acceptance verification

- [ ] Run `./mvnw -B clean test` and confirm all unit/integration tests pass.
- [ ] Run `docker compose --env-file .env.docker config` and confirm both
  RabbitMQ and Kafka services render.
- [ ] Run the application with `MESSAGING_BROKER=rabbitmq` and verify Rabbit
  matches delivery.
- [ ] Run the application with `MESSAGING_BROKER=kafka` and verify Kafka matches
  delivery with a null record key.
- [ ] Verify a publication failure leaves `rundown:delta_last_id:<sportId>`
  unchanged and a retry produces permitted duplicates before a successful cursor
  write.
- [ ] Inspect `git status --short`, `git diff --check`, and recent commits
  before requesting review.
