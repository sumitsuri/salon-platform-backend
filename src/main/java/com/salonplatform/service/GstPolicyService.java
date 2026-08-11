package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves whether GST should be applied on bills.
 * Brand default is off ({@code tenants.gst_enabled = false}); branches may override.
 */
@Service
@RequiredArgsConstructor
public class GstPolicyService {

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;

    public boolean isGstEnabled(UUID tenantId, UUID branchId) {
        boolean brandEnabled = tenantRepository.findById(tenantId)
                .map(Tenant::getGstEnabled)
                .map(Boolean.TRUE::equals)
                .orElse(false);
        if (branchId == null) {
            return brandEnabled;
        }
        Branch branch = branchRepository.findById(branchId).orElse(null);
        if (branch == null || !branch.getTenantId().equals(tenantId)) {
            return brandEnabled;
        }
        if (branch.getGstEnabled() != null) {
            return Boolean.TRUE.equals(branch.getGstEnabled());
        }
        return brandEnabled;
    }

    /** Branch override when set; otherwise brand default. */
    public boolean isGstEnabled(Branch branch, Tenant tenant) {
        if (branch.getGstEnabled() != null) {
            return Boolean.TRUE.equals(branch.getGstEnabled());
        }
        return tenant != null && Boolean.TRUE.equals(tenant.getGstEnabled());
    }

    public boolean isBrandGstEnabled(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(Tenant::getGstEnabled)
                .map(Boolean.TRUE::equals)
                .orElse(false);
    }
}
