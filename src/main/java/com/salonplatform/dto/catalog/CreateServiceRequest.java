package com.salonplatform.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateServiceRequest {
    @NotNull
    private UUID categoryId;
    @NotBlank
    private String name;
    private String description;
    private String sacCode;
    private BigDecimal gstRate = new BigDecimal("18.00");
    private Integer durationMinutes;
}
