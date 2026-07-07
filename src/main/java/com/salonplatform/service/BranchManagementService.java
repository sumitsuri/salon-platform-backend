package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.dto.branch.BranchResponse;
import com.salonplatform.dto.branch.CreateBranchRequest;
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

    @Transactional
    public BranchResponse create(CreateBranchRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
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
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        return toResponse(branch);
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
                .status(b.getStatus())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
