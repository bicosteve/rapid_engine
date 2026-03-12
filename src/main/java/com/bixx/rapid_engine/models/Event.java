package com.bixx.rapid_engine.models;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Event {
    private String eventId;
    private String eventUuid;
    private Integer sportId;
    private LocalDateTime eventDate;
    private LocalDateTime createdAt; // to be set by DB in consumer
    private LocalDateTime updatedAt; // tp be set by DB in consumer

    private Score score;
    private List<Team> teams;
    private Schedule schedule;
    private List<Market> markets;
}
