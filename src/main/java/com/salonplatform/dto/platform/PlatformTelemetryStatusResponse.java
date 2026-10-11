package com.salonplatform.dto.platform;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformTelemetryStatusResponse {
    private boolean ingestEnabled;
    private boolean eventsTableReady;
    private long eventsLast24Hours;
    private long rollupRowsLast24Hours;
    /** Events with tenant_id set (brand activity). */
    private long eventsWithTenantLast24Hours;
    private String latestEventAt;
}
