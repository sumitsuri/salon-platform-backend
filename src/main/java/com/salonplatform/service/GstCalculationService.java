package com.salonplatform.service;

import com.salonplatform.domain.entity.Booking;
import com.salonplatform.domain.entity.BookingLineItem;
import com.salonplatform.domain.entity.Coupon;
import com.salonplatform.domain.entity.MembershipPlan;
import com.salonplatform.domain.entity.MembershipSubscription;
import com.salonplatform.domain.entity.Offer;
import com.salonplatform.domain.entity.SalonService;
import com.salonplatform.domain.enums.DiscountType;
import com.salonplatform.domain.enums.ServiceScopeType;
import com.salonplatform.domain.repository.SalonServiceRepository;
import com.salonplatform.dto.billing.BillLinePreview;
import com.salonplatform.dto.billing.BillPreviewResponse;
import com.salonplatform.util.PromoScopeUtils;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes GST bill with membership (auto) then coupon XOR offer on eligible lines (pre-GST).
 */
@Service
@RequiredArgsConstructor
public class GstCalculationService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final SalonServiceRepository salonServiceRepository;

    public BillPreviewResponse calculate(Booking booking, List<BookingLineItem> lines) {
        return calculate(booking, lines, PromoContext.empty());
    }

    public BillPreviewResponse calculate(Booking booking, List<BookingLineItem> lines, PromoContext promo) {
        Map<UUID, SalonService> serviceById = new HashMap<>();
        for (BookingLineItem line : lines) {
            if (line.getServiceId() != null) {
                salonServiceRepository.findById(line.getServiceId())
                        .ifPresent(svc -> serviceById.put(svc.getId(), svc));
            }
        }

        List<BillLinePreview> linePreviews = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;
        BigDecimal totalMembershipDiscount = BigDecimal.ZERO;
        BigDecimal totalPromoDiscount = BigDecimal.ZERO;

        // First pass: gross after membership + line discount; track eligible promo bases for FLAT.
        List<LineScratch> scratches = new ArrayList<>();
        BigDecimal flatPromoEligibleBase = BigDecimal.ZERO;

        for (BookingLineItem line : lines) {
            SalonService svc = serviceById.get(line.getServiceId());
            UUID categoryId = svc != null ? svc.getCategoryId() : null;

            BigDecimal gross = line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
            boolean memberEligible = promo.getMembershipPlan() != null
                    && PromoScopeUtils.serviceEligible(
                            promo.getMembershipPlan().getServiceScope(),
                            promo.getMembershipPlan().getScopeIds(),
                            line.getServiceId(),
                            categoryId);

            BigDecimal membershipDiscount = BigDecimal.ZERO;
            if (memberEligible && promo.getMembershipPlan().getBenefitPercent() != null) {
                membershipDiscount = calculateDiscount(
                        gross, DiscountType.PERCENT, promo.getMembershipPlan().getBenefitPercent());
            }
            BigDecimal afterMembership = gross.subtract(membershipDiscount).max(BigDecimal.ZERO);

            BigDecimal manualLineDiscount = calculateDiscount(
                    afterMembership, line.getLineDiscountType(), line.getLineDiscountValue());
            BigDecimal afterManual = afterMembership.subtract(manualLineDiscount).max(BigDecimal.ZERO);

            boolean promoEligible = isPromoEligible(promo, line.getServiceId(), categoryId);
            if (promoEligible && promo.getPromoDiscountType() == DiscountType.FLAT) {
                flatPromoEligibleBase = flatPromoEligibleBase.add(afterManual);
            }

            scratches.add(LineScratch.builder()
                    .line(line)
                    .gross(gross)
                    .membershipDiscount(membershipDiscount)
                    .afterManual(afterManual)
                    .promoEligible(promoEligible)
                    .build());
        }

        for (LineScratch scratch : scratches) {
            BookingLineItem line = scratch.line;
            BigDecimal promoDiscount = BigDecimal.ZERO;

            if (scratch.promoEligible && promo.getPromoDiscountType() != null && promo.getPromoDiscountValue() != null) {
                if (promo.getPromoDiscountType() == DiscountType.PERCENT) {
                    promoDiscount = calculateDiscount(
                            scratch.afterManual, DiscountType.PERCENT, promo.getPromoDiscountValue());
                } else if (flatPromoEligibleBase.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal share = scratch.afterManual
                            .divide(flatPromoEligibleBase, 8, ROUNDING)
                            .multiply(promo.getPromoDiscountValue().min(flatPromoEligibleBase));
                    promoDiscount = share.setScale(SCALE, ROUNDING);
                }
            }

            BigDecimal taxable = scratch.afterManual.subtract(promoDiscount).max(BigDecimal.ZERO);
            BigDecimal halfRate = line.getGstRate().divide(BigDecimal.valueOf(2), 4, ROUNDING);
            BigDecimal cgst = taxable.multiply(halfRate).divide(BigDecimal.valueOf(100), SCALE, ROUNDING);
            BigDecimal sgst = taxable.multiply(halfRate).divide(BigDecimal.valueOf(100), SCALE, ROUNDING);
            BigDecimal lineTotal = taxable.add(cgst).add(sgst);

            BigDecimal combinedLineDiscount = scratch.membershipDiscount
                    .add(scratch.gross.subtract(scratch.membershipDiscount).subtract(scratch.afterManual).max(BigDecimal.ZERO))
                    .add(promoDiscount);

            linePreviews.add(BillLinePreview.builder()
                    .lineItemId(line.getId())
                    .serviceName(line.getServiceName())
                    .unitPrice(line.getUnitPrice())
                    .quantity(line.getQuantity())
                    .lineDiscount(combinedLineDiscount)
                    .taxableAmount(taxable)
                    .cgstAmount(cgst)
                    .sgstAmount(sgst)
                    .lineTotal(lineTotal)
                    .build());

            subtotal = subtotal.add(taxable);
            totalCgst = totalCgst.add(cgst);
            totalSgst = totalSgst.add(sgst);
            totalMembershipDiscount = totalMembershipDiscount.add(scratch.membershipDiscount);
            totalPromoDiscount = totalPromoDiscount.add(promoDiscount);
        }

        // Legacy bill-level discount (manual) applied after tax if no instrument promo used as bill discount.
        BigDecimal preBillDiscountTotal = subtotal.add(totalCgst).add(totalSgst);
        BigDecimal legacyBillDiscount = BigDecimal.ZERO;
        if (booking.getCouponId() == null && booking.getOfferId() == null
                && (totalPromoDiscount.compareTo(BigDecimal.ZERO) == 0)) {
            legacyBillDiscount = calculateDiscount(
                    preBillDiscountTotal, booking.getBillDiscountType(), booking.getBillDiscountValue());
        }
        BigDecimal grandTotal = preBillDiscountTotal.subtract(legacyBillDiscount).max(BigDecimal.ZERO);
        BigDecimal totalDiscount = totalMembershipDiscount.add(totalPromoDiscount).add(legacyBillDiscount);

        String membershipLabel = null;
        if (promo.getMembershipSubscription() != null && promo.getMembershipPlan() != null) {
            membershipLabel = promo.getMembershipPlan().getName()
                    + " (−" + promo.getMembershipPlan().getBenefitPercent().stripTrailingZeros().toPlainString() + "%)";
        } else if (promo.getPendingMembershipPlan() != null) {
            membershipLabel = promo.getPendingMembershipPlan().getName()
                    + " (−" + promo.getPendingMembershipPlan().getBenefitPercent().stripTrailingZeros().toPlainString()
                    + "% · new)";
        }
        String promoLabel = null;
        if (promo.getCoupon() != null) {
            promoLabel = "Coupon " + promo.getCoupon().getCode() + " · " + promo.getCoupon().getName();
        } else if (promo.getOffer() != null) {
            promoLabel = "Offer · " + promo.getOffer().getName();
        }

        String manualDiscountLabel = null;
        if (legacyBillDiscount.compareTo(BigDecimal.ZERO) > 0) {
            if (booking.getBillDiscountType() == DiscountType.PERCENT) {
                manualDiscountLabel = "Manager discount (−"
                        + booking.getBillDiscountValue().stripTrailingZeros().toPlainString() + "%)";
            } else {
                manualDiscountLabel = "Manager discount";
            }
            if (booking.getBillDiscountNote() != null && !booking.getBillDiscountNote().isBlank()) {
                manualDiscountLabel = manualDiscountLabel + " · " + booking.getBillDiscountNote().trim();
            }
        }

        BigDecimal membershipFeeAmount = BigDecimal.ZERO;
        String membershipFeeLabel = null;
        if (promo.getPendingMembershipPlan() != null && promo.getMembershipSubscription() == null) {
            MembershipPlan pendingPlan = promo.getPendingMembershipPlan();
            membershipFeeAmount = pendingPlan.getFeeAmount() != null ? pendingPlan.getFeeAmount() : BigDecimal.ZERO;
            if (membershipFeeAmount.compareTo(BigDecimal.ZERO) > 0) {
                membershipFeeLabel = "Membership · " + pendingPlan.getName();
                linePreviews.add(BillLinePreview.builder()
                        .serviceName(membershipFeeLabel)
                        .unitPrice(membershipFeeAmount)
                        .quantity(1)
                        .lineDiscount(BigDecimal.ZERO)
                        .taxableAmount(BigDecimal.ZERO)
                        .cgstAmount(BigDecimal.ZERO)
                        .sgstAmount(BigDecimal.ZERO)
                        .lineTotal(membershipFeeAmount)
                        .build());
                grandTotal = grandTotal.add(membershipFeeAmount);
            }
        }

        return BillPreviewResponse.builder()
                .lines(linePreviews)
                .subtotal(subtotal.add(totalMembershipDiscount).add(totalPromoDiscount))
                .membershipDiscountAmount(totalMembershipDiscount)
                .promoDiscountAmount(totalPromoDiscount)
                .manualDiscountAmount(legacyBillDiscount)
                .manualDiscountLabel(manualDiscountLabel)
                .discountAmount(totalDiscount)
                .taxableAmount(subtotal)
                .cgstAmount(totalCgst)
                .sgstAmount(totalSgst)
                .grandTotal(grandTotal)
                .couponId(promo.getCoupon() != null ? promo.getCoupon().getId() : booking.getCouponId())
                .offerId(promo.getOffer() != null ? promo.getOffer().getId() : booking.getOfferId())
                .membershipSubscriptionId(promo.getMembershipSubscription() != null
                        ? promo.getMembershipSubscription().getId()
                        : booking.getMembershipSubscriptionId())
                .membershipLabel(membershipLabel)
                .promoLabel(promoLabel)
                .membershipFeeAmount(membershipFeeAmount)
                .membershipFeeLabel(membershipFeeLabel)
                .build();
    }

    private boolean isPromoEligible(PromoContext promo, UUID serviceId, UUID categoryId) {
        ServiceScopeType scope;
        String scopeIds;
        if (promo.getCoupon() != null) {
            scope = promo.getCoupon().getServiceScope();
            scopeIds = promo.getCoupon().getScopeIds();
        } else if (promo.getOffer() != null) {
            scope = promo.getOffer().getServiceScope();
            scopeIds = promo.getOffer().getScopeIds();
        } else {
            return false;
        }
        return PromoScopeUtils.serviceEligible(scope, scopeIds, serviceId, categoryId);
    }

    public BigDecimal calculateDiscount(BigDecimal base, DiscountType type, BigDecimal value) {
        if (type == null || value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (type == DiscountType.FLAT) {
            return value.min(base);
        }
        return base.multiply(value).divide(BigDecimal.valueOf(100), SCALE, ROUNDING);
    }

    @Data
    @Builder
    public static class PromoContext {
        private MembershipSubscription membershipSubscription;
        private MembershipPlan membershipPlan;
        /** Plan queued for sale on this visit (discount preview + fee line). */
        private MembershipPlan pendingMembershipPlan;
        private Coupon coupon;
        private Offer offer;

        public static PromoContext empty() {
            return PromoContext.builder().build();
        }

        public DiscountType getPromoDiscountType() {
            if (coupon != null) {
                return coupon.getDiscountType();
            }
            if (offer != null) {
                return offer.getDiscountType();
            }
            return null;
        }

        public BigDecimal getPromoDiscountValue() {
            if (coupon != null) {
                return coupon.getDiscountValue();
            }
            if (offer != null) {
                return offer.getDiscountValue();
            }
            return null;
        }
    }

    @Data
    @Builder
    private static class LineScratch {
        private BookingLineItem line;
        private BigDecimal gross;
        private BigDecimal membershipDiscount;
        private BigDecimal afterManual;
        private boolean promoEligible;
    }
}
