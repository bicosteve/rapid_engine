package com.bixx.rapid_engine.config;

import com.bixx.rapid_engine.models.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link JacksonConfig}.
 *
 * <p>Asserts that the global {@link ObjectMapper} bean produced by the
 * configuration class honours every contract the rest of the application
 * relies on: snake_case naming, tolerant deserialization, ISO-8601 dates
 * and a registered {@link JavaTimeModule}.
 */
class JacksonConfigTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new JacksonConfig().objectMapper();
    }

    @Test
    @DisplayName("objectMapper: bean is non-null")
    void objectMapper_isNotNull() {
        assertThat(objectMapper).isNotNull();
    }

    @Test
    @DisplayName("objectMapper: does not fail on unknown properties")
    void doesNotFailOnUnknownProperties() throws JsonProcessingException {
        String json = """
                {
                  "event_id": "abc",
                  "unknown_field_that_does_not_map": "should not throw",
                  "future_only_field": 42
                }
                """;

        Event event = objectMapper.readValue(json, Event.class);
        assertThat(event.getEventId()).isEqualTo("abc");
    }

    @Test
    @DisplayName("objectMapper: serializes Java field names to snake_case")
    void serializesToSnakeCase() throws JsonProcessingException {
        Event event = Event.builder()
                .eventId("abc-123")
                .eventUuid("uuid-xyz")
                .sportId(1)
                .build();

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("event_id")).isTrue();
        assertThat(node.has("event_uuid")).isTrue();
        assertThat(node.has("sport_id")).isTrue();
    }

    @Test
    @DisplayName("objectMapper: deserializes snake_case keys to camelCase fields")
    void deserializesFromSnakeCase() throws JsonProcessingException {
        String json = """
                {
                  "event_id": "abc-123",
                  "event_uuid": "uuid-xyz",
                  "sport_id": 4
                }
                """;

        Event event = objectMapper.readValue(json, Event.class);

        assertThat(event.getEventId()).isEqualTo("abc-123");
        assertThat(event.getEventUuid()).isEqualTo("uuid-xyz");
        assertThat(event.getSportId()).isEqualTo(4);
    }

    @Test
    @DisplayName("objectMapper: uses SNAKE_CASE naming strategy")
    void usesSnakeCaseNamingStrategy() {
        assertThat(objectMapper.getSerializationConfig().getPropertyNamingStrategy())
                .isInstanceOf(PropertyNamingStrategies.SnakeCaseStrategy.class);
    }

    @Test
    @DisplayName("objectMapper: has JavaTimeModule registered")
    void hasJavaTimeModuleRegistered() {
        // getRegisteredModuleIds() returns the module's id (e.g. "jackson-datatype-jsr310")
        assertThat(objectMapper.getRegisteredModuleIds())
                .anyMatch(id -> id.toString().toLowerCase().contains("jsr310"));
    }

    @Test
    @DisplayName("objectMapper: serializes dates as ISO-8601 strings, not numeric timestamps")
    void doesNotSerializeDatesAsTimestamps() throws JsonProcessingException {
        Event event = Event.builder()
                .eventId("e1")
                .eventDate(OffsetDateTime.of(2026, 6, 7, 19, 0, 0, 0, ZoneOffset.UTC))
                .createdAt(LocalDateTime.of(2026, 6, 7, 19, 0, 0))
                .build();

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("event_date").isTextual()).isTrue();
        assertThat(node.get("event_date").asText()).startsWith("2026-06-07");
        assertThat(node.get("created_at").isTextual()).isTrue();
    }
}
