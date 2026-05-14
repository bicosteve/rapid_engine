package com.bixx.rapid_engine.producer;

import com.bixx.rapid_engine.config.RundownConfig;
import com.bixx.rapid_engine.models.Event;
import com.bixx.rapid_engine.models.Meta;
import com.bixx.rapid_engine.models.RundownResponse;
import com.bixx.rapid_engine.rabbitmq.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventProducer {

    private final RundownConfig rundownConfig;
    private final RestTemplate restTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> stringRedisTemplate;

    public int fetchEvents(Integer sportsId){
        try {
            return this.fetchAndPublishEvents(sportsId);
        } catch(Exception e) {
            log.error("Error fetching sport {} with error {}", sportsId, e.getMessage());
            return 0;
        }
    }

    private int fetchAndPublishEvents(Integer sportId) throws Exception{
        // 02. Build the URL
        // LocalDate.now(Clock.systemUTC())
        LocalDate today = LocalDate.now(Clock.systemUTC());
        int totalEvents = 0;

        // Yesterday's Games
        totalEvents += this.fetchEventsForADate(sportId, today.minusDays(1));

        Thread.sleep(1200); // Wait for a second

        // Today's Games
        totalEvents += this.fetchEventsForADate(sportId, today);

        Thread.sleep(1200);

        // Tomorrow's Games
        totalEvents += this.fetchEventsForADate(sportId, today.plusDays(1));

        return totalEvents;

    }


    private int fetchEventsForADate(Integer sportsId, LocalDate eventDate) throws Exception{
        // 01. Check in Redis the last existing deltaLastId
        String redisKey = "rundown:delta_last_id:%s".formatted(sportsId);
        String deltaLastId = this.stringRedisTemplate.opsForValue().get(redisKey);

        String url;
        if(deltaLastId == null) {
            log.info("No delta found for {}. Fetching the events data", sportsId);
            url = "%s/sports/%s/events/%s?affiliate_ids=%s".formatted(
                    this.rundownConfig.getHost(),
                    sportsId,
                    eventDate,
                    this.rundownConfig.getAffiliateId());
        } else {
            log.info("Delta {} found for sportId {}, fetching updates", deltaLastId, sportsId);
            url = "%s/sports/%s/events/%s?affiliate_ids=%s&delta_last_id=%s".formatted(
                    this.rundownConfig.getHost(),
                    sportsId,
                    eventDate,
                    this.rundownConfig.getAffiliateId(),
                    deltaLastId);

        }

        // 02. Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-TheRundown-Key", this.rundownConfig.getKey());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 03. Make the api call
        ResponseEntity<String> response = this.restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class);

        if(response.getStatusCode() != HttpStatus.OK) {
            log.error("Sport Id {} - API returned {}",
                    sportsId,
                    response.getStatusCode());

            return 0;
        }

        // 04. Parse the response into RundownResponse.class
        RundownResponse rundownResponse = this.objectMapper
                .readValue(response.getBody(), RundownResponse.class);

        // 05. Check for Events
        List<Event> events = rundownResponse.getEvents();
        if(events == null || events.isEmpty()) {
            log.warn(
                    "Date {}  - no events found for sportId {}",
                    eventDate,
                    sportsId);
            return 0;
        }

        // 06. Get events & meta from response
        Meta meta = rundownResponse.getMeta();
        String newDeltaLastId = meta.getDeltaLastId();
        if(newDeltaLastId != null) {
            this.stringRedisTemplate
                    .opsForValue()
                    .set(redisKey, newDeltaLastId, Duration.ofHours(24));

            log.info(
                    "Sport Id {} - saved new delta {}",
                    sportsId,
                    newDeltaLastId);
        }

        // 07. Publish each event from the events list to RabbitMQ
        events.forEach(event -> {
            this.rabbitTemplate.convertAndSend(
                    this.rabbitMQConfig.getMatches().getExchange(),
                    this.rabbitMQConfig.getMatches().getRoutingKey(),
                    event);

        });

        return events.size();
    }
}
