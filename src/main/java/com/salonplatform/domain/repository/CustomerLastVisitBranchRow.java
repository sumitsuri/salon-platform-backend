package com.salonplatform.domain.repository;

import java.util.UUID;

/** Branch name for a customer's most recent completed visit. */
public interface CustomerLastVisitBranchRow {
    UUID getCustomerId();

    String getBranchName();
}
