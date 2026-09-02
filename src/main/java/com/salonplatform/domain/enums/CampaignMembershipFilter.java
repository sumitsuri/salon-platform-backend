package com.salonplatform.domain.enums;

public enum CampaignMembershipFilter {
    /** No active membership subscription. */
    NON_MEMBER,
    /** Has an active membership. */
    ACTIVE,
    /** Had membership but none active now. */
    EXPIRED,
    /** Active membership expiring within N days (see filterMembershipExpiringWithinDays). */
    EXPIRING_SOON
}
