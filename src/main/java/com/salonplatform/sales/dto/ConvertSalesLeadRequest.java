package com.salonplatform.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ConvertSalesLeadRequest {

    @NotBlank
    private String tenantSlug;

    @NotBlank
    private String adminName;

    @NotBlank
    private String adminEmail;

    @NotBlank
    private String adminPassword;

    private String planTier;

    @NotNull
    private BigDecimal projectedMrr;
}
