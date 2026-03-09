package com.bixx.rapid_engine.models;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Event {
    private Integer id;
    private String eventId;
    private Integer sportId;
    private LocalDateTime eventDate;
    private String venueName;
    private String venueLocation;
    private String seasonType;
    private Integer seasonYear;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Score score;
    private List<Team> teams;
    private List<Market> markets;
}
