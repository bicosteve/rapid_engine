package com.bixx.rapid_engine.utils;

import com.bixx.rapid_engine.producer.EventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchSyncTask {
    private final EventProducer eventProducer;

    @Scheduled(fixedRate = 3_600_000, initialDelay = 30_000)
    public void fetchMatches(){
        log.info("Scheduler::triggering match producer");
        this.eventProducer.fetchAndPublishMatches();
    }
}
