package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ServiceStats {
    private String serviceName;
    private BigDecimal revenue;
    private long count;
}
