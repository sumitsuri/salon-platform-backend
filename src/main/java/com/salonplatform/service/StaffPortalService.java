package com.salonplatform.service;

import com.salonplatform.domain.entity.AttendanceRecord;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Invoice;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.enums.LeaveStatus;
import com.salonplatform.domain.repository.AttendanceRecordRepository;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.InvoiceRepository;
import com.salonplatform.domain.repository.LeaveRecordRepository;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.dto.attendance.AttendanceResponse;
import com.salonplatform.dto.attendance.PunchResult;
import com.salonplatform.dto.attendance.VerifiedPunchRequest;
import com.salonplatform.dto.leave.CreateLeaveRequest;
import com.salonplatform.dto.leave.LeaveResponse;
import com.salonplatform.dto.staffportal.*;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffPortalService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final StaffAccessService staffAccessService;
    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final LeaveRecordRepository leaveRepository;
    private final AttendanceService attendanceService;
    private final AttendancePhotoStorageService photoStorage;
    private final LeaveService leaveService;
    private final StaffGrowthManagementService growthManagementService;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceSalesAggregationService invoiceSalesAggregationService;
    private final StaffPortalSalesInsightsService staffPortalSalesInsightsService;

    @Transactional(readOnly = true)
    public StaffPortalProfileResponse getProfile() {
        Staff staff = staffAccessService.requireCurrentStaff();
        UserPrincipal user = SecurityUtils.currentUser();
        Branch branch = branchRepository.findById(staff.getBranchId()).orElse(null);
        return StaffPortalProfileResponse.builder()
                .staffId(staff.getId())
                .userId(user.getId())
                .name(staff.getName())
                .email(user.getEmail())
                .phone(staff.getPhone())
                .designation(staff.getDesignation())
                .role(staff.getRole())
                .skills(staff.getSkills())
                .branchId(staff.getBranchId())
                .branchName(branch != null ? branch.getName() : null)
                .joiningDate(staff.getJoiningDate())
                .hasProfilePhoto(staff.getProfilePhotoKey() != null)
                .hasAadharDocument(staff.getAadharDocumentKey() != null)
                .idProofReference(staff.getIdProofReference())
                .monthlySalesTarget(staff.getMonthlySalesTarget())
                .incentivePercent(staff.getIncentivePercent())
                .build();
    }

    @Transactional
    public StaffPortalProfileResponse updateProfile(UpdateStaffPortalProfileRequest request) {
        Staff staff = staffAccessService.requireCurrentStaff();
        if (request.getPhone() != null) {
            staff.setPhone(request.getPhone().trim());
        }
        if (request.getDesignation() != null && !request.getDesignation().isBlank()) {
            staff.setDesignation(request.getDesignation().trim());
        }
        staffRepository.save(staff);
        return getProfile();
    }

    @Transactional
    public StaffPortalProfileResponse uploadProfilePhoto(MultipartFile photo) {
        Staff staff = staffAccessService.requireCurrentStaff();
        if (staff.getProfilePhotoKey() != null) {
            photoStorage.delete(staff.getProfilePhotoKey());
        }
        String key = photoStorage.store(staff.getTenantId(), staff.getBranchId(), staff.getId(), "profile", photo);
        staff.setProfilePhotoKey(key);
        staffRepository.save(staff);
        return getProfile();
    }

    @Transactional
    public StaffPortalProfileResponse uploadAadhar(MultipartFile document) {
        Staff staff = staffAccessService.requireCurrentStaff();
        if (staff.getAadharDocumentKey() != null) {
            photoStorage.delete(staff.getAadharDocumentKey());
        }
        String key = photoStorage.store(staff.getTenantId(), staff.getBranchId(), staff.getId(), "aadhar", document);
        staff.setAadharDocumentKey(key);
        staff.setIdProofCollected(true);
        staffRepository.save(staff);
        return getProfile();
    }

    public byte[] loadProfilePhoto() {
        Staff staff = staffAccessService.requireCurrentStaff();
        if (staff.getProfilePhotoKey() == null) {
            throw new com.salonplatform.exception.ResourceNotFoundException("No profile photo");
        }
        return photoStorage.load(staff.getProfilePhotoKey());
    }

    public String profilePhotoContentType() {
        Staff staff = staffAccessService.requireCurrentStaff();
        if (staff.getProfilePhotoKey() == null) {
            throw new com.salonplatform.exception.ResourceNotFoundException("No profile photo");
        }
        return photoStorage.contentTypeForKey(staff.getProfilePhotoKey());
    }

    public byte[] loadAadharDocument() {
        Staff staff = staffAccessService.requireCurrentStaff();
        if (staff.getAadharDocumentKey() == null) {
            throw new com.salonplatform.exception.ResourceNotFoundException("No document");
        }
        return photoStorage.load(staff.getAadharDocumentKey());
    }

    @Transactional
    public PunchResult selfPunch(VerifiedPunchRequest request, MultipartFile photo) {
        Staff staff = staffAccessService.requireCurrentStaff();
        request.setStaffId(staff.getId());
        return attendanceService.verifiedPunch(request, photo);
    }

    @Transactional(readOnly = true)
    public StaffAttendanceMonthResponse attendanceMonth(Integer year, Integer month) {
        Staff staff = staffAccessService.requireCurrentStaff();
        LocalDate today = LocalDate.now(ZONE);
        int y = year != null ? year : today.getYear();
        int m = month != null ? month : today.getMonthValue();
        LocalDate start = LocalDate.of(y, m, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        if (end.isAfter(today)) {
            end = today;
        }

        Branch branch = branchRepository.findById(staff.getBranchId()).orElse(null);
        List<AttendanceRecord> records = attendanceRepository.findByStaffIdAndWorkDateBetweenOrderByWorkDateDesc(
                staff.getId(), start, end);
        Map<LocalDate, AttendanceRecord> byDate = records.stream()
                .collect(Collectors.toMap(AttendanceRecord::getWorkDate, r -> r, (a, b) -> a));

        long leaveDays = countLeaveDays(staff.getId(), start, end);
        double present = 0;
        double absent = 0;
        double half = 0;
        long overtimeMinutes = 0;
        long lessMinutes = 0;

        List<AttendanceResponse> dayRows = new ArrayList<>();
        for (LocalDate d = end; !d.isBefore(start); d = d.minusDays(1)) {
            AttendanceRecord record = byDate.get(d);
            if (record != null) {
                AttendanceResponse row = attendanceService.toResponse(record, staff);
                dayRows.add(row);
                String status = row.getStatus();
                if ("COMPLETED".equals(status)) {
                    present += 1;
                } else if ("PRESENT".equals(status)) {
                    present += 0.5;
                    half += 0.5;
                }
                if (row.getHoursWorked() != null && branch != null) {
                    long expectedMinutes = expectedShiftMinutes(branch);
                    long workedMinutes = (long) (row.getHoursWorked() * 60);
                    if (workedMinutes > expectedMinutes) {
                        overtimeMinutes += workedMinutes - expectedMinutes;
                    } else if (workedMinutes < expectedMinutes) {
                        lessMinutes += expectedMinutes - workedMinutes;
                    }
                }
            } else if (!isOnLeave(staff.getId(), d)) {
                if (isWorkingDay(d)) {
                    absent += 1;
                    dayRows.add(AttendanceResponse.builder()
                            .staffId(staff.getId())
                            .staffName(staff.getName())
                            .branchId(staff.getBranchId())
                            .workDate(d)
                            .status("ABSENT")
                            .build());
                }
            }
        }

        String periodLabel = start.format(DateTimeFormatter.ofPattern("d MMM")) + " – "
                + end.format(DateTimeFormatter.ofPattern("d MMM yyyy"));

        return StaffAttendanceMonthResponse.builder()
                .year(y)
                .month(m)
                .monthLabel(start.format(MONTH_LABEL))
                .periodLabel(periodLabel)
                .presentDays(present)
                .absentDays(absent)
                .halfDays(half)
                .leaveDays(leaveDays)
                .overtimeHours(formatDurationMinutes(overtimeMinutes))
                .lessHours(formatDurationMinutes(lessMinutes))
                .days(dayRows)
                .build();
    }

    @Transactional(readOnly = true)
    public StaffGrowthSnapshotResponse growthSnapshot() {
        Staff staff = staffAccessService.requireCurrentStaff();
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = today;
        Instant rangeStart = start.atStartOfDay(ZONE).toInstant();
        Instant rangeEnd = end.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<Invoice> invoices = invoiceRepository.findByTenantAndDateRange(staff.getTenantId(), rangeStart, rangeEnd);
        invoices = invoices.stream().filter(i -> staff.getBranchId().equals(i.getBranchId())).collect(Collectors.toList());
        var agg = invoiceSalesAggregationService.aggregateByStaff(invoices).getOrDefault(
                staff.getId(),
                new InvoiceSalesAggregationService.StaffLineAggregate(BigDecimal.ZERO, BigDecimal.ZERO, 0));

        BigDecimal target = staff.getMonthlySalesTarget() != null ? staff.getMonthlySalesTarget() : BigDecimal.ZERO;
        BigDecimal actual = agg.finalRevenue();
        BigDecimal achievement = BigDecimal.ZERO;
        if (target.compareTo(BigDecimal.ZERO) > 0) {
            achievement = actual.multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP);
        }
        long daysInPeriod = ChronoUnit.DAYS.between(start, end) + 1;
        long daysElapsed = today.getDayOfMonth();
        boolean meeting = target.compareTo(BigDecimal.ZERO) > 0 && actual.compareTo(target) >= 0;
        boolean onTrack = meeting;
        if (!meeting && target.compareTo(BigDecimal.ZERO) > 0 && daysElapsed > 0) {
            BigDecimal expected = target.multiply(BigDecimal.valueOf(daysElapsed))
                    .divide(BigDecimal.valueOf(daysInPeriod), 2, RoundingMode.HALF_UP);
            onTrack = actual.compareTo(expected) >= 0;
        }

        BigDecimal incentivePct = staff.getIncentivePercent() != null ? staff.getIncentivePercent() : BigDecimal.ZERO;
        BigDecimal projectedIncentive = BigDecimal.ZERO;
        if (meeting && target.compareTo(BigDecimal.ZERO) > 0 && incentivePct.compareTo(BigDecimal.ZERO) > 0) {
            projectedIncentive = target.multiply(incentivePct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        List<AttendanceRecord> records = attendanceRepository.findByStaffIdAndWorkDateBetweenOrderByWorkDateDesc(
                staff.getId(), start, end);
        long present = records.stream().filter(r -> r.getEntryTime() != null).count();
        long absent = Math.max(0, daysElapsed - present - countLeaveDays(staff.getId(), start, end));
        long late = records.stream().filter(r -> AttendanceService.isLate(r)).count();
        long early = records.stream().filter(r -> {
            Branch branch = branchRepository.findById(staff.getBranchId()).orElse(null);
            return AttendanceService.isEarlyExit(r, branch);
        }).count();
        long geo = records.stream().filter(AttendanceService::hasGeoFlag).count();
        int compliance = AttendanceService.computeComplianceScore(present, absent, late, early, geo);

        String periodLabel = start.format(DateTimeFormatter.ofPattern("d MMM")) + " – "
                + end.format(DateTimeFormatter.ofPattern("d MMM yyyy"));

        Instant todayStart = today.atStartOfDay(ZONE).toInstant();
        Instant todayEnd = today.plusDays(1).atStartOfDay(ZONE).toInstant();
        List<Invoice> todayInvoices = invoiceRepository.findByTenantAndDateRange(staff.getTenantId(), todayStart, todayEnd);
        todayInvoices = todayInvoices.stream()
                .filter(i -> staff.getBranchId().equals(i.getBranchId()))
                .collect(Collectors.toList());
        var todayAgg = invoiceSalesAggregationService.aggregateByStaff(todayInvoices).getOrDefault(
                staff.getId(),
                new InvoiceSalesAggregationService.StaffLineAggregate(BigDecimal.ZERO, BigDecimal.ZERO, 0));

        return StaffGrowthSnapshotResponse.builder()
                .periodLabel(periodLabel)
                .monthlySalesTarget(target)
                .actualSales(actual)
                .achievementPercent(achievement)
                .meetingTarget(meeting)
                .onTrack(onTrack)
                .salesCount(agg.serviceCount())
                .avgTicketSize(agg.avgFinalTicket())
                .projectedIncentive(projectedIncentive)
                .attendanceComplianceScore(BigDecimal.valueOf(compliance))
                .daysPresent(present)
                .daysAbsent(absent)
                .todaySales(todayAgg.finalRevenue())
                .todaySalesCount(todayAgg.serviceCount())
                .build();
    }

    public LeaveResponse applyLeave(CreateLeaveRequest request) {
        Staff staff = staffAccessService.requireCurrentStaff();
        request.setStaffId(staff.getId());
        return leaveService.create(request);
    }

    public List<LeaveResponse> myLeaves() {
        Staff staff = staffAccessService.requireCurrentStaff();
        return leaveService.listForStaff(staff.getId());
    }

    public List<StaffGoalResponse> myGoals() {
        Staff staff = staffAccessService.requireCurrentStaff();
        return growthManagementService.listGoals(staff.getId());
    }

    public List<StaffPerformanceReviewResponse> myReviews() {
        Staff staff = staffAccessService.requireCurrentStaff();
        return growthManagementService.listReviews(staff.getId(), true);
    }

    @Transactional(readOnly = true)
    public StaffPortalSalesInsightsResponse salesInsights(int historyMonths) {
        Staff staff = staffAccessService.requireCurrentStaff();
        return staffPortalSalesInsightsService.insights(staff, historyMonths);
    }

    private long countLeaveDays(UUID staffId, LocalDate start, LocalDate end) {
        long count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isOnLeave(staffId, d)) count++;
        }
        return count;
    }

    private boolean isOnLeave(UUID staffId, LocalDate day) {
        return leaveRepository.existsByStaffIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                staffId, LeaveStatus.APPROVED, day, day);
    }

    private static boolean isWorkingDay(LocalDate day) {
        DayOfWeek dow = day.getDayOfWeek();
        return dow != DayOfWeek.SUNDAY;
    }

    private static long expectedShiftMinutes(Branch branch) {
        LocalTime open = AttendanceService.parseTime(branch.getOpenTime(), LocalTime.of(9, 30));
        LocalTime close = AttendanceService.parseTime(branch.getCloseTime(), LocalTime.of(18, 0));
        return Duration.between(open, close).toMinutes();
    }

    private static String formatDurationMinutes(long minutes) {
        if (minutes <= 0) return "00:00";
        long h = minutes / 60;
        long m = minutes % 60;
        return String.format("%02d:%02d", h, m);
    }
}
