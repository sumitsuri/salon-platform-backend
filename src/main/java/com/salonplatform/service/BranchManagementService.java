package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.BranchBusinessType;
import com.salonplatform.domain.enums.BranchStatus;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.dto.branch.BranchResponse;
import com.salonplatform.dto.branch.CreateBranchRequest;
import com.salonplatform.dto.branch.UpdateBranchGeofenceRequest;
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
    private final GstPolicyService gstPolicyService;

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
                .businessType(request.getBusinessType() != null ? request.getBusinessType() : BranchBusinessType.SALON)
                .phoneNumberRequired(request.getPhoneNumberRequired() != null
                        ? request.getPhoneNumberRequired() : true)
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
        if (request.getBusinessType() != null && request.getBusinessType() != branch.getBusinessType()) {
            branch.setBusinessType(request.getBusinessType());
            branch.setGoogleSearchRankData(null);
            branch.setDigitalPresenceUpdatedAt(null);
        } else if (request.getBusinessType() != null) {
            branch.setBusinessType(request.getBusinessType());
        }
        if (request.getPhoneNumberRequired() != null) {
            branch.setPhoneNumberRequired(request.getPhoneNumberRequired());
        }
        applyGstPolicy(branch, request.getGstPolicy());

        return toResponse(branchRepository.save(branch));
    }

    @Transactional
    public BranchResponse updateDigitalPresence(UUID id, com.salonplatform.dto.branch.UpdateBranchDigitalPresenceRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Branch branch = requireBranch(tenantId, id);

        if (request.getGooglePlaceId() != null) branch.setGooglePlaceId(blankToNull(request.getGooglePlaceId()));
        if (request.getGoogleMapsUrl() != null) branch.setGoogleMapsUrl(blankToNull(request.getGoogleMapsUrl()));
        if (request.getGoogleReviewUrl() != null) branch.setGoogleReviewUrl(blankToNull(request.getGoogleReviewUrl()));
        if (request.getGoogleReviewAutoPublish() != null) branch.setGoogleReviewAutoPublish(request.getGoogleReviewAutoPublish());
        if (request.getGoogleRating() != null) branch.setGoogleRating(request.getGoogleRating());
        if (request.getGoogleReviewCount() != null) branch.setGoogleReviewCount(request.getGoogleReviewCount());
        if (request.getGbpPhotoCount() != null) branch.setGbpPhotoCount(request.getGbpPhotoCount());
        if (request.getGbpVideoCount() != null) branch.setGbpVideoCount(request.getGbpVideoCount());
        if (request.getGbpHasPhone() != null) branch.setGbpHasPhone(request.getGbpHasPhone());
        if (request.getGbpHasWebsite() != null) branch.setGbpHasWebsite(request.getGbpHasWebsite());
        if (request.getGbpHasHours() != null) branch.setGbpHasHours(request.getGbpHasHours());
        if (request.getGbpHasBookButton() != null) branch.setGbpHasBookButton(request.getGbpHasBookButton());
        if (request.getGbpServicesListedCount() != null) branch.setGbpServicesListedCount(request.getGbpServicesListedCount());
        if (request.getEstimatedSearchRank() != null) branch.setEstimatedSearchRank(request.getEstimatedSearchRank());
        branch.setDigitalPresenceUpdatedAt(java.time.Instant.now());

        return toResponse(branchRepository.save(branch));
    }

    @Transactional
    public BranchResponse updateGeofence(UUID id, UpdateBranchGeofenceRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        Branch branch = requireBranch(tenantId, id);
        branch.setLatitude(request.getLatitude());
        branch.setLongitude(request.getLongitude());
        if (request.getGeofenceRadiusMeters() != null) {
            branch.setGeofenceRadiusMeters(request.getGeofenceRadiusMeters());
        }
        if (request.getAttendanceGraceMinutes() != null) {
            branch.setAttendanceGraceMinutes(request.getAttendanceGraceMinutes());
        }
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
                .latitude(b.getLatitude())
                .longitude(b.getLongitude())
                .geofenceRadiusMeters(b.getGeofenceRadiusMeters())
                .attendanceGraceMinutes(b.getAttendanceGraceMinutes())
                .monthlySalesTarget(b.getMonthlySalesTarget())
                .status(b.getStatus())
                .businessType(b.getBusinessType())
                .phoneNumberRequired(b.getPhoneNumberRequired())
                .gstEnabled(b.getGstEnabled())
                .gstEffective(gstPolicyService.isGstEnabled(b.getTenantId(), b.getId()))
                .googleReviewUrl(b.getGoogleReviewUrl())
                .googleReviewAutoPublish(b.getGoogleReviewAutoPublish())
                .googlePlaceId(b.getGooglePlaceId())
                .googleMapsUrl(b.getGoogleMapsUrl())
                .googleRating(b.getGoogleRating())
                .googleReviewCount(b.getGoogleReviewCount())
                .gbpPhotoCount(b.getGbpPhotoCount())
                .gbpVideoCount(b.getGbpVideoCount())
                .gbpHasPhone(b.getGbpHasPhone())
                .gbpHasWebsite(b.getGbpHasWebsite())
                .gbpHasHours(b.getGbpHasHours())
                .gbpHasBookButton(b.getGbpHasBookButton())
                .gbpServicesListedCount(b.getGbpServicesListedCount())
                .estimatedSearchRank(b.getEstimatedSearchRank())
                .digitalPresenceUpdatedAt(b.getDigitalPresenceUpdatedAt())
                .createdAt(b.getCreatedAt())
                .build();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void applyGstPolicy(Branch branch, String gstPolicy) {
        if (gstPolicy == null || gstPolicy.isBlank()) {
            return;
        }
        switch (gstPolicy.trim().toUpperCase()) {
            case "INHERIT" -> branch.setGstEnabled(null);
            case "ENABLED" -> branch.setGstEnabled(true);
            case "DISABLED" -> branch.setGstEnabled(false);
            default -> throw new BadRequestException("Invalid gstPolicy: use INHERIT, ENABLED, or DISABLED");
        }
    }
}
