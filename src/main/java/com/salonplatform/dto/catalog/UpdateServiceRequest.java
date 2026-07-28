package com.salonplatform.dto.catalog;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class UpdateServiceRequest {
    private UUID categoryId;
    private String name;
    private String description;
    private String sacCode;
    private BigDecimal gstRate;
    private Integer durationMinutes;
    private Boolean active;
}
