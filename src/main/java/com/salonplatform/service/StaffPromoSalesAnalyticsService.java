package com.salonplatform.service;

import com.salonplatform.domain.entity.CustomerPackageSubscription;
import com.salonplatform.domain.entity.MembershipSubscription;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.repository.CustomerPackageSubscriptionRepository;
import com.salonplatform.domain.repository.MembershipSubscriptionRepository;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.dto.analytics.StaffPromoSalesResponse;
import com.salonplatform.exception.ForbiddenException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffPromoSalesAnalyticsService {

    public static final BigDecimal MEMBERSHIP_INCENTIVE_INR = new BigDecimal("50");
    public static final BigDecimal PACKAGE_INCENTIVE_PERCENT = new BigDecimal("3");
    public static final BigDecimal PACKAGE_INCENTIVE_MAX_INR = new BigDecimal("500");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final MembershipSubscriptionRepository membershipSubscriptionRepository;
    private final CustomerPackageSubscriptionRepository packageSubscriptionRepository;
    private final StaffRepository staffRepository;

    public StaffPromoSalesResponse getStaffPromoSales(List<UUID> branchIds, LocalDate today) {
        UserPrincipal user = SecurityUtils.currentUser();
        UUID tenantId = SecurityUtils.requireTenantId();
        List<UUID> resolvedBranchIds = resolveBranchIds(user, branchIds);
        LocalDate day = today != null ? today : LocalDate.now(IST);

        List<MembershipSubscription> memberships = membershipSubscriptionRepository
                .findByTenantIdAndSoldByStaffIdIsNotNull(tenantId);
        List<CustomerPackageSubscription> packages = packageSubscriptionRepository
                .findByTenantIdAndSoldByStaffIdIsNotNull(tenantId);

        Map<UUID, MutableAgg> byStaff = new HashMap<>();

        for (MembershipSubscription sub : memberships) {
            if (sub.getSoldByStaffId() == null || !branchAllowed(sub.getBranchId(), resolvedBranchIds)) {
                continue;
            }
            MutableAgg agg = byStaff.computeIfAbsent(sub.getSoldByStaffId(), k -> new MutableAgg());
            agg.membershipTotal++;
            if (isOnDay(sub.getCreatedAt(), day)) {
                agg.membershipToday++;
            }
        }

        for (CustomerPackageSubscription sub : packages) {
            if (sub.getSoldByStaffId() == null || !branchAllowed(sub.getBranchId(), resolvedBranchIds)) {
                continue;
            }
            MutableAgg agg = byStaff.computeIfAbsent(sub.getSoldByStaffId(), k -> new MutableAgg());
            BigDecimal inc = packageIncentiveForAmount(sub.getAmountPaid());
            agg.packageIncentiveTotal = agg.packageIncentiveTotal.add(inc);
            agg.packageTotal++;
            if (isOnDay(sub.getCreatedAt(), day)) {
                agg.packageToday++;
                agg.packageIncentiveToday = agg.packageIncentiveToday.add(inc);
            }
        }

        List<Staff> roster = loadRoster(tenantId, resolvedBranchIds);
        for (Staff member : roster) {
            byStaff.putIfAbsent(member.getId(), new MutableAgg());
        }

        List<StaffPromoSalesResponse.StaffPromoSalesRow> rows = roster.stream()
                .map(member -> {
                    UUID staffId = member.getId();
                    MutableAgg agg = byStaff.getOrDefault(staffId, new MutableAgg());
                    BigDecimal membershipTodayEarnings = MEMBERSHIP_INCENTIVE_INR.multiply(
                            BigDecimal.valueOf(agg.membershipToday));
                    BigDecimal membershipTotalEarnings = MEMBERSHIP_INCENTIVE_INR.multiply(
                            BigDecimal.valueOf(agg.membershipTotal));
                    BigDecimal packageTodayEarnings = agg.packageIncentiveToday;
                    BigDecimal packageTotalEarnings = agg.packageIncentiveTotal;
                    BigDecimal todayEarnings = membershipTodayEarnings.add(packageTodayEarnings);
                    BigDecimal totalEarnings = membershipTotalEarnings.add(packageTotalEarnings);
                    return StaffPromoSalesResponse.StaffPromoSalesRow.builder()
                            .staffId(staffId)
                            .staffName(member.getName())
                            .membershipCountToday(agg.membershipToday)
                            .packageCountToday(agg.packageToday)
                            .membershipTodayEarnings(membershipTodayEarnings)
                            .membershipTotalEarnings(membershipTotalEarnings)
                            .packageTodayEarnings(packageTodayEarnings)
                            .packageTotalEarnings(packageTotalEarnings)
                            .todayEarnings(todayEarnings)
                            .membershipCountTotal(agg.membershipTotal)
                            .packageCountTotal(agg.packageTotal)
                            .totalEarnings(totalEarnings)
                            .build();
                })
                .sorted(Comparator.comparing(StaffPromoSalesResponse.StaffPromoSalesRow::getTotalEarnings).reversed()
                        .thenComparing(StaffPromoSalesResponse.StaffPromoSalesRow::getTodayEarnings).reversed()
                        .thenComparing(StaffPromoSalesResponse.StaffPromoSalesRow::getStaffName))
                .collect(Collectors.toList());

        return StaffPromoSalesResponse.builder()
                .membershipIncentivePerSale(MEMBERSHIP_INCENTIVE_INR)
                .packageIncentivePercent(PACKAGE_INCENTIVE_PERCENT)
                .staff(rows)
                .build();
    }

    static BigDecimal packageIncentiveForAmount(BigDecimal amountPaid) {
        if (amountPaid == null || amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return amountPaid.multiply(PACKAGE_INCENTIVE_PERCENT)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                .min(PACKAGE_INCENTIVE_MAX_INR);
    }

    private static boolean isOnDay(Instant instant, LocalDate day) {
        if (instant == null) {
            return false;
        }
        return instant.atZone(IST).toLocalDate().equals(day);
    }

    private static boolean branchAllowed(UUID branchId, List<UUID> resolvedBranchIds) {
        if (resolvedBranchIds == null || resolvedBranchIds.isEmpty()) {
            return true;
        }
        return resolvedBranchIds.contains(branchId);
    }

    private List<UUID> resolveBranchIds(UserPrincipal user, List<UUID> branchIds) {
        if (SecurityUtils.isManagerRole()) {
            if (user.getBranchId() == null) {
                throw new ForbiddenException("Branch context required");
            }
            SecurityUtils.assertBranchAccess(user.getBranchId());
            return List.of(user.getBranchId());
        }
        SecurityUtils.assertBrandAdminOrAbove();
        return branchIds;
    }

    private List<Staff> loadRoster(UUID tenantId, List<UUID> resolvedBranchIds) {
        if (resolvedBranchIds != null && resolvedBranchIds.size() == 1) {
            return staffRepository.findByTenantIdAndBranchIdAndActiveTrue(tenantId, resolvedBranchIds.get(0));
        }
        if (resolvedBranchIds != null && !resolvedBranchIds.isEmpty()) {
            List<Staff> merged = new ArrayList<>();
            for (UUID branchId : resolvedBranchIds) {
                merged.addAll(staffRepository.findByTenantIdAndBranchIdAndActiveTrue(tenantId, branchId));
            }
            return merged;
        }
        return staffRepository.findByTenantId(tenantId).stream().filter(Staff::isActive).toList();
    }

    private static final class MutableAgg {
        int membershipToday;
        int membershipTotal;
        int packageToday;
        int packageTotal;
        BigDecimal packageIncentiveToday = BigDecimal.ZERO;
        BigDecimal packageIncentiveTotal = BigDecimal.ZERO;
    }
}
