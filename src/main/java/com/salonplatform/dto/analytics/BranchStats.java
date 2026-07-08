package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BranchStats {
    private UUID branchId;
    private String branchName;
    private BigDecimal revenue;
    private long visits;
    private BigDecimal avgTicket;
    private BigDecimal discountAmount;
    private List<StaffStats> topStaff;
    private List<ServiceStats> topServices;
}
