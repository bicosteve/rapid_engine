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
public class Score {
    private String eventId;
    private String eventStatus;
    private String eventStatusDetail;
    private Integer teamIdAway;
    private Integer teamIdHome;
    private int winnerAway;
    private int winnerHome;
    private int scoreAway;
    private int scoreHome;
    private int gameClock;
    private int gamePeriod;
    private String broadcast;
    private String venueName;
    private String venueLocation;
    private LocalDateTime updatedAt;
}
