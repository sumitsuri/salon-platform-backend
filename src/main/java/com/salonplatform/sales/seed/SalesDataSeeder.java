package com.salonplatform.sales.seed;

import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.sales.domain.entity.SalesIncentiveRule;
import com.salonplatform.sales.domain.entity.SalesLead;
import com.salonplatform.sales.domain.entity.SalesLocality;
import com.salonplatform.sales.domain.entity.SalesTarget;
import com.salonplatform.sales.domain.enums.BillingPeriod;
import com.salonplatform.sales.domain.enums.IncentiveEventType;
import com.salonplatform.sales.domain.repository.SalesIncentiveRuleRepository;
import com.salonplatform.sales.domain.repository.SalesLeadRepository;
import com.salonplatform.sales.domain.repository.SalesLocalityRepository;
import com.salonplatform.sales.domain.repository.SalesTargetRepository;
import com.salonplatform.sales.application.SalesPricingUtils;
import com.salonplatform.sales.dto.UpdateSalesLeadRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class SalesDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SalesLocalityRepository localityRepository;
    private final SalesIncentiveRuleRepository incentiveRuleRepository;
    private final SalesLeadRepository leadRepository;
    private final SalesTargetRepository targetRepository;

    private static final BigDecimal DEMO_QUOTED_MONTHLY = new BigDecimal("1000");
    private static final BigDecimal DISCOUNT_TWENTY_PCT = new BigDecimal("20");
    private static final BigDecimal DISCOUNT_FIFTEEN_PCT = new BigDecimal("15");

    @Override
    @Transactional
    public void run(String... args) {
        seedSalesReps();
        seedLocalities();
        seedIncentiveRules();
        backfillLeadLocalityIds();
        backfillMissingLeadLocalities();
        applyDemoLeadPricing();
        seedDemoTargets();
    }

    private void seedSalesReps() {
        if (userRepository.findByEmail("sales1@antrahq.local").isEmpty()) {
            userRepository.save(User.builder()
                    .name("Rajesh Kumar")
                    .email("sales1@antrahq.local")
                    .password(passwordEncoder.encode("sales123"))
                    .role(UserRole.SALES_EXECUTIVE)
                    .active(true)
                    .build());
            log.info("Seeded sales rep: sales1@antrahq.local / sales123");
        }
        if (userRepository.findByEmail("sales2@antrahq.local").isEmpty()) {
            userRepository.save(User.builder()
                    .name("Priya Sharma")
                    .email("sales2@antrahq.local")
                    .password(passwordEncoder.encode("sales123"))
                    .role(UserRole.SALES_EXECUTIVE)
                    .active(true)
                    .build());
            log.info("Seeded sales rep: sales2@antrahq.local / sales123");
        }
    }

    private void seedLocalities() {
        if (localityRepository.count() > 0) {
            return;
        }
        List<String[]> localities = List.of(
                new String[]{"Koramangala", "South"},
                new String[]{"Indiranagar", "East"},
                new String[]{"HSR Layout", "South"},
                new String[]{"Whitefield", "East"},
                new String[]{"Jayanagar", "South"},
                new String[]{"Malleshwaram", "North"},
                new String[]{"Rajajinagar", "West"},
                new String[]{"MG Road", "Central"},
                new String[]{"Electronic City", "South"},
                new String[]{"Hebbal", "North"},
                new String[]{"Marathahalli", "East"},
                new String[]{"Banashankari", "South"}
        );
        for (String[] loc : localities) {
            localityRepository.save(SalesLocality.builder()
                    .name(loc[0])
                    .zone(loc[1])
                    .active(true)
                    .build());
        }
        log.info("Seeded {} Bangalore localities", localities.size());
    }

    private void seedIncentiveRules() {
        if (incentiveRuleRepository.count() > 0) {
            return;
        }
        incentiveRuleRepository.save(SalesIncentiveRule.builder()
                .eventType(IncentiveEventType.FREE_TRIAL)
                .amountInr(new BigDecimal("500"))
                .active(true)
                .build());
        incentiveRuleRepository.save(SalesIncentiveRule.builder()
                .eventType(IncentiveEventType.WON)
                .amountInr(new BigDecimal("2000"))
                .active(true)
                .build());
        log.info("Seeded default sales incentive rules");
    }

    /** Link localityName text to localityId for leads created before locality picker. */
    private void backfillLeadLocalityIds() {
        Map<String, UUID> byName = localityRepository.findAll().stream()
                .collect(Collectors.toMap(
                        loc -> loc.getName().trim().toLowerCase(Locale.ROOT),
                        SalesLocality::getId,
                        (a, b) -> a));
        int updated = 0;
        for (SalesLead lead : leadRepository.findAll()) {
            if (lead.getLocalityId() != null || lead.getLocalityName() == null || lead.getLocalityName().isBlank()) {
                continue;
            }
            UUID id = byName.get(lead.getLocalityName().trim().toLowerCase(Locale.ROOT));
            if (id != null) {
                lead.setLocalityId(id);
                leadRepository.save(lead);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Backfilled localityId on {} sales leads", updated);
        }
    }

    /** Assign a Bangalore locality when both localityId and localityName are missing. */
    private void backfillMissingLeadLocalities() {
        List<SalesLocality> localities = localityRepository.findByActiveTrueOrderByZoneAscNameAsc();
        if (localities.isEmpty()) {
            return;
        }
        List<SalesLead> leads = leadRepository.findAll(Sort.by("createdAt", "id"));
        int updated = 0;
        for (int i = 0; i < leads.size(); i++) {
            SalesLead lead = leads.get(i);
            boolean missingLocality = lead.getLocalityId() == null
                    && (lead.getLocalityName() == null || lead.getLocalityName().isBlank());
            if (!missingLocality) {
                continue;
            }
            SalesLocality loc = localities.get(i % localities.size());
            lead.setLocalityId(loc.getId());
            lead.setLocalityName(loc.getName());
            leadRepository.save(lead);
            updated++;
        }
        if (updated > 0) {
            log.info("Assigned default locality to {} sales leads", updated);
        }
    }

    /** Demo pricing: ₹1000/mo quoted; 60% of leads @ 20% off, remainder @ 15% off. */
    private void applyDemoLeadPricing() {
        List<SalesLead> leads = leadRepository.findAll(Sort.by("createdAt", "id"));
        if (leads.isEmpty()) {
            return;
        }
        int splitAt = (int) Math.ceil(leads.size() * 0.6);
        for (int i = 0; i < leads.size(); i++) {
            SalesLead lead = leads.get(i);
            BigDecimal discountPct = i < splitAt ? DISCOUNT_TWENTY_PCT : DISCOUNT_FIFTEEN_PCT;
            UpdateSalesLeadRequest request = new UpdateSalesLeadRequest();
            request.setQuotedAmount(DEMO_QUOTED_MONTHLY);
            request.setBillingPeriod(BillingPeriod.MONTHLY);
            request.setDiscountPercent(discountPct);
            SalesPricingUtils.syncPricing(lead, request);
            leadRepository.save(lead);
        }
        log.info(
                "Applied demo lead pricing to {} leads ({} @ 20% off, {} @ 15% off, ₹1000/mo quoted)",
                leads.size(),
                splitAt,
                leads.size() - splitAt);
    }

    /** Ensure every active sales rep has weekly targets for the current week. */
    private void seedDemoTargets() {
        LocalDate week = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<User> reps = userRepository.findByRoleAndActiveTrue(UserRole.SALES_EXECUTIVE);
        if (reps.isEmpty()) {
            return;
        }
        int created = 0;
        for (User rep : reps) {
            if (targetRepository.findByRepIdAndWeekStartDate(rep.getId(), week).isPresent()) {
                continue;
            }
            targetRepository.save(SalesTarget.builder()
                    .repId(rep.getId())
                    .weekStartDate(week)
                    .targetLeads(10)
                    .targetVisits(15)
                    .targetPitches(8)
                    .targetTrials(3)
                    .targetConversions(1)
                    .build());
            created++;
        }
        if (created > 0) {
            log.info("Seeded weekly sales targets for {} reps (week of {})", created, week);
        }
    }
}
