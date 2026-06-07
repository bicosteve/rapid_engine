package com.bixx.rapid_engine.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity-checks the model {@code @JsonProperty("is_main_line")} override on
 * {@link Price} and the global snake_case contract enforced by
 * {@link com.bixx.rapid_engine.config.JacksonConfig}.
 */
class EventSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Use the same configuration the application wires up in production.
        objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    @DisplayName("Event: serializes all fields to snake_case")
    void event_serializesToSnakeCase() throws JsonProcessingException {
        Event event = Event.builder()
                .eventId("abc")
                .eventUuid("uuid-xyz")
                .sportId(4)
                .eventDate(OffsetDateTime.of(2026, 6, 7, 19, 0, 0, 0, ZoneOffset.UTC))
                .createdAt(LocalDateTime.of(2026, 6, 7, 19, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 6, 7, 19, 5, 0))
                .build();

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("event_id")).isTrue();
        assertThat(node.has("event_uuid")).isTrue();
        assertThat(node.has("sport_id")).isTrue();
        assertThat(node.has("event_date")).isTrue();
        assertThat(node.has("created_at")).isTrue();
        assertThat(node.has("updated_at")).isTrue();
    }

    @Test
    @DisplayName("Price: is_main_line is present in the JSON output")
    void price_isMainLineSerializesAsIsMainLine() throws JsonProcessingException {
        Price price = Price.builder()
                .id("p1")
                .price(-110)
                .priceDelta(5)
                .isMainLine(true)
                .updatedAt(OffsetDateTime.of(2026, 6, 7, 19, 0, 0, 0, ZoneOffset.UTC))
                .build();

        String json = objectMapper.writeValueAsString(price);
        JsonNode node = objectMapper.readTree(json);

        // The @JsonProperty("is_main_line") annotation produces this key.
        // (Lombok's @Data additionally generates an `isMainLine()` boolean
        // getter, which the SNAKE_CASE strategy also serialises as
        // "main_line" after stripping the boolean "is" prefix; the test
        // here only asserts that the explicitly-named "is_main_line" key
        // is present and carries the correct value.)
        assertThat(node.has("is_main_line")).isTrue();
        assertThat(node.get("is_main_line").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Price: is_main_line deserializes back to isMainLine")
    void price_isMainLineDeserializesFromIsMainLine() throws JsonProcessingException {
        String json = """
                {
                  "id": "p1",
                  "price": -110,
                  "price_delta": 5,
                  "is_main_line": false,
                  "updated_at": "2026-06-07T19:00:00Z"
                }
                """;

        Price price = objectMapper.readValue(json, Price.class);

        assertThat(price.isMainLine()).isFalse();
        assertThat(price.getId()).isEqualTo("p1");
        assertThat(price.getPrice()).isEqualTo(-110);
    }

    @Test
    @DisplayName("Meta: deltaLastId serializes to delta_last_id")
    void meta_deltaLastIdSerializes() throws JsonProcessingException {
        Meta meta = Meta.builder().deltaLastId("d-42").build();

        String json = objectMapper.writeValueAsString(meta);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("delta_last_id")).isTrue();
        assertThat(node.get("delta_last_id").asText()).isEqualTo("d-42");
    }
}
