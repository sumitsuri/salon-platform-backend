package com.salonplatform.dto.branch;

import com.salonplatform.domain.enums.BranchStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateBranchRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String code;
    private String address;
    private String societyDefault;
    private String gstin;
    private String phone;
    private String openTime;
    private String closeTime;
    private BranchStatus status = BranchStatus.ACTIVE;
}
