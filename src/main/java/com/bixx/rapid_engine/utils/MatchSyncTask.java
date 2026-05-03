package com.bixx.rapid_engine.utils;

import com.bixx.rapid_engine.config.RundownConfig;
import com.bixx.rapid_engine.producer.EventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchSyncTask {
    private final RundownConfig rundownConfig;
    private final EventProducer eventProducer;
    private int sportIndex = 0;


    @Scheduled(fixedRate = 300_000, initialDelay = 20_000)
    public void fetchMatches(){
        // Scheduled to run after 1hr
        // delay the first call after the app start with 20s

        List<Integer> sportIds = rundownConfig.getSportsId();
        if(sportIds == null || sportIds.isEmpty())
            return;

        // Get the current sport ID and increment the index value for the next time
        Integer sportId = sportIds.get(this.sportIndex);
        log.info("Scheduler::Cycling to sport id {} ", sportId);

        log.info("Scheduler::triggering match producer");
        this.eventProducer.fetchEvents(sportId);

        // Use Round-robin logic
        sportIndex = (sportIndex + 1) % sportIds.size();
    }
}
