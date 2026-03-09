package com.bixx.rapid_engine.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Participant {
    private Integer id;
    private Integer participantId;
    private Integer marketId;
    private int rundownId;
    private String name;
    private String type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
