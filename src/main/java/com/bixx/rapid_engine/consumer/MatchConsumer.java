package com.bixx.rapid_engine.consumer;

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
    public void consume(String match){
        // log.info("Consumer::received match {}",match.getMatchId());
        this.matchRepository.insertMatch(match);

        try{
            log.info("Consumer:match {} inserted successfully",match.getMatchId());
        } catch(Exception e) {
            log.error("Consumer::error processing match {}:{}",match.getMatchId(),e.getMessage());
            throw new RuntimeException(e);

        }

    }
}
