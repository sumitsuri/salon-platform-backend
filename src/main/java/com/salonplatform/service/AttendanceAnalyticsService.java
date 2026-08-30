package com.salonplatform.service;

import com.salonplatform.domain.entity.AttendanceRecord;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.LeaveRecord;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.enums.LeaveStatus;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.analytics.*;
import com.salonplatform.dto.attendance.AttendanceResponse;
import com.salonplatform.dto.leave.LeaveResponse;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceAnalyticsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final AttendanceRecordRepository attendanceRepository;
    private final LeaveRecordRepository leaveRepository;
    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;
    private final AttendanceService attendanceService;
    private final LeaveService leaveService;

    public AttendanceDashboardResponse getDashboard(LocalDate startDate, LocalDate endDate, List<UUID> branchIds) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBrandAdminOrAbove();

        LocalDate start = startDate != null ? startDate : LocalDate.now(ZONE).minusDays(59);
        LocalDate end = endDate != null ? endDate : LocalDate.now(ZONE);
        LocalDate today = LocalDate.now(ZONE);

        Set<UUID> branchFilter = branchIds != null && !branchIds.isEmpty()
                ? new HashSet<>(branchIds) : null;

        List<Staff> allStaff = staffRepository.findByTenantId(tenantId).stream()
                .filter(Staff::isActive)
                .filter(s -> branchFilter == null || branchFilter.contains(s.getBranchId()))
                .collect(Collectors.toList());

        List<AttendanceRecord> records = branchFilter != null
                ? attendanceRepository.findByTenantIdAndBranchIdInAndWorkDateBetween(tenantId, new ArrayList<>(branchFilter), start, end)
                : attendanceRepository.findByTenantIdAndWorkDateBetweenOrderByEntryTimeDesc(tenantId, start, end);

        List<LeaveRecord> leaves = branchFilter != null
                ? leaveRepository.findByTenantIdAndBranchIdInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        tenantId, new ArrayList<>(branchFilter), end, start)
                : leaveRepository.findByTenantIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(tenantId, end, start);

        List<LeaveRecord> approvedLeaves = leaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .collect(Collectors.toList());

        Set<UUID> presentToday = records.stream()
                .filter(r -> r.getWorkDate().equals(today) && r.getEntryTime() != null)
                .map(AttendanceRecord::getStaffId)
                .collect(Collectors.toSet());

        long onLeaveToday = allStaff.stream()
                .filter(s -> isOnLeave(s.getId(), approvedLeaves, today))
                .count();

        long absentToday = allStaff.size() - presentToday.size() - onLeaveToday;
        if (absentToday < 0) absentToday = 0;

        List<DailyAttendanceTrend> dailyTrends = buildDailyTrends(start, end, records, approvedLeaves, allStaff.size());

        Map<UUID, List<AttendanceRecord>> byStaff = records.stream()
                .collect(Collectors.groupingBy(AttendanceRecord::getStaffId));

        Map<UUID, Long> leaveDaysByStaff = new HashMap<>();
        for (Staff staff : allStaff) {
            long days = countLeaveDays(staff.getId(), approvedLeaves, start, end);
            leaveDaysByStaff.put(staff.getId(), days);
        }

        List<StaffAttendanceSummary> staffSummaries = allStaff.stream().map(staff -> {
            List<AttendanceRecord> staffRecords = byStaff.getOrDefault(staff.getId(), List.of());
            Branch branch = branchRepository.findById(staff.getBranchId()).orElse(null);
            long daysPresent = staffRecords.stream().filter(r -> r.getEntryTime() != null).count();
            long absentDays = countAbsentDays(staff.getId(), staffRecords, approvedLeaves, start, end);
            double totalHours = staffRecords.stream()
                    .map(AttendanceService::computeHours)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();
            long lateArrivals = staffRecords.stream().filter(r -> AttendanceService.isLate(r, branch)).count();
            long earlyExits = staffRecords.stream().filter(r -> AttendanceService.isEarlyExit(r, branch)).count();
            long geoFlags = staffRecords.stream().filter(AttendanceService::hasGeoFlag).count();
            BigDecimal avgHours = daysPresent > 0
                    ? BigDecimal.valueOf(totalHours / daysPresent).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal performanceScore = computePerformanceScore(daysPresent, leaveDaysByStaff.getOrDefault(staff.getId(), 0L),
                    lateArrivals, avgHours, start, end);
            int complianceScore = AttendanceService.computeComplianceScore(
                    daysPresent, absentDays, lateArrivals, earlyExits, geoFlags);

            String branchName = branch != null ? branch.getName() : "";

            Optional<AttendanceRecord> latestRecord = staffRecords.stream()
                    .max(Comparator.comparing(AttendanceRecord::getWorkDate)
                            .thenComparing(r -> r.getEntryTime() != null ? r.getEntryTime() : Instant.EPOCH));

            return StaffAttendanceSummary.builder()
                    .staffId(staff.getId().toString())
                    .staffName(staff.getName())
                    .branchName(branchName)
                    .daysPresent(daysPresent)
                    .daysLeave(leaveDaysByStaff.getOrDefault(staff.getId(), 0L))
                    .totalHours(BigDecimal.valueOf(totalHours).setScale(1, RoundingMode.HALF_UP))
                    .avgHoursPerDay(avgHours)
                    .lateArrivals(lateArrivals)
                    .earlyExits(earlyExits)
                    .geoFlags(geoFlags)
                    .performanceScore(performanceScore)
                    .complianceScore(complianceScore)
                    .attendanceRecordId(latestRecord.map(r -> r.getId().toString()).orElse(null))
                    .entryTime(latestRecord.map(AttendanceRecord::getEntryTime).orElse(null))
                    .exitTime(latestRecord.map(AttendanceRecord::getExitTime).orElse(null))
                    .hasEntryPhoto(latestRecord.map(r -> r.getEntryPhotoKey() != null && !r.getEntryPhotoKey().isBlank()).orElse(false))
                    .hasExitPhoto(latestRecord.map(r -> r.getExitPhotoKey() != null && !r.getExitPhotoKey().isBlank()).orElse(false))
                    .build();
        }).sorted(Comparator.comparing(StaffAttendanceSummary::getComplianceScore).reversed())
                .collect(Collectors.toList());

        double avgHoursAll = staffSummaries.stream()
                .map(StaffAttendanceSummary::getAvgHoursPerDay)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);

        List<AttendanceResponse> recentRecords = records.stream()
                .sorted(Comparator.comparing(AttendanceRecord::getWorkDate).reversed()
                        .thenComparing(r -> r.getEntryTime() != null ? r.getEntryTime() : java.time.Instant.EPOCH))
                .limit(20)
                .map(r -> staffRepository.findById(r.getStaffId())
                        .map(s -> attendanceService.toResponse(r, s))
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<LeaveResponse> leaveResponses = approvedLeaves.stream()
                .map(l -> staffRepository.findById(l.getStaffId())
                        .map(s -> leaveService.toResponse(l, s))
                        .orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(LeaveResponse::getStartDate).reversed())
                .limit(15)
                .collect(Collectors.toList());

        return AttendanceDashboardResponse.builder()
                .totalStaff(allStaff.size())
                .presentToday(presentToday.size())
                .onLeaveToday(onLeaveToday)
                .absentToday(absentToday)
                .avgHoursPerStaff(BigDecimal.valueOf(avgHoursAll).setScale(1, RoundingMode.HALF_UP))
                .dailyTrends(dailyTrends)
                .staffSummaries(staffSummaries)
                .recentRecords(recentRecords)
                .leaves(leaveResponses)
                .build();
    }

    private List<DailyAttendanceTrend> buildDailyTrends(LocalDate start, LocalDate end,
                                                        List<AttendanceRecord> records,
                                                        List<LeaveRecord> approvedLeaves,
                                                        int totalStaff) {
        List<DailyAttendanceTrend> trends = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            LocalDate day = d;
            long present = records.stream()
                    .filter(r -> r.getWorkDate().equals(day) && r.getEntryTime() != null)
                    .map(AttendanceRecord::getStaffId)
                    .distinct()
                    .count();
            long onLeave = approvedLeaves.stream()
                    .filter(l -> !l.getStartDate().isAfter(day) && !l.getEndDate().isBefore(day))
                    .map(LeaveRecord::getStaffId)
                    .distinct()
                    .count();
            double avgHours = records.stream()
                    .filter(r -> r.getWorkDate().equals(day))
                    .map(AttendanceService::computeHours)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0);

            trends.add(DailyAttendanceTrend.builder()
                    .date(day)
                    .presentCount(present)
                    .leaveCount(onLeave)
                    .avgHours(BigDecimal.valueOf(avgHours).setScale(1, RoundingMode.HALF_UP))
                    .build());
        }
        return trends;
    }

    private boolean isOnLeave(UUID staffId, List<LeaveRecord> leaves, LocalDate date) {
        return leaves.stream()
                .anyMatch(l -> l.getStaffId().equals(staffId)
                        && !l.getStartDate().isAfter(date)
                        && !l.getEndDate().isBefore(date));
    }

    private long countLeaveDays(UUID staffId, List<LeaveRecord> leaves, LocalDate start, LocalDate end) {
        long count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isOnLeave(staffId, leaves, d)) count++;
        }
        return count;
    }

    private long countAbsentDays(UUID staffId, List<AttendanceRecord> staffRecords,
                                 List<LeaveRecord> approvedLeaves, LocalDate start, LocalDate end) {
        long count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            final LocalDate day = d;
            if (day.isAfter(LocalDate.now(ZONE))) continue;
            if (isOnLeave(staffId, approvedLeaves, day)) continue;
            boolean present = staffRecords.stream()
                    .anyMatch(r -> r.getWorkDate().equals(day) && r.getEntryTime() != null);
            if (!present) count++;
        }
        return count;
    }

    private BigDecimal computePerformanceScore(long daysPresent, long leaveDays, long lateArrivals,
                                               BigDecimal avgHours, LocalDate start, LocalDate end) {
        long workingDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        double attendancePct = workingDays > 0 ? (daysPresent * 100.0 / workingDays) : 0;
        double punctualityPct = daysPresent > 0 ? ((daysPresent - lateArrivals) * 100.0 / daysPresent) : 0;
        double hoursScore = Math.min(avgHours.doubleValue() / 8.0 * 100, 100);
        double leavePenalty = Math.min(leaveDays * 2.0, 20);

        double score = attendancePct * 0.45 + punctualityPct * 0.25 + hoursScore * 0.30 - leavePenalty;
        return BigDecimal.valueOf(Math.max(0, Math.min(100, score))).setScale(0, RoundingMode.HALF_UP);
    }
}
