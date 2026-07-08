package com.salonplatform.dto.branch;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BranchTargetTrendsResponse {
    private String periodLabel;
    private List<BranchTargetTrend> branches;
}
