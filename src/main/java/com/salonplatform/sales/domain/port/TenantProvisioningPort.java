package com.salonplatform.sales.domain.port;

import com.salonplatform.sales.domain.entity.SalesLead;

public interface TenantProvisioningPort {
    TenantProvisionResult provisionFromLead(SalesLead lead, ProvisionMode mode);
}
