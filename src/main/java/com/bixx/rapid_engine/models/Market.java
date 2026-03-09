package com.bixx.rapid_engine.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Market {
    private Integer id;
    private Integer rundownMarketId;
    private int marketTypeId;
    private int periodId;
    private String name;
    private String description;
    private String eventId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
