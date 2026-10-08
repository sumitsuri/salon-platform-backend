package com.salonplatform.dto.staffportal;

import lombok.Data;

@Data
public class UpdateStaffPortalProfileRequest {
    private String phone;
    private String bankAccountNumber;
    private String bankName;
    private String bankIfscCode;
}
