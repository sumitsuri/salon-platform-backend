package com.salonplatform.dto.staff;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StaffTargetTrendsResponse {
    private String periodLabel;
    private List<BranchStaffTargetTrends> branches;
}
