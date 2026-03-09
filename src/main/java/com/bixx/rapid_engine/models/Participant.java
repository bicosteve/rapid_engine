package com.bixx.rapid_engine.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Participant {
    private Integer id;
    private Integer participantType;
    private Integer marketId;
    private Integer rundownId;
    private String name;
    private String type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<Price> prices;
}
