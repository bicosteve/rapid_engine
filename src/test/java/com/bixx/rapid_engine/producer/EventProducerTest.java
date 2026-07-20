package com.bixx.rapid_engine.producer;

import com.bixx.rapid_engine.config.RundownConfig;
import com.bixx.rapid_engine.messaging.EventChannel;
import com.bixx.rapid_engine.messaging.EventPublishException;
import com.bixx.rapid_engine.messaging.EventPublisher;
import com.bixx.rapid_engine.models.Event;
import com.bixx.rapid_engine.models.Meta;
import com.bixx.rapid_engine.models.RundownResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EventProducer}.
 *
 * <p>The producer is tested in isolation by mocking every collaborator
 * (REST client, EventPublisher, RedisTemplate, ObjectMapper, configs).
 * The private {@code fetchEventsForADate} method is exercised via reflection
 * so we don't pay the 1.2s inter-call {@code Thread.sleep} cost that the
 * public {@code fetchEvents} would impose.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventProducerTest {

    @Mock private RundownConfig rundownConfig;
@Mock private RestTemplate restTemplate;
 @Mock private EventPublisher eventPublisher;
 @Mock private ObjectMapper objectMapper;
    @Mock private RedisTemplate<String, String> stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

 private EventProducer eventProducer;


    @BeforeEach
    void setUp() {
 eventProducer = new EventProducer(
 rundownConfig,
 restTemplate,
 eventPublisher,
 objectMapper,
 stringRedisTemplate);
        when(rundownConfig.getHost()).thenReturn("https://api.example.com");
        when(rundownConfig.getKey()).thenReturn("test-key");
        when(rundownConfig.getAffiliateId()).thenReturn("23");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // =========================================================================
    // fetchEvents (public entry point)
    // =========================================================================

    @Test
    @DisplayName("fetchEvents: catches and logs exception, returns 0")
    void fetchEvents_returnsZeroOnException() {
        // valueOps.get throws when invoked
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis down"));

        int result = eventProducer.fetchEvents(1);

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("fetchEvents: returns 0 when the API returns a non-200 status")
    void fetchEvents_returnsZeroOnNonOkStatus() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("oops"));

        int result = eventProducer.fetchEvents(1);

        assertThat(result).isZero();
        verify(eventPublisher, never()).publish(eq(EventChannel.MATCHES), any(Event.class));
    }

    // =========================================================================
    // fetchEventsForADate (private, invoked via reflection)
    // =========================================================================

    @Test
    @DisplayName("fetchEventsForADate: URL omits delta_last_id when Redis has no cursor")
    void fetchEventsForADate_urlOmitsDeltaWhenRedisEmpty() throws Exception {
        when(valueOps.get(anyString())).thenReturn(null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));
        when(objectMapper.readValue(eq("{}"), eq(RundownResponse.class)))
                .thenReturn(emptyResponse());

        invokeFetchForADate(1, LocalDate.now());

        org.mockito.ArgumentCaptor<String> urlCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(
                urlCaptor.capture(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class));

        assertThat(urlCaptor.getValue())
                .contains("/sports/1/events/")
                .contains("affiliate_ids=23")
                .doesNotContain("delta_last_id");
    }

    @Test
    @DisplayName("fetchEventsForADate: URL includes delta_last_id when present in Redis")
    void fetchEventsForADate_urlIncludesDeltaFromRedis() throws Exception {
        when(valueOps.get(anyString())).thenReturn("cursor-xyz");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));
        when(objectMapper.readValue(eq("{}"), eq(RundownResponse.class)))
                .thenReturn(emptyResponse());

        invokeFetchForADate(1, LocalDate.now());

        org.mockito.ArgumentCaptor<String> urlCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(
                urlCaptor.capture(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class));

        assertThat(urlCaptor.getValue())
                .contains("delta_last_id=cursor-xyz")
                .contains("affiliate_ids=23");
    }

    @Test
    @DisplayName("fetchEventsForADate: sends X-TheRundown-Key header from config")
    void fetchEventsForADate_setsApiKeyHeader() throws Exception {
        when(valueOps.get(anyString())).thenReturn(null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));
        when(objectMapper.readValue(eq("{}"), eq(RundownResponse.class)))
                .thenReturn(emptyResponse());

        invokeFetchForADate(1, LocalDate.now());

        org.mockito.ArgumentCaptor<HttpEntity<?>> entityCaptor =
                org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.GET),
                entityCaptor.capture(),
                eq(String.class));

        assertThat(entityCaptor.getValue().getHeaders().getFirst("X-TheRundown-Key"))
                .isEqualTo("test-key");
    }

    @Test
    @DisplayName("fetchEventsForADate: returns 0 and skips publish when API returns non-200")
    void fetchEventsForADate_returnsZeroOnNonOk() throws Exception {
        when(valueOps.get(anyString())).thenReturn(null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("upstream down"));

        int result = invokeFetchForADate(1, LocalDate.now());

        assertThat(result).isZero();
        verify(eventPublisher, never()).publish(eq(EventChannel.MATCHES), any(Event.class));
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("fetchEventsForADate: advances a new cursor after an empty successful batch")
    void fetchEventsForADate_advancesCursorAfterEmptyBatch() throws Exception {
        when(valueOps.get(anyString())).thenReturn(null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));
        when(objectMapper.readValue(eq("{}"), eq(RundownResponse.class)))
                .thenReturn(RundownResponse.builder()
                        .meta(Meta.builder().deltaLastId("new-delta-42").build())
                        .events(List.of())
                        .build());

        int result = invokeFetchForADate(1, LocalDate.now());

        assertThat(result).isZero();
        verify(eventPublisher, never()).publish(eq(EventChannel.MATCHES), any(Event.class));
        verify(valueOps).set(
                "rundown:delta_last_id:1", "new-delta-42", Duration.ofHours(24));
    }

    @Test
    @DisplayName("fetchEventsForADate: returns 0 when events list is null")
    void fetchEventsForADate_returnsZeroOnNullEvents() throws Exception {
        when(valueOps.get(anyString())).thenReturn(null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));
        when(objectMapper.readValue(eq("{}"), eq(RundownResponse.class)))
                .thenReturn(RundownResponse.builder().events(null).build());

        int result = invokeFetchForADate(1, LocalDate.now());

        assertThat(result).isZero();
    }

 @Test
 @DisplayName("fetchEventsForADate: publishes every event through the matches channel before saving its cursor")
 void fetchEventsForADate_publishesEventsBeforeSavingCursor() throws Exception {
 when(valueOps.get(anyString())).thenReturn(null);
 when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
 .thenReturn(ResponseEntity.ok("{}"));
 when(objectMapper.readValue(eq("{}"), eq(RundownResponse.class)))
 .thenReturn(responseWithDelta("new-delta-42", 2));

 int result = invokeFetchForADate(1, LocalDate.now());

 assertThat(result).isEqualTo(2);
 InOrder inOrder = inOrder(eventPublisher, valueOps);
 inOrder.verify(eventPublisher, times(2))
 .publish(eq(EventChannel.MATCHES), any(Event.class));
 inOrder.verify(valueOps).set(
 "rundown:delta_last_id:1", "new-delta-42", Duration.ofHours(24));
 }

 @Test
 @DisplayName("fetchEventsForADate: leaves cursor unchanged and stops when publishing fails")
 void fetchEventsForADate_doesNotAdvanceCursorAfterPublishFailure() throws Exception {
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

 @Test
 @DisplayName("fetchEventsForADate: persists the new delta_last_id in Redis with 24h TTL")
 void fetchEventsForADate_persistsDeltaWithTtl() throws Exception {
 when(valueOps.get(anyString())).thenReturn(null);
 when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
 .thenReturn(ResponseEntity.ok("{}"));
 when(objectMapper.readValue(eq("{}"), eq(RundownResponse.class)))
 .thenReturn(responseWithDelta("new-delta-42", 1));

 invokeFetchForADate(1, LocalDate.now());

 verify(valueOps).set(
 eq("rundown:delta_last_id:1"),
 eq("new-delta-42"),
 eq(Duration.ofHours(24)));
 }

    @Test
    @DisplayName("fetchEventsForADate: skips Redis SET when the new delta_last_id is null")
    void fetchEventsForADate_skipsSetWhenDeltaMissing() throws Exception {
        when(valueOps.get(anyString())).thenReturn(null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));
        when(objectMapper.readValue(eq("{}"), eq(RundownResponse.class)))
                .thenReturn(responseWithDelta(null, 2));

        int result = invokeFetchForADate(1, LocalDate.now());

        assertThat(result).isEqualTo(2);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("fetchEventsForADate: Redis key includes the sportId")
    void fetchEventsForADate_redisKeyIncludesSportId() throws Exception {
        when(valueOps.get("rundown:delta_last_id:7")).thenReturn(null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));
        when(objectMapper.readValue(eq("{}"), eq(RundownResponse.class)))
                .thenReturn(emptyResponse());

        invokeFetchForADate(7, LocalDate.now());

        verify(valueOps).get("rundown:delta_last_id:7");
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private RundownResponse emptyResponse() {
        return RundownResponse.builder()
                .meta(Meta.builder().deltaLastId(null).build())
                .events(List.of())
                .build();
    }

    private RundownResponse responseWithEvents(int count) {
        List<Event> events = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(Event.builder().eventId("e" + i).build());
        }
        return RundownResponse.builder()
                .meta(Meta.builder().deltaLastId("d-" + count).build())
                .events(events)
                .build();
    }

    private RundownResponse responseWithDelta(String delta, int eventCount) {
        List<Event> events = new java.util.ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            events.add(Event.builder().eventId("e" + i).build());
        }
        return RundownResponse.builder()
                .meta(Meta.builder().deltaLastId(delta).build())
                .events(events)
                .build();
    }

    /**
     * Invokes the private {@code fetchEventsForADate(Integer, LocalDate)} method
     * via reflection. Returns the unboxed {@code int} result.
     */
    private int invokeFetchForADate(Integer sportId, LocalDate date) throws Exception {
        Method m = EventProducer.class.getDeclaredMethod(
                "fetchEventsForADate", Integer.class, LocalDate.class);
        m.setAccessible(true);
        Object out = m.invoke(eventProducer, sportId, date);
        return (Integer) out;
    }
}
