package com.salonplatform.service;

import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ForbiddenException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffAccessService {

    private final StaffRepository staffRepository;

    public Staff requireCurrentStaff() {
        if (!SecurityUtils.isSalonStaff()) {
            throw new ForbiddenException("Staff app access required");
        }
        UUID tenantId = SecurityUtils.requireTenantId();
        UUID userId = SecurityUtils.currentUserId();
        Staff staff = staffRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new BadRequestException("No employee profile linked to this login"));
        if (!staff.isActive()) {
            throw new BadRequestException("Employee profile is inactive");
        }
        return staff;
    }

    public Staff requireStaffInTenant(UUID staffId) {
        UUID tenantId = SecurityUtils.requireTenantId();
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        if (!staff.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Staff not found");
        }
        return staff;
    }

    public void assertCanViewStaff(Staff staff) {
        if (SecurityUtils.isSalonStaff()) {
            Staff self = requireCurrentStaff();
            if (!self.getId().equals(staff.getId())) {
                throw new ForbiddenException("Access denied");
            }
            return;
        }
        if (SecurityUtils.isManagerRole()) {
            SecurityUtils.assertBranchAccess(staff.getBranchId());
            return;
        }
        SecurityUtils.assertBrandAdminOrAbove();
    }
}
