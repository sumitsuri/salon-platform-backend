package com.salonplatform.sales.domain.port;

public interface TenantReadPort {
    TenantSnapshot getSnapshot();
}
