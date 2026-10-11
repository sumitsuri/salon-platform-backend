package com.salonplatform.dto.platform;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PlatformUsageHeatmapResponse {

    private Instant from;
    private Instant to;

    /** Events and active users per app surface (admin / manager / employee). */
    private List<PortalSlice> portals;

    /** Brand × app grid for portfolio overview. */
    private List<TenantPortalCell> tenantPortalCells;

    /** Branch × app grid when a brand is in focus. */
    private List<BranchPortalCell> branchPortalCells;

    /** User × day intensity when a brand is in focus. */
    private List<UserDayCell> userDayCells;

    @Data
    @Builder
    public static class PortalSlice {
        private String portal;
        private int eventCount;
        private int activeUsers;
    }

    @Data
    @Builder
    public static class TenantPortalCell {
        private UUID tenantId;
        private String tenantName;
        private String portal;
        private int eventCount;
        private int activeUsers;
    }

    @Data
    @Builder
    public static class BranchPortalCell {
        private UUID branchId;
        private String branchName;
        private String branchCode;
        private String portal;
        private int eventCount;
        private int activeUsers;
    }

    @Data
    @Builder
    public static class UserDayCell {
        private UUID userId;
        private String userName;
        private String role;
        private String branchName;
        private LocalDate usageDate;
        private int eventCount;
    }
}
