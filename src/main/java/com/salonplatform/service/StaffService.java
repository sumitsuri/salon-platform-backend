package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.dto.staff.CreateStaffRequest;
import com.salonplatform.dto.staff.StaffResponse;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;

    @Transactional
    public StaffResponse create(CreateStaffRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        Staff staff = staffRepository.save(Staff.builder()
                .tenantId(tenantId)
                .branchId(request.getBranchId())
                .name(request.getName())
                .phone(request.getPhone())
                .role(request.getRole())
                .skills(request.getSkills())
                .active(true)
                .build());
        return toResponse(staff);
    }

    public List<StaffResponse> listByBranch(UUID branchId) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBranchAccess(branchId);
        return staffRepository.findByTenantIdAndBranchIdAndActiveTrue(tenantId, branchId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<StaffResponse> listAll() {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBrandAdminOrAbove();
        return staffRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private StaffResponse toResponse(Staff s) {
        String branchName = branchRepository.findById(s.getBranchId()).map(Branch::getName).orElse(null);
        return StaffResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .phone(s.getPhone())
                .branchId(s.getBranchId())
                .branchName(branchName)
                .role(s.getRole())
                .skills(s.getSkills())
                .active(s.isActive())
                .build();
    }
}
