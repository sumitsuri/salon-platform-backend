package com.salonplatform.dto.tenant;

import com.salonplatform.domain.enums.OutboundMessagingMode;
import lombok.Data;

/** Platform-only brand controls; null fields are left unchanged. */
@Data
public class UpdateTenantSandboxRequest {
    private Boolean demoTenant;
    private OutboundMessagingMode outboundMessagingMode;
    /** Platform license: Local Spotlight. */
    private Boolean localSpotlightEnabled;
    /** Platform license: Guest Voice / review routing. */
    private Boolean guestReviewsEnabled;
}
