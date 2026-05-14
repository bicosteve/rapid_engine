package com.bixx.rapid_engine.utils;

import com.bixx.rapid_engine.config.RundownConfig;
import com.bixx.rapid_engine.producer.EventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchSyncTask {
    private final RundownConfig rundownConfig;
    private final EventProducer eventProducer;
    private final StringRedisTemplate stringRedisTemplate;
    private int sportIndex = 0;


    @Scheduled(fixedRate = 300_000, initialDelay = 20_000)
    public void fetchMatches(){
        // Scheduled to run after 5mins
        // delay the first call after the app start with 20s

        // 01. Check for the sportsId provided in the config
        List<Integer> sportIds = rundownConfig.getSportsId();
        if(sportIds == null || sportIds.isEmpty())
            return;

        // 02. Get the current sport IDs
        // and increment the index value for the next time
        Integer sportId = sportIds.get(this.sportIndex);
        log.info("Cycling to sport id {} ", sportId);

        // 03. Track published events
        log.info("Trigger match producer");
        int publishedEvents = this.eventProducer.fetchEvents(sportId);
        log.info("Published events {} ", publishedEvents);

        // 04. Use Round-robin logic
        sportIndex = (sportIndex + 1) % sportIds.size();
    }
}