package com.salonplatform.dto.branch;

import com.salonplatform.domain.enums.BranchBusinessType;
import com.salonplatform.domain.enums.BranchStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateBranchRequest {
    private String name;
    private String code;
    private String address;
    private String societyDefault;
    private String gstin;
    private String phone;
    private String openTime;
    private String closeTime;
    private BigDecimal monthlySalesTarget;
    private BranchStatus status;
    private BranchBusinessType businessType;
    /** Default true — phone required for customer registration at this branch. */
    private Boolean phoneNumberRequired;
    /** INHERIT, ENABLED, or DISABLED — branch GST policy override. */
    private String gstPolicy;
}
