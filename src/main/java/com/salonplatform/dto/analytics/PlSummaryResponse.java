package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PlSummaryResponse {
    private String periodLabel;
    private BrandPlSummary brand;
    private List<BranchPlSummary> branches;
}
