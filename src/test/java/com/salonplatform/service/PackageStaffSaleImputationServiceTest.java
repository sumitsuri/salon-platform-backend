package com.salonplatform.service;

import com.salonplatform.domain.entity.CustomerPackageSubscription;
import com.salonplatform.domain.entity.ServicePackagePlan;
import com.salonplatform.domain.enums.PackagePlanType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackageStaffSaleImputationServiceTest {

    @Test
    void bundleDiscountFactorUsesListTotalAndAmountPaid() {
        CustomerPackageSubscription sub = CustomerPackageSubscription.builder()
                .amountPaid(new BigDecimal("6000"))
                .planType(PackagePlanType.SERVICE_BUNDLE)
                .build();
        ServicePackagePlan plan = ServicePackagePlan.builder()
                .listPriceTotal(new BigDecimal("10000"))
                .planType(PackagePlanType.SERVICE_BUNDLE)
                .build();
        assertEquals(0, new BigDecimal("0.6")
                .compareTo(PackageStaffSaleImputationService.packageSaleDiscountFactor(sub, plan)));
    }

    @Test
    void valueCreditDiscountFactorUsesCreditTotal() {
        CustomerPackageSubscription sub = CustomerPackageSubscription.builder()
                .amountPaid(new BigDecimal("10000"))
                .creditTotal(new BigDecimal("13000"))
                .planType(PackagePlanType.VALUE_CREDIT)
                .build();
        ServicePackagePlan plan = ServicePackagePlan.builder()
                .creditValue(new BigDecimal("13000"))
                .planType(PackagePlanType.VALUE_CREDIT)
                .build();
        assertEquals(0, new BigDecimal("10000")
                .divide(new BigDecimal("13000"), 6, java.math.RoundingMode.HALF_UP)
                .compareTo(PackageStaffSaleImputationService.packageSaleDiscountFactor(sub, plan)));
    }

    @Test
    void grossWithGstAddsCgstSgst() {
        BigDecimal gross = PackageStaffSaleImputationService.grossWithGst(
                new BigDecimal("1000"), new BigDecimal("18"));
        assertEquals(0, new BigDecimal("1180.00").compareTo(gross));
    }
}
