package com.salonplatform.service;

import com.salonplatform.domain.entity.BookingLineItem;
import com.salonplatform.domain.entity.CustomerPackageSubscription;
import com.salonplatform.domain.entity.ServicePackagePlan;
import com.salonplatform.domain.enums.PackagePlanType;
import com.salonplatform.domain.repository.BranchServiceRepository;
import com.salonplatform.domain.repository.CustomerPackageSubscriptionRepository;
import com.salonplatform.domain.repository.ServicePackagePlanRepository;
import com.salonplatform.dto.billing.BillLinePreview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Imputes staff sale value for services redeemed from prepaid packages (not the package sale itself).
 */
@Service
@RequiredArgsConstructor
public class PackageStaffSaleImputationService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;

    private final CustomerPackageSubscriptionRepository subscriptionRepository;
    private final ServicePackagePlanRepository planRepository;
    private final BranchServiceRepository branchServiceRepository;

    public record ImputedLineAmounts(BigDecimal listAmount, BigDecimal finalAmount) {}

    /**
     * @return imputed amounts when line redeems a package; {@code null} for normal cash lines
     */
    public ImputedLineAmounts imputeForStaffSale(BookingLineItem line, BillLinePreview preview) {
        if (line.getPackageSubscriptionId() == null) {
            return null;
        }
        CustomerPackageSubscription sub = subscriptionRepository.findById(line.getPackageSubscriptionId())
                .orElse(null);
        if (sub == null) {
            return null;
        }
        ServicePackagePlan plan = planRepository.findById(sub.getPlanId()).orElse(null);
        PackagePlanType planType = effectivePlanType(sub, plan);

        int qty = line.getQuantity() != null && line.getQuantity() > 0 ? line.getQuantity() : 1;
        BigDecimal branchList = resolveBranchListPrice(line);
        BigDecimal catalogPreTax = branchList.multiply(BigDecimal.valueOf(qty)).setScale(SCALE, ROUND);
        BigDecimal factor = packageSaleDiscountFactor(sub, plan);

        BigDecimal consumptionBase;
        if (planType == PackagePlanType.VALUE_CREDIT) {
            if (preview != null && preview.getLineTotal() != null
                    && preview.getLineTotal().compareTo(BigDecimal.ZERO) > 0) {
                consumptionBase = preview.getLineTotal();
            } else {
                consumptionBase = grossWithGst(catalogPreTax, line.getGstRate());
            }
        } else {
            consumptionBase = grossWithGst(catalogPreTax, line.getGstRate());
        }

        BigDecimal finalAmount = consumptionBase.multiply(factor).setScale(SCALE, ROUND);
        return new ImputedLineAmounts(catalogPreTax, finalAmount);
    }

    /**
     * Prepaid discount ratio: amount customer paid ÷ reference value of the package (list total or credit).
     */
    public static BigDecimal packageSaleDiscountFactor(
            CustomerPackageSubscription sub,
            ServicePackagePlan plan) {
        BigDecimal paid = sub.getAmountPaid() != null ? sub.getAmountPaid() : BigDecimal.ZERO;
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        BigDecimal reference;
        if (effectivePlanType(sub, plan) == PackagePlanType.VALUE_CREDIT) {
            reference = sub.getCreditTotal() != null ? sub.getCreditTotal() : BigDecimal.ZERO;
            if (reference.compareTo(BigDecimal.ZERO) <= 0 && plan != null && plan.getCreditValue() != null) {
                reference = plan.getCreditValue();
            }
        } else {
            reference = plan != null && plan.getListPriceTotal() != null
                    ? plan.getListPriceTotal()
                    : BigDecimal.ZERO;
        }
        if (reference.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return paid.divide(reference, 6, ROUND).min(BigDecimal.ONE);
    }

    static BigDecimal grossWithGst(BigDecimal preTax, BigDecimal gstRatePercent) {
        if (preTax == null) {
            preTax = BigDecimal.ZERO;
        }
        if (gstRatePercent == null || gstRatePercent.compareTo(BigDecimal.ZERO) <= 0) {
            return preTax.setScale(SCALE, ROUND);
        }
        BigDecimal rate = gstRatePercent.divide(BigDecimal.valueOf(100), 6, ROUND);
        BigDecimal tax = preTax.multiply(rate).setScale(SCALE, ROUND);
        return preTax.add(tax).setScale(SCALE, ROUND);
    }

    static PackagePlanType effectivePlanType(CustomerPackageSubscription sub, ServicePackagePlan plan) {
        if (sub.getPlanType() == PackagePlanType.VALUE_CREDIT) {
            return PackagePlanType.VALUE_CREDIT;
        }
        if (sub.getCreditTotal() != null && sub.getCreditTotal().compareTo(BigDecimal.ZERO) > 0) {
            return PackagePlanType.VALUE_CREDIT;
        }
        if (plan != null && plan.getPlanType() == PackagePlanType.VALUE_CREDIT) {
            return PackagePlanType.VALUE_CREDIT;
        }
        return PackagePlanType.SERVICE_BUNDLE;
    }

    private BigDecimal resolveBranchListPrice(BookingLineItem line) {
        if (line.getBranchServiceId() != null) {
            return branchServiceRepository.findById(line.getBranchServiceId())
                    .map(bs -> bs.getPrice() != null ? bs.getPrice() : BigDecimal.ZERO)
                    .orElse(line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO);
        }
        return line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
    }
}
