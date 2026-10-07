package com.salonplatform.dto.staffportal;

import com.salonplatform.domain.enums.StaffRole;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class StaffPortalProfileResponse {
    private UUID staffId;
    private UUID userId;
    private String name;
    private String email;
    private String phone;
    private String designation;
    private StaffRole role;
    private String skills;
    private UUID branchId;
    private String branchName;
    private LocalDate joiningDate;
    private boolean hasProfilePhoto;
    private boolean hasAadharDocument;
    private String idProofReference;
    private BigDecimal monthlySalesTarget;
    private BigDecimal incentivePercent;
}
