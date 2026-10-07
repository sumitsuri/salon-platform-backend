package com.salonplatform.dto.analytics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocalSpotlightSyncProgressResponse {
    private boolean active;
    private String phase;
    private int completedSteps;
    private int totalSteps;
    private String detail;
    private int percent;
    /** Set when {@link #active} is false after the most recent sync finished. */
    private String lastSyncMessage;
    private String lastSyncError;
    private Boolean lastSyncSkipped;
}
