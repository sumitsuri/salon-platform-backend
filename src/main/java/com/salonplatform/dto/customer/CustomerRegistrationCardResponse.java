package com.salonplatform.dto.customer;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CustomerRegistrationCardResponse {
    private String tenantName;
    private String tenantLogoUrl;
    private String primaryColor;
    private String branchName;
    private String branchAddress;
    private String customerName;
    private String visitPassId;
    private String phone;
    private String publicPassUrl;
    private Instant issuedAt;
}
