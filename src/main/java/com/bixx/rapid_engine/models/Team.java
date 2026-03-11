package com.bixx.rapid_engine.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Team {
    private Integer teamId;
    private String eventId;
    private String name;
    private String mascot;
    private String abbreviation;
    private Integer conferenceId;
    private Integer divisionId;
    private Integer ranking;
    private String record;
    private Boolean isAway;
    private Boolean isHome;
    private Conference conference;
}
