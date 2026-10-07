package com.salonplatform.service;

import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.entity.StaffGoal;
import com.salonplatform.domain.entity.StaffPerformanceReview;
import com.salonplatform.domain.enums.StaffGoalStatus;
import com.salonplatform.domain.repository.StaffGoalRepository;
import com.salonplatform.domain.repository.StaffPerformanceReviewRepository;
import com.salonplatform.dto.staff.CreateStaffGoalRequest;
import com.salonplatform.dto.staff.CreateStaffPerformanceReviewRequest;
import com.salonplatform.dto.staffportal.StaffGoalResponse;
import com.salonplatform.dto.staffportal.StaffPerformanceReviewResponse;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffGrowthManagementService {

    private final StaffAccessService staffAccessService;
    private final StaffGoalRepository goalRepository;
    private final StaffPerformanceReviewRepository reviewRepository;

    @Transactional
    public StaffGoalResponse createGoal(UUID staffId, CreateStaffGoalRequest request) {
        Staff staff = staffAccessService.requireStaffInTenant(staffId);
        staffAccessService.assertCanViewStaff(staff);
        if (SecurityUtils.isSalonStaff()) {
            throw new com.salonplatform.exception.ForbiddenException("Only managers can assign goals");
        }
        SecurityUtils.assertManagerOrBrandAdmin();

        StaffGoal goal = goalRepository.save(StaffGoal.builder()
                .tenantId(staff.getTenantId())
                .staffId(staff.getId())
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .metricUnit(request.getMetricUnit())
                .targetValue(request.getTargetValue())
                .currentValue(request.getCurrentValue() != null ? request.getCurrentValue() : BigDecimal.ZERO)
                .periodStart(request.getPeriodStart())
                .periodEnd(request.getPeriodEnd())
                .status(StaffGoalStatus.ACTIVE)
                .createdByUserId(SecurityUtils.currentUserId())
                .build());
        return toGoalResponse(goal);
    }

    @Transactional
    public StaffPerformanceReviewResponse createReview(UUID staffId, CreateStaffPerformanceReviewRequest request) {
        Staff staff = staffAccessService.requireStaffInTenant(staffId);
        staffAccessService.assertCanViewStaff(staff);
        if (SecurityUtils.isSalonStaff()) {
            throw new com.salonplatform.exception.ForbiddenException("Only managers can publish reviews");
        }
        SecurityUtils.assertManagerOrBrandAdmin();

        StaffPerformanceReview review = reviewRepository.save(StaffPerformanceReview.builder()
                .tenantId(staff.getTenantId())
                .staffId(staff.getId())
                .periodLabel(request.getPeriodLabel().trim())
                .reviewDate(request.getReviewDate())
                .overallRating(request.getOverallRating())
                .strengths(request.getStrengths())
                .improvements(request.getImprovements())
                .managerNotes(request.getManagerNotes())
                .attendanceScore(request.getAttendanceScore())
                .salesAchievementPercent(request.getSalesAchievementPercent())
                .visibleToStaff(request.getVisibleToStaff() == null || request.getVisibleToStaff())
                .reviewedByUserId(SecurityUtils.currentUserId())
                .build());
        return toReviewResponse(review);
    }

    public List<StaffGoalResponse> listGoals(UUID staffId) {
        Staff staff = staffAccessService.requireStaffInTenant(staffId);
        staffAccessService.assertCanViewStaff(staff);
        return goalRepository.findByTenantIdAndStaffIdOrderByPeriodEndDescCreatedAtDesc(
                        staff.getTenantId(), staff.getId()).stream()
                .map(this::toGoalResponse)
                .collect(Collectors.toList());
    }

    public List<StaffPerformanceReviewResponse> listReviews(UUID staffId, boolean staffView) {
        Staff staff = staffAccessService.requireStaffInTenant(staffId);
        staffAccessService.assertCanViewStaff(staff);
        List<StaffPerformanceReview> reviews;
        if (staffView || SecurityUtils.isSalonStaff()) {
            reviews = reviewRepository.findByTenantIdAndStaffIdAndVisibleToStaffTrueOrderByReviewDateDescCreatedAtDesc(
                    staff.getTenantId(), staff.getId());
        } else {
            reviews = reviewRepository.findByTenantIdAndStaffIdOrderByReviewDateDescCreatedAtDesc(
                    staff.getTenantId(), staff.getId());
        }
        return reviews.stream().map(this::toReviewResponse).collect(Collectors.toList());
    }

    StaffGoalResponse toGoalResponse(StaffGoal goal) {
        BigDecimal progress = BigDecimal.ZERO;
        if (goal.getTargetValue() != null && goal.getTargetValue().compareTo(BigDecimal.ZERO) > 0
                && goal.getCurrentValue() != null) {
            progress = goal.getCurrentValue()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(goal.getTargetValue(), 1, RoundingMode.HALF_UP);
        }
        return StaffGoalResponse.builder()
                .id(goal.getId())
                .title(goal.getTitle())
                .description(goal.getDescription())
                .metricUnit(goal.getMetricUnit())
                .targetValue(goal.getTargetValue())
                .currentValue(goal.getCurrentValue())
                .periodStart(goal.getPeriodStart())
                .periodEnd(goal.getPeriodEnd())
                .status(goal.getStatus())
                .progressPercent(progress)
                .build();
    }

    StaffPerformanceReviewResponse toReviewResponse(StaffPerformanceReview review) {
        return StaffPerformanceReviewResponse.builder()
                .id(review.getId())
                .periodLabel(review.getPeriodLabel())
                .reviewDate(review.getReviewDate())
                .overallRating(review.getOverallRating())
                .strengths(review.getStrengths())
                .improvements(review.getImprovements())
                .managerNotes(review.getManagerNotes())
                .attendanceScore(review.getAttendanceScore())
                .salesAchievementPercent(review.getSalesAchievementPercent())
                .build();
    }
}
