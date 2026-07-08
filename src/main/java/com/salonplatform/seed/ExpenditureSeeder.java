package com.salonplatform.seed;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.BranchExpenditure;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.enums.ExpenditureCategory;
import com.salonplatform.domain.repository.BranchExpenditureRepository;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class ExpenditureSeeder implements CommandLineRunner {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final BranchExpenditureRepository expenditureRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Tenant tenant = tenantRepository.findBySlug("demo-brand").orElse(null);
        if (tenant == null) return;

        long existing = expenditureRepository.findByTenantIdAndActiveTrueOrderByExpenseMonthDesc(tenant.getId()).size();
        if (existing > 0) return;

        List<Branch> branches = branchRepository.findByTenantId(tenant.getId());
        LocalDate month = LocalDate.now(ZONE).withDayOfMonth(1);

        for (Branch branch : branches) {
            boolean isLithos = branch.getCode().equals("LIT");
            seed(branch, month, ExpenditureCategory.EMPLOYEE_SALARY,
                    isLithos ? "185000" : "142000", "Monthly staff payroll");
            seed(branch, month, ExpenditureCategory.RENT,
                    isLithos ? "85000" : "72000", "Shop rent");
            seed(branch, month, ExpenditureCategory.PRODUCT_COST,
                    isLithos ? "42000" : "35000", "Consumables & retail stock");
            seed(branch, month, ExpenditureCategory.EMPLOYEE_ACCOMMODATION_RENT,
                    isLithos ? "28000" : "22000", "Staff housing");
            seed(branch, month, ExpenditureCategory.MISCELLANEOUS,
                    isLithos ? "12000" : "9500", "Utilities, marketing, misc");
        }

        log.info("Seeded demo branch expenditures for {}", month);
    }

    private void seed(Branch branch, LocalDate month, ExpenditureCategory category,
                      String amount, String description) {
        expenditureRepository.save(BranchExpenditure.builder()
                .tenantId(branch.getTenantId())
                .branchId(branch.getId())
                .category(category)
                .expenseMonth(month)
                .amount(new BigDecimal(amount))
                .description(description)
                .active(true)
                .build());
    }
}
