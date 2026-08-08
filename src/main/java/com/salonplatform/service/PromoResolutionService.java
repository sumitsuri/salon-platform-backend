package com.salonplatform.service;

import com.salonplatform.domain.entity.Coupon;
import com.salonplatform.domain.entity.MembershipPlan;
import com.salonplatform.domain.entity.MembershipSubscription;
import com.salonplatform.domain.entity.Offer;
import com.salonplatform.domain.enums.PromoStatus;
import com.salonplatform.domain.repository.CouponRepository;
import com.salonplatform.domain.repository.MembershipPlanRepository;
import com.salonplatform.domain.repository.OfferRepository;
import com.salonplatform.dto.promo.ApplicablePromoResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.util.PromoScopeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromoResolutionService {

    private final CouponRepository couponRepository;
    private final OfferRepository offerRepository;
    private final MembershipService membershipService;
    private final MembershipPlanRepository membershipPlanRepository;

    public GstCalculationService.PromoContext resolveForBooking(
            UUID tenantId,
            UUID branchId,
            UUID customerId,
            UUID couponId,
            UUID offerId) {
        return resolveForBooking(tenantId, branchId, customerId, couponId, offerId, null);
    }

    public GstCalculationService.PromoContext resolveForBooking(
            UUID tenantId,
            UUID branchId,
            UUID customerId,
            UUID couponId,
            UUID offerId,
            UUID pendingMembershipPlanId) {
        if (couponId != null && offerId != null) {
            throw new BadRequestException("Select either a coupon or an offer, not both");
        }

        MembershipSubscription membership = membershipService.findActive(tenantId, customerId).orElse(null);
        MembershipPlan plan = null;
        MembershipPlan pendingPlan = null;
        if (membership != null) {
            plan = membershipPlanRepository.findById(membership.getPlanId()).orElse(null);
            if (plan != null && !PromoScopeUtils.branchAllowed(plan.getBranchIds(), branchId)) {
                membership = null;
                plan = null;
            }
        } else if (pendingMembershipPlanId != null) {
            pendingPlan = membershipPlanRepository.findById(pendingMembershipPlanId).orElse(null);
            if (pendingPlan != null) {
                if (pendingPlan.getStatus() != PromoStatus.ACTIVE
                        || !PromoScopeUtils.branchAllowed(pendingPlan.getBranchIds(), branchId)) {
                    pendingPlan = null;
                } else {
                    plan = pendingPlan;
                }
            }
        }

        Coupon coupon = null;
        Offer offer = null;
        Instant now = Instant.now();

        if (couponId != null) {
            coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
            assertTenant(coupon.getTenantId(), tenantId);
            validateInstrument(coupon.getStatus(), coupon.getStartsAt(), coupon.getEndsAt(),
                    coupon.getBranchIds(), branchId, coupon.getMaxRedemptionsTotal(), coupon.getRedemptionCount(),
                    "Coupon");
        }
        if (offerId != null) {
            offer = offerRepository.findById(offerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
            assertTenant(offer.getTenantId(), tenantId);
            validateInstrument(offer.getStatus(), offer.getStartsAt(), offer.getEndsAt(),
                    offer.getBranchIds(), branchId, offer.getMaxRedemptionsTotal(), offer.getRedemptionCount(),
                    "Offer");
        }

        return GstCalculationService.PromoContext.builder()
                .membershipSubscription(membership)
                .membershipPlan(plan)
                .pendingMembershipPlan(pendingPlan)
                .coupon(coupon)
                .offer(offer)
                .build();
    }

    public List<ApplicablePromoResponse> listApplicable(UUID branchId) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBranchAccess(branchId);
        Instant now = Instant.now();
        List<ApplicablePromoResponse> result = new ArrayList<>();

        for (Coupon c : couponRepository.findByTenantIdAndStatus(tenantId, PromoStatus.ACTIVE)) {
            if (!isCurrentlyValid(c.getStartsAt(), c.getEndsAt(), now)) continue;
            if (!PromoScopeUtils.branchAllowed(c.getBranchIds(), branchId)) continue;
            if (exhausted(c.getMaxRedemptionsTotal(), c.getRedemptionCount())) continue;
            result.add(ApplicablePromoResponse.builder()
                    .id(c.getId())
                    .kind("COUPON")
                    .name(c.getName())
                    .code(c.getCode())
                    .discountType(c.getDiscountType())
                    .discountValue(c.getDiscountValue())
                    .serviceScope(c.getServiceScope())
                    .scopeIds(PromoScopeUtils.parseIds(c.getScopeIds()))
                    .endsAt(c.getEndsAt())
                    .status(c.getStatus())
                    .build());
        }
        for (Offer o : offerRepository.findByTenantIdAndStatus(tenantId, PromoStatus.ACTIVE)) {
            if (!isCurrentlyValid(o.getStartsAt(), o.getEndsAt(), now)) continue;
            if (!PromoScopeUtils.branchAllowed(o.getBranchIds(), branchId)) continue;
            if (exhausted(o.getMaxRedemptionsTotal(), o.getRedemptionCount())) continue;
            result.add(ApplicablePromoResponse.builder()
                    .id(o.getId())
                    .kind("OFFER")
                    .name(o.getName())
                    .discountType(o.getDiscountType())
                    .discountValue(o.getDiscountValue())
                    .serviceScope(o.getServiceScope())
                    .scopeIds(PromoScopeUtils.parseIds(o.getScopeIds()))
                    .endsAt(o.getEndsAt())
                    .status(o.getStatus())
                    .build());
        }
        return result;
    }

    public void incrementRedemptions(UUID couponId, UUID offerId) {
        if (couponId != null) {
            couponRepository.findById(couponId).ifPresent(c -> {
                c.setRedemptionCount((c.getRedemptionCount() == null ? 0 : c.getRedemptionCount()) + 1);
                couponRepository.save(c);
            });
        }
        if (offerId != null) {
            offerRepository.findById(offerId).ifPresent(o -> {
                o.setRedemptionCount((o.getRedemptionCount() == null ? 0 : o.getRedemptionCount()) + 1);
                offerRepository.save(o);
            });
        }
    }

    private void validateInstrument(
            PromoStatus status,
            Instant startsAt,
            Instant endsAt,
            String branchIds,
            UUID branchId,
            Integer max,
            Integer count,
            String label) {
        if (status != PromoStatus.ACTIVE) {
            throw new BadRequestException(label + " is not active");
        }
        if (!isCurrentlyValid(startsAt, endsAt, Instant.now())) {
            throw new BadRequestException(label + " is outside its validity window");
        }
        if (!PromoScopeUtils.branchAllowed(branchIds, branchId)) {
            throw new BadRequestException(label + " is not valid at this branch");
        }
        if (exhausted(max, count)) {
            throw new BadRequestException(label + " has reached its redemption limit");
        }
    }

    private boolean isCurrentlyValid(Instant startsAt, Instant endsAt, Instant now) {
        return (startsAt == null || !now.isBefore(startsAt)) && (endsAt == null || !now.isAfter(endsAt));
    }

    private boolean exhausted(Integer max, Integer count) {
        return max != null && count != null && count >= max;
    }

    private void assertTenant(UUID entityTenant, UUID tenantId) {
        if (!entityTenant.equals(tenantId)) {
            throw new ResourceNotFoundException("Promo not found");
        }
    }
}
