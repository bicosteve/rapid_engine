package com.bixx.rapid_engine.producer;

import com.bixx.rapid_engine.config.RundownConfig;
import com.bixx.rapid_engine.models.Event;
import com.bixx.rapid_engine.rabbitmq.RabbitMQConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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

    public void fetchAndPublishMatches(){
        try{
            // 01. Call the Rundown API
            String url = "%s/sports/%d/events/recent".formatted(this.rundownConfig.getBaseUrl());

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-TheRundown-Key", this.rundownConfig.getApiKey());
            HttpEntity<String> entity = new HttpEntity<>(headers);
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

            // 02. Parse the response into a list of matches
            List<Event> events = objectMapper
                    .readValue(response.getBody(), new TypeReference<List<Event >>() {});

            // 03. Publish each match to rabbit mq
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
