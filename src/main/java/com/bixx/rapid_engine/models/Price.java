package com.bixx.rapid_engine.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Price {
    private Integer id;
    private String rundownPriceId;
    private Integer participantId;
    private int bookmakerId;
    private String lineId;
    private String handicapValue;
    private int price;
    private int priceDelta;
    private boolean isMainLine;
    private BigDecimal odds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
