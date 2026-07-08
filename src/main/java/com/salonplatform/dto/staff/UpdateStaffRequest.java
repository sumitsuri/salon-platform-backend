package com.salonplatform.dto.staff;

import com.salonplatform.domain.enums.StaffRole;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateStaffRequest {
    private String name;
    private String phone;
    private UUID branchId;
    private StaffRole role;
    private String skills;
    private String biometricId;
    private BigDecimal salary;
    private LocalDate joiningDate;
    private Boolean idProofCollected;
    private String idProofReference;
    private BigDecimal monthlySalesTarget;
    private BigDecimal incentivePercent;
    private Boolean active;
}
