package com.bixx.rapid_engine.utils;

import com.bixx.rapid_engine.config.RundownConfig;
import com.bixx.rapid_engine.producer.EventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchSyncTask {
    private final RundownConfig rundownConfig;
    private final EventProducer eventProducer;
    private final StringRedisTemplate stringRedisTemplate;
    private int sportIndex = 0;

    private static final int DAILY_DATA_POINT_BUDGET = 20_000;
    private static final int ESTIMATED_POINTS_PER_FETCH = 750;


    @Scheduled(fixedRate = 500_000, initialDelay = 20_000)
    public void fetchMatches(){
        // Scheduled to run after 1hr
        // delay the first call after the app start with 20s

        // 01. Check for the sportsId provided in the config
        List<Integer> sportIds = rundownConfig.getSportsId();
        if(sportIds == null || sportIds.isEmpty())
            return;

        // 02. Check for daily budget before fetching
        if(this.isBudgetExhausted()) {
            log.warn("Daily budgeted data points exhausted. Skip fetch");
            return;
        }

        // 03. Get the current sport ID and increment the index value for the next time
        Integer sportId = sportIds.get(this.sportIndex);
        log.info("Cycling to sport id {} ", sportId);

        log.info("Triggering match producer");
        int publishedEvent = this.eventProducer.fetchEvents(sportId);

        // 04. Track estimated data points used
        if(publishedEvent > 0) {
            this.trackDataPointUsed(publishedEvent);
        }

        // 05. Use Round-robin logic
        sportIndex = (sportIndex + 1) % sportIds.size();
    }

    private boolean isBudgetExhausted(){
        String usageKey = this.getDailyUsageKey();
        String usage = this.stringRedisTemplate.opsForValue().get(usageKey);

        if(usage == null)
            return false;

        int used = Integer.parseInt(usage);
        log.info("Daily data points used {}/{} ", used, DAILY_DATA_POINT_BUDGET);

        return used >= DAILY_DATA_POINT_BUDGET;
    }

    private void trackDataPointUsed(int eventsPublished){
        // Estimates the data points used
        // each event has at most 3 markets,
        int pointsUsed = eventsPublished * ESTIMATED_POINTS_PER_FETCH;

        String usageKey = this.getDailyUsageKey();

        // Increment counter - expire after 24hours
        this.stringRedisTemplate.opsForValue().increment(usageKey, pointsUsed);
        this.stringRedisTemplate.expire(usageKey, Duration.ofSeconds(24));

        log.info(
                "Estimated {} data points used for {} events ",
                pointsUsed,
                eventsPublished
        );

    }


    private String getDailyUsageKey(){
        // gets the dailyUsageKey
        // key resets daily - includes today's date
        return "rundown:data_points:%s".formatted(LocalDateTime.now());
    }
}