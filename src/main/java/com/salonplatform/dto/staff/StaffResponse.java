package com.salonplatform.dto.staff;

import com.salonplatform.domain.enums.StaffRole;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
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
    private String designation;
    private boolean hasStaffLogin;
    /** Login email when {@link #hasStaffLogin}; passwords are never returned. */
    private String staffLoginEmail;
    private boolean active;
    /** Set once the employee is soft-deactivated; their earlier data is retained. */
    private Instant deactivatedAt;
    /** Populated only for BRAND_ADMIN (CEO) */
    private BigDecimal salary;
    private LocalDate joiningDate;
    private LocalDate exitDate;
    private Boolean idProofCollected;
    private String idProofReference;
    private BigDecimal monthlySalesTarget;
    private BigDecimal incentivePercent;
}
