package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class LocalSpotlightRankHistoryResponse {
    private UUID branchId;
    private LocalDate from;
    private LocalDate to;
    private List<DailyPoint> points;

    @Data
    @Builder
    public static class DailyPoint {
        private LocalDate date;
        private String keyword;
        private String pinCode;
        private Integer yourRank;
        private boolean beyondTop20;
    }
}
