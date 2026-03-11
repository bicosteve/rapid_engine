package com.bixx.rapid_engine.consumer;

import com.bixx.rapid_engine.models.Event;
import com.bixx.rapid_engine.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchConsumer {
    private final String name;
    private final MatchRepository matchRepository;


    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void consume(Event event){
        log.info("Consumer::received match {}",event.getEventId());
        this.matchRepository.insertEvent(event);

        try{
            log.info("Consumer::match {} inserted successfully",event.getEventId());
        } catch(Exception e) {
            log.error("Consumer::error processing match {}:{}",event.getEventId(),e.getMessage());
            throw new RuntimeException(e);

        }

    }
}
