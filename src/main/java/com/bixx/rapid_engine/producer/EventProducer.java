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

import java.time.Duration;
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


    public void fetchEvents(){
       List<Integer> sportIds = this.rundownConfig.getSportsId();
           sportIds.forEach(sportId ->{
               try{
                   this.fetchAndPublishEvents(sportId);
                   Thread.sleep(2000);
                   // wait for 2s before next request.
                   // necessary to avoid 429 rate limit exception.
               }catch(InterruptedException ex){
                   Thread.currentThread().interrupt();
                   log.error("Producer::sleep interrupted for sport {}",sportId);
               } catch(Exception e) {
                   log.error("Producer::error fetching sport {} with error {}",
                           sportId,
                           e.getMessage()
                   );
               }
           });
    }


    private void fetchAndPublishEvents(Integer sportId) throws Exception {

        // 01. Check in Redis the last existing deltaLastId
        String redisKey = "rundown:delta_last_id:%s".formatted(sportId);
        String deltaLastId = this.stringRedisTemplate.opsForValue().get(redisKey);


        // 02. Build the URL
        LocalDate today = now();
        String url;
        if(deltaLastId == null){
            log.info("Producer::sport {} - no delta found, fetching all events",sportId);
            url = "%s/sports/%s/events/%s?affiliate_ids=%s".formatted(
                    this.rundownConfig.getHost(),
                    sportId,
                    today,
                    this.rundownConfig.getHost()
            );
        } else {
            log.info("Producer::sport {} - delta found {}, fetching updates",sportId,deltaLastId);
            url = "%s/sports/%s/events/%s?affiliate_ids=%s&delta_last_id=%s".formatted(
                    this.rundownConfig.getHost(),
                    sportId,
                    today,
                    this.rundownConfig.getHost(),
                    deltaLastId
            );

        }

        // 03. Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-TheRundown-Key", this.rundownConfig.getKey());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 04. Make the api call
        ResponseEntity<String> response = this.restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
        );

        if(response.getStatusCode() != HttpStatus.OK){
            log.error("Producer::sport {} - API returned {}", sportId, response.getStatusCode());
            return;
        }

        // 05. Parse the response into RundownResponse.class
        RundownResponse rundownResponse = this.objectMapper
                .readValue(response.getBody(), RundownResponse.class);

        // 06. Check for Events
        List<Event> events = rundownResponse.getEvents();
        if(events == null || events.isEmpty()){
            log.warn("Producer::sport {} - no events found for {}",sportId,today);
            return;
        }


        // 07. Get events & meta from response;
        Meta meta = rundownResponse.getMeta();
        String newDeltaLastId = meta.getDeltaLastId();
        if(newDeltaLastId != null){
            this.stringRedisTemplate.opsForValue().set(redisKey,newDeltaLastId, Duration.ofHours(24));
            log.info("Producer::sport {} - saved new delta {}",sportId,newDeltaLastId);
        }


        // 08. Publish each event from the events list to RabbitMQ
        events.forEach(event -> {
            this.rabbitTemplate.convertAndSend(
                    this.rabbitMQConfig.getExchange(),
                    this.rabbitMQConfig.getRoutingKey(),
                    event
            );
        });
        log.info("Producer::sport_id {} --> events {} ",sportId,events);
    }
}
