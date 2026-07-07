package com.salonplatform.dto.tenant;

import com.salonplatform.domain.enums.TenantStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class TenantResponse {
    private UUID id;
    private String name;
    private String slug;
    private String logoUrl;
    private String primaryColor;
    private TenantStatus status;
    private Instant createdAt;
}
