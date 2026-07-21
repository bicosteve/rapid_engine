# Kafka and RabbitMQ Configurable Broker Design

**Date:** 2026-07-20

## Goal

Allow rapid-engine to publish events to either RabbitMQ or Kafka by changing one required configuration value. The application runs with exactly one active broker publisher at a time.

```yaml
app:
  messaging:
    broker: rabbitmq # allowed values: rabbitmq, kafka
```

Missing or unsupported broker values fail application startup. The event JSON payload remains equivalent for both brokers.

## Scope

This change adds Kafka publishing alongside the existing RabbitMQ implementation. It includes selected-broker topology provisioning, local Docker Compose services for both brokers, delivery reliability changes, test coverage, and configuration/documentation updates.

It does not add consumers, an outbox, transactional coordination with Redis, or broker fan-out. Exactly one broker is selected for each application instance.

## Architecture

### Broker-neutral publishing port

Add an internal `EventPublisher` interface that accepts a logical channel and event payload:

```java
interface EventPublisher {
    void publish(EventChannel channel, Event event);
}

enum EventChannel {
    MATCHES,
    RESULTS
}
```

`EventProducer` depends solely on this interface. It must no longer inject or import RabbitMQ-specific components such as `RabbitTemplate` or `RabbitMQConfig`.

Two adapters implement the port:

- `RabbitEventPublisher` maps each `EventChannel` to its Rabbit exchange and routing key, and publishes through RabbitMQ.
- `KafkaEventPublisher` maps each `EventChannel` to its Kafka topic and publishes a record without a key.

`MATCHES` replaces the current direct Rabbit publish path. `RESULTS` is configured and provisioned in both adapters but is not emitted until a future producer uses it.

### Conditional wiring

Use `@ConditionalOnProperty` keyed by `app.messaging.broker` to activate a single broker adapter:

- `rabbitmq`: activates `RabbitEventPublisher` plus Rabbit queues, topic exchanges, bindings, message converter, and template configuration.
- `kafka`: activates `KafkaEventPublisher` plus Kafka topic declarations.

No selected-broker-independent publisher may inject a broker client. The unselected broker has no application publisher or topology beans, so changing the broker name requires no code change or Compose-file modification.

## Configuration

Connection settings remain native to Spring Boot:

```yaml
spring:
  rabbitmq:
    # connection and TLS properties
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      acks: all
      properties:
        enable.idempotence: true

app:
  messaging:
    broker: kafka
  rabbitmq:
    matches:
      queue: matches.queue
      exchange: matches.exchange
      routing-key: matches
    results:
      queue: results.queue
      exchange: results.exchange
      routing-key: results
  kafka:
    matches:
      topic: matches.events
    results:
      topic: results.events
    topic-defaults:
      partitions: 1
      replication-factor: 1
```

Rabbit properties retain their existing semantics. Kafka configuration contains only Kafka-specific topic data; Rabbit queues, exchanges, and routing keys are not forced into a shared destination schema.

Both adapters serialize events with the existing shared snake-case `ObjectMapper`. Payload fields and values must match across brokers. Header and transport metadata may remain broker-specific.

## Publishing and failure semantics

The application provides at-least-once delivery per polling cycle:

1. Calculate outgoing event changes against the current Redis cursor.
2. Publish all events via `EventPublisher` and wait for broker acknowledgement.
3. Persist the updated Redis cursor only after every publish succeeds.

Rabbit publishing uses publisher confirms and treats nacks or unroutable returned messages as failures. Kafka publishing waits for the producer send future and uses acknowledged, idempotent producer settings.

Any failure aborts the poll and leaves the existing Redis cursor unchanged. A later poll can republish previously acknowledged messages if a failure occurs partway through the batch. Consumers must therefore be idempotent or deduplicate events.

## Destination provisioning

The selected adapter provisions both channels during application startup:

- RabbitMQ declares the existing queues, topic exchanges, and bindings for `MATCHES` and `RESULTS`.
- Kafka declares configured `matches` and `results` topics using the configured partition count and replication factor; defaults are one for local development.

The local Docker Compose setup starts both RabbitMQ and Kafka. Application configuration still selects exactly one active connection and one topology provisioner.

## Dependencies and deployment

Add Spring Kafka support and the test dependencies needed for Kafka Testcontainers integration. Keep the existing AMQP support.

Update `application-dev.yaml`, `application-prod.yaml`, `.env-example`, `env.docker.example`, Docker Compose, README, and deployment guidance to document:

- `app.messaging.broker` selection;
- RabbitMQ connection variables;
- Kafka bootstrap/security variables;
- per-broker destination configuration;
- the at-least-once and duplicate-delivery contract.

## Testing

1. Update `EventProducer` unit tests to mock `EventPublisher`, verify `MATCHES` publication, publisher failure propagation, and that the Redis cursor is not advanced after failures.
2. Add Rabbit adapter tests for channel-to-exchange/routing-key mapping, confirms/returned-message failures, and JSON serialization.
3. Add Kafka adapter tests for channel-to-topic mapping, acknowledgement failure handling, and the same JSON serialization contract.
4. Add Spring wiring tests for `rabbitmq` and `kafka`, verifying that only the selected publisher and topology configuration beans are created.
5. Add Testcontainers integration tests for both brokers that validate successful publishing and retry-safe cursor behavior after publish failure.

## Acceptance criteria

- Setting `app.messaging.broker=rabbitmq` routes existing matches events to RabbitMQ and provisions Rabbit destinations.
- Setting `app.messaging.broker=kafka` routes the same events to the Kafka matches topic and provisions Kafka topics.
- Invalid or missing broker selection prevents startup.
- Kafka messages have no key and contain the same event JSON payload as Rabbit messages.
- No cursor update occurs when any publication in a cycle fails.
- Local Compose starts RabbitMQ and Kafka without needing edits to switch the application between them.
- Unit, wiring, and broker integration tests cover both selected paths.
