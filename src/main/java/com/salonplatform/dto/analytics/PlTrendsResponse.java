package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PlTrendsResponse {
    private String periodLabel;
    private List<BranchPlTrend> branches;
}
