package com.salonplatform.dto.branch;

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
}
