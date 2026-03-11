package com.bixx.rapid_engine.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Price {
    private String id;
    private int price;
    private int priceDelta;

    @JsonProperty("is_main_line")
    private boolean isMainLine;

    private LocalDateTime updatedAt;

    // Will be null but to be set by consumer at insert
    private Integer participantId;
    private Integer bookmakerId;
    private String lineId;
    private String handicapValue;
}
