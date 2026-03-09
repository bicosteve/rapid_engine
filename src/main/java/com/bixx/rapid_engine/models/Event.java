package com.bixx.rapid_engine.models;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Data
@RequiredArgsConstructor
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
}
