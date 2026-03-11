package com.bixx.rapid_engine.repository;

import com.bixx.rapid_engine.models.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MatchRepository {
    private final JdbcTemplate jdbcTemplate;


    public void insertEvent(Event event){
        System.out.println(event.getEventId());
        this.jdbcTemplate.update("");
    }
}
