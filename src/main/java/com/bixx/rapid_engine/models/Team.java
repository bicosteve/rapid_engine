package com.bixx.rapid_engine.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Team {
    private Integer id;
    private Integer teamId;
    private String eventId;
    private String name;
    private String abbreviation;
    private Boolean isAway;
    private Boolean isHome;
    private String record;
    private int sportId;
    private int conferenceId;
    private String leagueName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
