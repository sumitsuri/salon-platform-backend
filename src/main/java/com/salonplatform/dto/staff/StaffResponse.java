package com.salonplatform.dto.staff;

import com.salonplatform.domain.enums.StaffRole;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class StaffResponse {
    private UUID id;
    private String name;
    private String phone;
    private UUID branchId;
    private String branchName;
    private StaffRole role;
    private String skills;
    private String biometricId;
    private boolean active;
    /** Populated only for BRAND_ADMIN (CEO) */
    private BigDecimal salary;
    private LocalDate joiningDate;
    private Boolean idProofCollected;
    private String idProofReference;
    private BigDecimal monthlySalesTarget;
    private BigDecimal incentivePercent;
}
