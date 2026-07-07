package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class StaffStats {
    private UUID staffId;
    private String staffName;
    private String branchName;
    private BigDecimal revenue;
}
