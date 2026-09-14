package com.salonplatform.dto.packageplan;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ServicePackagePlanItemResponse {
    private UUID id;
    private UUID serviceId;
    private String serviceName;
    /** Tenant catalog list price per session (informational). */
    private BigDecimal listPrice;
    private Integer quantity;
    private Integer sortOrder;
}
