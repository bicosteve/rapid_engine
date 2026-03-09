package com.bixx.rapid_engine.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MatchRepository {
    private final JdbcTemplate jdbcTemplate;


    public void insertMatch(String match){
        System.out.println(match);
    }
}
