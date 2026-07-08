package com.salonplatform.dto.staff;

import com.salonplatform.domain.enums.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateStaffRequest {
    @NotBlank
    private String name;
    private String phone;
    @NotNull
    private UUID branchId;
    private StaffRole role = StaffRole.STYLIST;
    private String skills;
    private String biometricId;
    private BigDecimal salary;
    private LocalDate joiningDate;
    private Boolean idProofCollected;
    private String idProofReference;
    private BigDecimal monthlySalesTarget;
    private BigDecimal incentivePercent;
}
