package com.salonplatform.sales.application;

import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.sales.domain.entity.SalesLead;
import com.salonplatform.sales.domain.entity.SalesTarget;
import com.salonplatform.sales.domain.enums.ActivityType;
import com.salonplatform.sales.domain.enums.LeadStage;
import com.salonplatform.sales.domain.repository.SalesActivityRepository;
import com.salonplatform.sales.domain.repository.SalesLeadRepository;
import com.salonplatform.sales.domain.repository.SalesTargetRepository;
import com.salonplatform.sales.dto.*;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesRepService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SalesTargetRepository targetRepository;
    private final SalesLeadRepository leadRepository;
    private final SalesActivityRepository activityRepository;
    private final SalesIncentiveService incentiveService;

    @Transactional(readOnly = true)
    public List<SalesRepResponse> listReps(boolean includeInactive) {
        SecurityUtils.assertPlatformAdmin();
        List<User> users = includeInactive
                ? userRepository.findByRoleOrderByActiveDescNameAsc(UserRole.SALES_EXECUTIVE)
                : userRepository.findByRoleAndActiveTrue(UserRole.SALES_EXECUTIVE);
        return users.stream()
                .map(u -> SalesRepResponse.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .active(u.isActive())
                        .build())
                .toList();
    }

    @Transactional
    public SalesRepResponse createRep(CreateSalesRepRequest request) {
        SecurityUtils.assertPlatformAdmin();
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BadRequestException("Email already exists");
        }
        User user = userRepository.save(User.builder()
                .name(request.getName())
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.SALES_EXECUTIVE)
                .active(true)
                .build());
        return toRepResponse(user);
    }

    @Transactional
    public SalesRepResponse updateRep(UUID id, UpdateSalesRepRequest request) {
        SecurityUtils.assertPlatformAdmin();
        User user = requireRep(id);
        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName().trim());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = request.getEmail().trim().toLowerCase();
            userRepository.findByEmail(email).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BadRequestException("Email already exists");
                }
            });
            user.setEmail(email);
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }
        return toRepResponse(userRepository.save(user));
    }

    @Transactional
    public void deactivateRep(UUID id) {
        SecurityUtils.assertPlatformAdmin();
        User user = requireRep(id);
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public SalesTargetResponse upsertTarget(UpsertSalesTargetRequest request) {
        SecurityUtils.assertPlatformAdmin();
        requireRep(request.getRepId());
        SalesTarget target = targetRepository.findByRepIdAndWeekStartDate(request.getRepId(), request.getWeekStartDate())
                .orElse(SalesTarget.builder()
                        .repId(request.getRepId())
                        .weekStartDate(request.getWeekStartDate())
                        .build());
        target.setTargetLeads(request.getTargetLeads());
        target.setTargetVisits(request.getTargetVisits());
        target.setTargetPitches(request.getTargetPitches());
        target.setTargetTrials(request.getTargetTrials());
        target.setTargetConversions(request.getTargetConversions());
        SalesTarget saved = targetRepository.save(target);
        return enrichTarget(saved, null, null);
    }

    @Transactional(readOnly = true)
    public List<SalesTargetResponse> listTargets(LocalDate weekStart, LocalDate from, LocalDate to) {
        SecurityUtils.assertSalesAccess();
        LocalDate week = weekStart != null ? weekStart : SalesLeadService.currentWeekStart();
        Instant[] range = resolveRange(from, to, week);
        List<SalesTarget> targets = SecurityUtils.isSalesExecutive()
                ? targetRepository.findByRepId(SecurityUtils.currentUserId())
                : targetRepository.findByWeekStartDate(week);
        return targets.stream().map(t -> enrichTarget(t, range[0], range[1])).toList();
    }

    @Transactional(readOnly = true)
    public RepPerformanceResponse myPerformance(LocalDate weekStart, LocalDate from, LocalDate to) {
        SecurityUtils.assertSalesAccess();
        if (SecurityUtils.isSalesExecutive()) {
            LocalDate week = resolveTargetWeek(weekStart, from, to);
            Instant[] range = resolveRange(from, to, week);
            User rep = userRepository.findById(SecurityUtils.currentUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            return buildPerformance(rep, week, range[0], range[1]);
        }
        throw new BadRequestException("Use team analytics for admin view");
    }

    @Transactional(readOnly = true)
    public List<RepPerformanceResponse> repPerformance(LocalDate weekStart, LocalDate from, LocalDate to) {
        SecurityUtils.assertPlatformAdmin();
        LocalDate week = resolveTargetWeek(weekStart, from, to);
        Instant[] range = resolveRange(from, to, week);
        return userRepository.findByRoleAndActiveTrue(UserRole.SALES_EXECUTIVE).stream()
                .map(rep -> buildPerformance(rep, week, range[0], range[1]))
                .sorted(Comparator.comparing(RepPerformanceResponse::getRevenueWon).reversed())
                .toList();
    }

    private RepPerformanceResponse buildPerformance(User rep, LocalDate week, Instant start, Instant end) {
        UUID repId = rep.getId();
        int leads = (int) leadRepository.findByAssignedRepId(repId).stream()
                .filter(l -> l.getCreatedAt().isAfter(start) && l.getCreatedAt().isBefore(end))
                .count();
        int visits = activityRepository
                .findByRepIdAndActivityTypeAndCreatedAtBetween(repId, ActivityType.VISIT, start, end).size();
        int pitches = activityRepository
                .findByRepIdAndActivityTypeAndCreatedAtBetween(repId, ActivityType.PITCH, start, end).size();
        int trials = (int) leadRepository.findByAssignedRepId(repId).stream()
                .filter(l -> l.getStage() == LeadStage.FREE_TRIAL
                        && l.getCreatedAt().isAfter(start) && l.getCreatedAt().isBefore(end))
                .count();
        int conversions = (int) leadRepository.findByAssignedRepId(repId).stream()
                .filter(l -> l.getStage() == LeadStage.WON
                        && l.getConvertedAt() != null
                        && l.getConvertedAt().isAfter(start) && l.getConvertedAt().isBefore(end))
                .count();
        int lost = (int) leadRepository.findByAssignedRepId(repId).stream()
                .filter(l -> l.getStage() == LeadStage.LOST
                        && l.getUpdatedAt().isAfter(start) && l.getUpdatedAt().isBefore(end))
                .count();

        BigDecimal revenueWon = leadRepository.findByAssignedRepId(repId).stream()
                .filter(l -> l.getStage() == LeadStage.WON
                        && l.getConvertedAt() != null
                        && l.getConvertedAt().isAfter(start) && l.getConvertedAt().isBefore(end))
                .map(SalesRepService::monthlyRevenueOrZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SalesTarget target = targetRepository.findByRepIdAndWeekStartDate(repId, week).orElse(null);
        double achievement = 0;
        boolean under = false;
        if (target != null && target.getTargetConversions() > 0) {
            achievement = (conversions * 100.0) / target.getTargetConversions();
            under = achievement < 70;
        }

        BigDecimal incentive = incentiveService.totalForRepWeek(repId, week);

        return RepPerformanceResponse.builder()
                .repId(repId)
                .repName(rep.getName())
                .leadsAdded(leads)
                .visits(visits)
                .pitches(pitches)
                .trials(trials)
                .conversions(conversions)
                .lost(lost)
                .revenueWon(revenueWon)
                .incentiveEarned(incentive)
                .targetAchievementPercent(achievement)
                .underperforming(under)
                .build();
    }

    private SalesTargetResponse enrichTarget(SalesTarget target, Instant startOverride, Instant endOverride) {
        String repName = userRepository.findById(target.getRepId()).map(User::getName).orElse(null);
        LocalDate week = target.getWeekStartDate();
        Instant[] range = startOverride != null && endOverride != null
                ? new Instant[] { startOverride, endOverride }
                : resolveRange(null, null, week);
        Instant start = range[0];
        Instant end = range[1];
        UUID repId = target.getRepId();

        return SalesTargetResponse.builder()
                .id(target.getId())
                .repId(repId)
                .repName(repName)
                .weekStartDate(week)
                .targetLeads(target.getTargetLeads())
                .targetVisits(target.getTargetVisits())
                .targetPitches(target.getTargetPitches())
                .targetTrials(target.getTargetTrials())
                .targetConversions(target.getTargetConversions())
                .actualLeads((int) leadRepository.findByAssignedRepId(repId).stream()
                        .filter(l -> l.getCreatedAt().isAfter(start) && l.getCreatedAt().isBefore(end)).count())
                .actualVisits(activityRepository
                        .findByRepIdAndActivityTypeAndCreatedAtBetween(repId, ActivityType.VISIT, start, end).size())
                .actualPitches(activityRepository
                        .findByRepIdAndActivityTypeAndCreatedAtBetween(repId, ActivityType.PITCH, start, end).size())
                .actualTrials((int) leadRepository.findByAssignedRepId(repId).stream()
                        .filter(l -> l.getStage() == LeadStage.FREE_TRIAL
                                && l.getCreatedAt().isAfter(start) && l.getCreatedAt().isBefore(end))
                        .count())
                .actualConversions((int) leadRepository.findByAssignedRepId(repId).stream()
                        .filter(l -> l.getStage() == LeadStage.WON
                                && l.getConvertedAt() != null
                                && l.getConvertedAt().isAfter(start) && l.getConvertedAt().isBefore(end))
                        .count())
                .build();
    }

    private static LocalDate resolveTargetWeek(LocalDate weekStart, LocalDate from, LocalDate to) {
        if (weekStart != null) {
            return weekStart;
        }
        if (to != null) {
            return weekStartContaining(to);
        }
        if (from != null) {
            return weekStartContaining(from);
        }
        return SalesLeadService.currentWeekStart();
    }

    private static Instant[] resolveRange(LocalDate from, LocalDate to, LocalDate weekFallback) {
        ZoneId zone = ZoneId.systemDefault();
        if (from != null && to != null) {
            return new Instant[] {
                    from.atStartOfDay(zone).toInstant(),
                    to.plusDays(1).atStartOfDay(zone).toInstant()
            };
        }
        LocalDate week = weekFallback != null ? weekFallback : SalesLeadService.currentWeekStart();
        return new Instant[] {
                week.atStartOfDay(zone).toInstant(),
                week.plusDays(7).atStartOfDay(zone).toInstant()
        };
    }

    static LocalDate weekStartContaining(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private User requireRep(UUID repId) {
        User user = userRepository.findById(repId)
                .orElseThrow(() -> new ResourceNotFoundException("Rep not found"));
        if (user.getRole() != UserRole.SALES_EXECUTIVE) {
            throw new BadRequestException("User is not a sales executive");
        }
        return user;
    }

    private SalesRepResponse toRepResponse(User user) {
        return SalesRepResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .active(user.isActive())
                .build();
    }

    private static BigDecimal monthlyRevenueOrZero(SalesLead lead) {
        BigDecimal mrr = SalesPricingUtils.monthlyRevenue(lead);
        if (mrr != null) {
            return mrr;
        }
        if (lead.getProjectedMrr() != null) {
            return lead.getProjectedMrr();
        }
        return BigDecimal.ZERO;
    }
}
