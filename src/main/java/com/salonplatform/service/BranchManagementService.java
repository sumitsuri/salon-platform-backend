package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.BranchStatus;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.branch.BranchResponse;
import com.salonplatform.dto.branch.CreateBranchRequest;
import com.salonplatform.dto.branch.UpdateBranchRequest;
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
public class BranchManagementService {

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    @Transactional
    public BranchResponse create(CreateBranchRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        if (branchRepository.findByTenantIdAndCode(tenantId, request.getCode()).isPresent()) {
            throw new BadRequestException("Branch code already exists");
        }
        Branch branch = branchRepository.save(Branch.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .code(request.getCode())
                .address(request.getAddress())
                .societyDefault(request.getSocietyDefault())
                .gstin(request.getGstin())
                .phone(request.getPhone())
                .openTime(request.getOpenTime())
                .closeTime(request.getCloseTime())
                .monthlySalesTarget(request.getMonthlySalesTarget())
                .status(request.getStatus())
                .build());
        return toResponse(branch);
    }

    public List<BranchResponse> list() {
        UUID tenantId = SecurityUtils.requireTenantId();
        return branchRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public BranchResponse get(UUID id) {
        UUID tenantId = SecurityUtils.requireTenantId();
        return toResponse(requireBranch(tenantId, id));
    }

    @Transactional
    public BranchResponse update(UUID id, UpdateBranchRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Branch branch = requireBranch(tenantId, id);

        if (request.getCode() != null && !request.getCode().equals(branch.getCode())) {
            branchRepository.findByTenantIdAndCode(tenantId, request.getCode())
                    .filter(b -> !b.getId().equals(id))
                    .ifPresent(b -> { throw new BadRequestException("Branch code already exists"); });
            branch.setCode(request.getCode());
        }
        if (request.getName() != null) branch.setName(request.getName());
        if (request.getAddress() != null) branch.setAddress(request.getAddress());
        if (request.getSocietyDefault() != null) branch.setSocietyDefault(request.getSocietyDefault());
        if (request.getGstin() != null) branch.setGstin(request.getGstin());
        if (request.getPhone() != null) branch.setPhone(request.getPhone());
        if (request.getOpenTime() != null) branch.setOpenTime(request.getOpenTime());
        if (request.getCloseTime() != null) branch.setCloseTime(request.getCloseTime());
        if (request.getMonthlySalesTarget() != null) branch.setMonthlySalesTarget(request.getMonthlySalesTarget());
        if (request.getStatus() != null) branch.setStatus(request.getStatus());

        return toResponse(branchRepository.save(branch));
    }

    @Transactional
    public void deactivate(UUID id) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Branch branch = requireBranch(tenantId, id);
        branch.setStatus(BranchStatus.INACTIVE);
        branchRepository.save(branch);
        userRepository.findByTenantIdAndBranchId(tenantId, id).forEach(u -> {
            u.setActive(false);
            userRepository.save(u);
        });
    }

    private Branch requireBranch(UUID tenantId, UUID id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        if (!branch.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Branch not found");
        }
        return branch;
    }

    private BranchResponse toResponse(Branch b) {
        return BranchResponse.builder()
                .id(b.getId())
                .name(b.getName())
                .code(b.getCode())
                .address(b.getAddress())
                .societyDefault(b.getSocietyDefault())
                .gstin(b.getGstin())
                .phone(b.getPhone())
                .openTime(b.getOpenTime())
                .closeTime(b.getCloseTime())
                .monthlySalesTarget(b.getMonthlySalesTarget())
                .status(b.getStatus())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
