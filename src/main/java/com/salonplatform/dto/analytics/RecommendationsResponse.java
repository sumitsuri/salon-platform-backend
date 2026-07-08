package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RecommendationsResponse {
    private List<Recommendation> brandWide;
    private List<BranchRecommendations> branches;
    private List<WeekdaySalesInsight> weekdayInsights;
}
