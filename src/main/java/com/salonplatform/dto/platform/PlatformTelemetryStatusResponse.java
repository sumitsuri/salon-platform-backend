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
    /** Total rows in {@code product_events} (sanity vs ranged portfolio queries). */
    private long eventsAllTime;
    /** Same date-window logic as default portfolio (UTC calendar days, last ~60 days). */
    private long eventsInPortfolioWindow;
}
