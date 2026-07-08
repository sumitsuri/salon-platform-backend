package com.salonplatform.dto.branch;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BranchTargetPerformanceResponse {
    private String periodLabel;
    private int meetingTargetCount;
    private int belowTargetCount;
    private List<BranchTargetPerformanceItem> branches;
}
