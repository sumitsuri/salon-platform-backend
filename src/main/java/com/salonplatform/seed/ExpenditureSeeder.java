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
        LocalDate month = LocalDate.now(ZONE).withDayOfMonth(1);
        for (String slug : SeedCatalog.slugs()) {
            tenantRepository.findBySlug(slug).ifPresent(tenant -> seedTenant(tenant, month));
        }
    }

    private void seedTenant(Tenant tenant, LocalDate month) {
        List<Branch> branches = branchRepository.findByTenantId(tenant.getId());
        int seeded = 0;

        for (Branch branch : branches) {
            boolean alreadySeeded = expenditureRepository
                    .findByTenantIdAndBranchIdAndActiveTrueOrderByExpenseMonthDesc(tenant.getId(), branch.getId())
                    .stream()
                    .anyMatch(e -> e.getExpenseMonth().equals(month));
            if (alreadySeeded) continue;

            boolean flagship = branch.getCode().equals("LIT") || branch.getCode().equals("IND");
            seed(branch, month, ExpenditureCategory.EMPLOYEE_SALARY,
                    flagship ? "185000" : "142000", "Monthly staff payroll");
            seed(branch, month, ExpenditureCategory.RENT,
                    flagship ? "85000" : "72000", "Shop rent");
            seed(branch, month, ExpenditureCategory.PRODUCT_COST,
                    flagship ? "42000" : "35000", "Consumables & retail stock");
            seed(branch, month, ExpenditureCategory.EMPLOYEE_ACCOMMODATION_RENT,
                    flagship ? "28000" : "22000", "Staff housing");
            seed(branch, month, ExpenditureCategory.MISCELLANEOUS,
                    flagship ? "12000" : "9500", "Utilities, marketing, misc");
            seeded++;
        }

        if (seeded > 0) {
            log.info("Seeded expenditures for '{}' on {} ({} branch(es))", tenant.getSlug(), month, seeded);
        }
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
