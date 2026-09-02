package com.salonplatform.domain.enums;

public enum CampaignStatus {
    /** Saved campaign definition — ready to send (or send again). */
    ACTIVE,
    /** @deprecated Legacy — treated as {@link #ACTIVE} for send eligibility. */
    DRAFT,
    /** @deprecated Legacy campaign-level sending — use {@link com.salonplatform.domain.entity.CampaignRun} instead. */
    SENDING,
    /** @deprecated Legacy — treated as {@link #ACTIVE} for send eligibility. */
    COMPLETED,
    /** @deprecated Legacy — treated as {@link #ACTIVE} for send eligibility. */
    FAILED,
    ARCHIVED
}
