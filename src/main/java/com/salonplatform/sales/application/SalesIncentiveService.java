package com.salonplatform.sales.application;

import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.sales.domain.entity.SalesIncentiveLedger;
import com.salonplatform.sales.domain.entity.SalesIncentiveRule;
import com.salonplatform.sales.domain.entity.SalesLead;
import com.salonplatform.sales.domain.enums.IncentiveEventType;
import com.salonplatform.sales.domain.repository.SalesIncentiveLedgerRepository;
import com.salonplatform.sales.domain.repository.SalesIncentiveRuleRepository;
import com.salonplatform.sales.dto.IncentiveRuleResponse;
import com.salonplatform.sales.dto.UpsertIncentiveRuleRequest;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesIncentiveService {

    private final SalesIncentiveRuleRepository ruleRepository;
    private final SalesIncentiveLedgerRepository ledgerRepository;

    @Transactional(readOnly = true)
    public List<IncentiveRuleResponse> listRules() {
        SecurityUtils.assertPlatformAdmin();
        return ruleRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public IncentiveRuleResponse upsertRule(UpsertIncentiveRuleRequest request) {
        SecurityUtils.assertPlatformAdmin();
        SalesIncentiveRule rule = ruleRepository.findByEventTypeAndActiveTrue(request.getEventType())
                .stream().findFirst()
                .orElse(SalesIncentiveRule.builder().eventType(request.getEventType()).build());
        rule.setAmountInr(request.getAmountInr());
        rule.setActive(request.isActive());
        return toResponse(ruleRepository.save(rule));
    }

    @Transactional
    public void recordIncentiveIfEligible(SalesLead lead, IncentiveEventType eventType) {
        if (lead.getAssignedRepId() == null) {
            return;
        }
        if (ledgerRepository.existsByLeadIdAndEventType(lead.getId(), eventType)) {
            return;
        }
        BigDecimal amount = ruleRepository.findByEventTypeAndActiveTrue(eventType).stream()
                .findFirst()
                .map(SalesIncentiveRule::getAmountInr)
                .orElse(defaultAmount(eventType));
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        ledgerRepository.save(SalesIncentiveLedger.builder()
                .repId(lead.getAssignedRepId())
                .leadId(lead.getId())
                .eventType(eventType)
                .amountInr(amount)
                .weekStartDate(SalesLeadService.currentWeekStart())
                .build());
    }

    @Transactional(readOnly = true)
    public BigDecimal totalForRepWeek(UUID repId, java.time.LocalDate weekStart) {
        return ledgerRepository.findByRepIdAndWeekStartDate(repId, weekStart).stream()
                .map(SalesIncentiveLedger::getAmountInr)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal defaultAmount(IncentiveEventType type) {
        return switch (type) {
            case FREE_TRIAL -> new BigDecimal("500");
            case WON -> new BigDecimal("2000");
        };
    }

    private IncentiveRuleResponse toResponse(SalesIncentiveRule rule) {
        return IncentiveRuleResponse.builder()
                .id(rule.getId())
                .eventType(rule.getEventType())
                .amountInr(rule.getAmountInr())
                .active(rule.isActive())
                .build();
    }
}
