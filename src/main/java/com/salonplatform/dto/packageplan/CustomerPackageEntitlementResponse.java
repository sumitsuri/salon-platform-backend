package com.salonplatform.dto.packageplan;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CustomerPackageEntitlementResponse {
    private UUID id;
    private UUID serviceId;
    private String serviceName;
    private Integer quantityTotal;
    private Integer quantityRemaining;
}
