package com.salonplatform.sales.infrastructure;

import com.salonplatform.domain.enums.TenantStatus;
import com.salonplatform.dto.tenant.CreateTenantRequest;
import com.salonplatform.dto.tenant.TenantResponse;
import com.salonplatform.sales.domain.entity.SalesLead;
import com.salonplatform.sales.domain.port.ProvisionMode;
import com.salonplatform.sales.domain.port.TenantProvisionResult;
import com.salonplatform.sales.domain.port.TenantProvisioningPort;
import com.salonplatform.sales.dto.ConvertSalesLeadRequest;
import com.salonplatform.service.PlatformManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MonolithTenantProvisioningAdapter implements TenantProvisioningPort {

    private final PlatformManagementService platformManagementService;

    @Override
    public TenantProvisionResult provisionFromLead(SalesLead lead, ProvisionMode mode) {
        throw new UnsupportedOperationException("Use provisionFromLeadWithRequest for conversion");
    }

    public TenantProvisionResult provisionFromLeadWithRequest(SalesLead lead, ConvertSalesLeadRequest request, ProvisionMode mode) {
        CreateTenantRequest create = new CreateTenantRequest();
        create.setName(lead.getBusinessName());
        create.setSlug(request.getTenantSlug().trim().toLowerCase(Locale.ROOT));
        create.setAdminName(request.getAdminName());
        create.setAdminEmail(request.getAdminEmail().trim().toLowerCase(Locale.ROOT));
        create.setAdminPassword(request.getAdminPassword());
        create.setPrimaryColor("#6366f1");

        TenantResponse tenant = platformManagementService.createTenant(create);
        if (mode == ProvisionMode.TRIAL) {
            platformManagementService.updateTenantStatus(tenant.getId(), TenantStatus.TRIAL);
        }
        return new TenantProvisionResult(tenant.getId(), tenant.getSlug());
    }
}
