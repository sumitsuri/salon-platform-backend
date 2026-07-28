package com.salonplatform.service;

import com.salonplatform.domain.entity.Coupon;
import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.domain.repository.CouponRepository;
import com.salonplatform.dto.promo.CouponResponse;
import com.salonplatform.dto.promo.CreateCouponRequest;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.util.PromoScopeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public CouponResponse create(CreateCouponRequest request) {
        SecurityUtils.assertBrandAdminOrAbove();
        UUID tenantId = SecurityUtils.requireTenantId();
        validateWindow(request.getStartsAt(), request.getEndsAt(), request.getDiscountType(), request.getDiscountValue());

        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        couponRepository.findByTenantIdAndCodeIgnoreCase(tenantId, code).ifPresent(c -> {
            throw new BadRequestException("Coupon code already exists");
        });

        Coupon coupon = Coupon.builder()
                .tenantId(tenantId)
                .name(request.getName().trim())
                .code(code)
                .description(request.getDescription())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .serviceScope(request.getServiceScope() != null ? request.getServiceScope() : com.salonplatform.domain.enums.ServiceScopeType.ALL)
                .scopeIds(PromoScopeUtils.joinIds(request.getScopeIds()))
                .branchIds(PromoScopeUtils.joinIds(request.getBranchIds()))
                .status(request.getStatus() != null ? request.getStatus() : PromoStatus.ACTIVE)
                .maxRedemptionsTotal(request.getMaxRedemptionsTotal())
                .createdByUserId(SecurityUtils.currentUser().getId())
                .build();
        return toResponse(couponRepository.save(coupon));
    }

    public List<CouponResponse> list() {
        SecurityUtils.assertBrandAdminOrAbove();
        return couponRepository.findByTenantIdOrderByCreatedAtDesc(SecurityUtils.requireTenantId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CouponResponse updateStatus(UUID id, PromoStatus status) {
        SecurityUtils.assertBrandAdminOrAbove();
        Coupon coupon = load(id);
        coupon.setStatus(status);
        return toResponse(couponRepository.save(coupon));
    }

    public Coupon load(UUID id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
        if (!coupon.getTenantId().equals(SecurityUtils.requireTenantId())) {
            throw new ResourceNotFoundException("Coupon not found");
        }
        return coupon;
    }

    private void validateWindow(java.time.Instant startsAt, java.time.Instant endsAt,
                                com.salonplatform.domain.enums.DiscountType type, BigDecimal value) {
        if (!endsAt.isAfter(startsAt)) {
            throw new BadRequestException("Coupon end must be after start");
        }
        if (type == com.salonplatform.domain.enums.DiscountType.PERCENT && value.compareTo(new BigDecimal("100")) > 0) {
            throw new BadRequestException("Percent discount cannot exceed 100");
        }
    }

    private CouponResponse toResponse(Coupon c) {
        return CouponResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .code(c.getCode())
                .description(c.getDescription())
                .discountType(c.getDiscountType())
                .discountValue(c.getDiscountValue())
                .startsAt(c.getStartsAt())
                .endsAt(c.getEndsAt())
                .serviceScope(c.getServiceScope())
                .scopeIds(PromoScopeUtils.parseIds(c.getScopeIds()))
                .branchIds(PromoScopeUtils.parseIds(c.getBranchIds()))
                .status(c.getStatus())
                .maxRedemptionsTotal(c.getMaxRedemptionsTotal())
                .redemptionCount(c.getRedemptionCount())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
