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

import java.time.LocalDate;
import java.util.List;

import static java.time.LocalDate.now;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventProducer {

    private final RundownConfig rundownConfig;
    private final RestTemplate restTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String,String> stringRedisTemplate;


    public void fetchAndPublishMatches(){
        try{
            // 00. Check in Redis the last existing deltaLastId
            String deltaLastId = this.stringRedisTemplate
                    .opsForValue()
                    .get("rundown:delta_last_id");

            // 01. Call the Rundown API
            LocalDate today = now();
            String url;
            if(deltaLastId == null){
                log.info("Producer::no delta_last_id found, fetching all events");
                url = "%s/sports/%s/events/%s".formatted(
                        this.rundownConfig.getBaseUrl(),
                        this.rundownConfig.getSportsId(),
                        today
                );
            } else {
                log.info("Producer::delta_last_id found {}, fetching updated events",deltaLastId);
                url = "%s/sports/%s/events/%s?delta_last_id=%s".formatted(
                        this.rundownConfig.getBaseUrl(),
                        this.rundownConfig.getSportsId(),
                        today,
                        deltaLastId
                );

            }


            HttpHeaders headers = new HttpHeaders();
            headers.set("X-TheRundown-Key", this.rundownConfig.getApiKey());

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if(response.getStatusCode() != HttpStatus.OK){
                log.error("Producer::Rundown API returned {}", response.getStatusCode());
                return;
            }

            // Response Headers
            log.info("Headers returned with response {}",response.getHeaders());

            // 02. Parse the response into a list of Events
            RundownResponse rundownResponse = this.objectMapper
                    .readValue(response.getBody(), RundownResponse.class);

            // 03. Get deltaLastId from meta & save to Redis
            Meta meta = rundownResponse.getMeta();
            String newDeltaLastId = meta.getDeltaLastId();
            if(newDeltaLastId != null){
                this.stringRedisTemplate.opsForValue().set("rundown:delta_last_id",newDeltaLastId);
                log.info("Producer::saved new delta_last_id {}",newDeltaLastId);
            }

            // 04. Get events list from rundownResponse
            List<Event> events = rundownResponse.getEvents();

            // 05. Check if events exist
            if(events == null || events.isEmpty()){
                log.warn(
                        "Producer::No events found for {} for sport id {}",
                        today,this.rundownConfig.getSportsId()
                );

                return;
            }

            // 06. Publish each event to rabbit mq
            events.forEach(event -> {
                this.rabbitTemplate.convertAndSend(
                        this.rabbitMQConfig.getExchange(),
                        this.rabbitMQConfig.getRoutingKey(),
                        event
                );

                log.info("Producer::published {} match to exchange ", event.getEventId());
            });

            log.info("Producer::published {} matches", events.size());

        } catch(Exception e) {
            log.error("Producer::error: {}", e.getMessage());
        }

    }
}
