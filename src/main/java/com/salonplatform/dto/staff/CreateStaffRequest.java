package com.salonplatform.dto.staff;

import com.salonplatform.domain.enums.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

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
}
