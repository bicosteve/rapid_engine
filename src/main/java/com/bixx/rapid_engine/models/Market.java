package com.bixx.rapid_engine.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Market {
    private Integer id;
    private Integer marketId;
    private int periodId;
    private String name;
    private String marketDescription;

    private String eventId; // set by consumer before table insert

    private List<Participant> participants;
}
