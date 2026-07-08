package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.dto.staff.CreateStaffRequest;
import com.salonplatform.dto.staff.StaffResponse;
import com.salonplatform.dto.staff.UpdateStaffRequest;
import com.salonplatform.exception.BadRequestException;
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
        requireTenantBranch(tenantId, request.getBranchId());

        Staff staff = staffRepository.save(Staff.builder()
                .tenantId(tenantId)
                .branchId(request.getBranchId())
                .name(request.getName())
                .phone(request.getPhone())
                .role(request.getRole() != null ? request.getRole() : com.salonplatform.domain.enums.StaffRole.STYLIST)
                .skills(request.getSkills())
                .biometricId(request.getBiometricId())
                .salary(request.getSalary())
                .joiningDate(request.getJoiningDate())
                .idProofCollected(request.getIdProofCollected() != null ? request.getIdProofCollected() : false)
                .idProofReference(request.getIdProofReference())
                .monthlySalesTarget(request.getMonthlySalesTarget())
                .incentivePercent(request.getIncentivePercent())
                .active(true)
                .build());
        return toResponse(staff);
    }

    public StaffResponse get(UUID id) {
        UUID tenantId = SecurityUtils.requireTenantId();
        Staff staff = requireStaff(tenantId, id);
        if (SecurityUtils.isManagerRole()) {
            SecurityUtils.assertBranchAccess(staff.getBranchId());
        } else {
            SecurityUtils.assertBrandAdminOrAbove();
        }
        return toResponse(staff);
    }

    @Transactional
    public StaffResponse update(UUID id, UpdateStaffRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Staff staff = requireStaff(tenantId, id);

        if (request.getName() != null) staff.setName(request.getName());
        if (request.getPhone() != null) staff.setPhone(request.getPhone());
        if (request.getBranchId() != null) {
            requireTenantBranch(tenantId, request.getBranchId());
            staff.setBranchId(request.getBranchId());
        }
        if (request.getRole() != null) staff.setRole(request.getRole());
        if (request.getSkills() != null) staff.setSkills(request.getSkills());
        if (request.getBiometricId() != null) staff.setBiometricId(request.getBiometricId());
        if (request.getSalary() != null) staff.setSalary(request.getSalary());
        if (request.getJoiningDate() != null) staff.setJoiningDate(request.getJoiningDate());
        if (request.getIdProofCollected() != null) staff.setIdProofCollected(request.getIdProofCollected());
        if (request.getIdProofReference() != null) staff.setIdProofReference(request.getIdProofReference());
        if (request.getMonthlySalesTarget() != null) staff.setMonthlySalesTarget(request.getMonthlySalesTarget());
        if (request.getIncentivePercent() != null) staff.setIncentivePercent(request.getIncentivePercent());
        if (request.getActive() != null) staff.setActive(request.getActive());

        return toResponse(staffRepository.save(staff));
    }

    @Transactional
    public void deactivate(UUID id) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Staff staff = requireStaff(tenantId, id);
        staff.setActive(false);
        staffRepository.save(staff);
    }

    public List<StaffResponse> listByBranch(UUID branchId) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBranchAccess(branchId);
        return staffRepository.findByTenantIdAndBranchIdAndActiveTrue(tenantId, branchId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<StaffResponse> listAll(UUID branchId) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBrandAdminOrAbove();
        List<Staff> staff;
        if (branchId != null) {
            staff = staffRepository.findByTenantIdAndBranchId(tenantId, branchId);
        } else {
            staff = staffRepository.findByTenantId(tenantId);
        }
        return staff.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private Staff requireStaff(UUID tenantId, UUID id) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        if (!staff.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Staff not found");
        }
        return staff;
    }

    private void requireTenantBranch(UUID tenantId, UUID branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        if (!branch.getTenantId().equals(tenantId)) {
            throw new BadRequestException("Branch does not belong to your brand");
        }
    }

    private StaffResponse toResponse(Staff s) {
        String branchName = branchRepository.findById(s.getBranchId()).map(Branch::getName).orElse(null);
        StaffResponse.StaffResponseBuilder builder = StaffResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .phone(s.getPhone())
                .branchId(s.getBranchId())
                .branchName(branchName)
                .role(s.getRole())
                .skills(s.getSkills())
                .biometricId(s.getBiometricId())
                .active(s.isActive());

        if (SecurityUtils.isBrandAdmin()) {
            builder.salary(s.getSalary())
                    .joiningDate(s.getJoiningDate())
                    .idProofCollected(Boolean.TRUE.equals(s.getIdProofCollected()))
                    .idProofReference(s.getIdProofReference())
                    .monthlySalesTarget(s.getMonthlySalesTarget())
                    .incentivePercent(s.getIncentivePercent());
        }
        return builder.build();
    }
}
