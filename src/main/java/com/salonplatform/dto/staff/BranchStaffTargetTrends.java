package com.salonplatform.dto.staff;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BranchStaffTargetTrends {
    private UUID branchId;
    private String branchName;
    private List<StaffTargetTrend> staff;
}
