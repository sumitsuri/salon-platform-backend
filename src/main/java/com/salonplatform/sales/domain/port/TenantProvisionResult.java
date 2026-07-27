package com.salonplatform.sales.domain.port;

import java.util.UUID;

public record TenantProvisionResult(UUID tenantId, String tenantSlug) {}
