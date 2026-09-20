package com.salonplatform.sales.domain.enums;

/** How a map-discovered place relates to CRM ownership for the current user. */
public enum LeadClaimStatus {
    /** Not in CRM yet — rep can claim (creates lead). */
    OPEN,
    /** In CRM, nobody holds an active claim — rep can claim. */
    UNCLAIMED,
    /** Active claim held by the current sales rep. */
    CLAIMED_BY_ME,
    /** Active claim held by another rep until claim expiry. */
    CLAIMED_BY_OTHER,
    /** Prior claim expired — available to claim again. */
    CLAIM_EXPIRED
}
