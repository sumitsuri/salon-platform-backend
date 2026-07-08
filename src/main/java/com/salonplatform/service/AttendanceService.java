package com.salonplatform.service;

import com.salonplatform.domain.entity.AttendanceRecord;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.enums.AttendanceMethod;
import com.salonplatform.domain.enums.LeaveStatus;
import com.salonplatform.domain.repository.AttendanceRecordRepository;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.LeaveRecordRepository;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.dto.attendance.*;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.repository.AttendanceSpecifications;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import com.salonplatform.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalTime EXPECTED_ENTRY = LocalTime.of(9, 30);

    private final AttendanceRecordRepository attendanceRepository;
    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;
    private final LeaveRecordRepository leaveRepository;

    @Transactional
    public PunchResult biometricPunch(BiometricPunchRequest request) {
        UUID tenantId = SecurityUtils.requireTenantId();
        UserPrincipal user = SecurityUtils.currentUser();
        UUID branchId = user.getBranchId();
        if (branchId == null) {
            throw new BadRequestException("Branch context required for biometric punch");
        }
        SecurityUtils.assertBranchAccess(branchId);

        Staff staff = staffRepository.findByTenantIdAndBiometricId(tenantId, request.getBiometricId())
                .orElseThrow(() -> new BadRequestException("Fingerprint not recognized. Register staff biometric or use manual entry."));
        if (!staff.getBranchId().equals(branchId)) {
            throw new BadRequestException("Staff belongs to a different branch");
        }
        if (!staff.isActive()) {
            throw new BadRequestException("Staff is inactive");
        }

        LocalDate today = LocalDate.now(ZONE);
        if (leaveRepository.existsByStaffIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                staff.getId(), LeaveStatus.APPROVED, today, today)) {
            throw new BadRequestException(staff.getName() + " is on approved leave today");
        }

        AttendanceRecord record = attendanceRepository.findByStaffIdAndWorkDate(staff.getId(), today)
                .orElse(null);

        Instant now = Instant.now();
        String action;
        if (record == null) {
            record = attendanceRepository.save(AttendanceRecord.builder()
                    .tenantId(tenantId)
                    .branchId(branchId)
                    .staffId(staff.getId())
                    .workDate(today)
                    .entryTime(now)
                    .entryMethod(AttendanceMethod.BIOMETRIC)
                    .build());
            action = "CHECK_IN";
        } else if (record.getExitTime() == null) {
            record.setExitTime(now);
            record.setExitMethod(AttendanceMethod.BIOMETRIC);
            attendanceRepository.save(record);
            action = "CHECK_OUT";
        } else {
            throw new BadRequestException("Attendance already completed for today");
        }

        AttendanceResponse response = toResponse(record, staff);
        String message = action.equals("CHECK_IN")
                ? staff.getName() + " checked in at " + formatTime(now)
                : staff.getName() + " checked out at " + formatTime(now);

        return PunchResult.builder().action(action).record(response).message(message).build();
    }

    @Transactional
    public AttendanceResponse manualEntry(ManualAttendanceRequest request) {
        UUID tenantId = SecurityUtils.requireTenantId();
        UserPrincipal user = SecurityUtils.currentUser();

        Staff staff = staffRepository.findById(request.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        if (!staff.getTenantId().equals(tenantId)) {
            throw new BadRequestException("Staff not in tenant");
        }
        SecurityUtils.assertBranchAccess(staff.getBranchId());

        if (request.getEntryTime() == null && request.getExitTime() == null) {
            throw new BadRequestException("Provide entry time and/or exit time");
        }

        AttendanceRecord record = attendanceRepository.findByStaffIdAndWorkDate(staff.getId(), request.getWorkDate())
                .orElse(AttendanceRecord.builder()
                        .tenantId(tenantId)
                        .branchId(staff.getBranchId())
                        .staffId(staff.getId())
                        .workDate(request.getWorkDate())
                        .build());

        if (request.getEntryTime() != null) {
            record.setEntryTime(request.getEntryTime());
            record.setEntryMethod(AttendanceMethod.MANUAL);
        }
        if (request.getExitTime() != null) {
            if (record.getEntryTime() != null && request.getExitTime().isBefore(record.getEntryTime())) {
                throw new BadRequestException("Exit time must be after entry time");
            }
            record.setExitTime(request.getExitTime());
            record.setExitMethod(AttendanceMethod.MANUAL);
        }
        record.setManualReason(request.getReason());
        record.setRecordedByUserId(user.getId());

        return toResponse(attendanceRepository.save(record), staff);
    }

    public PageResponse<AttendanceResponse> listPaged(AttendanceListFilter filter) {
        UUID tenantId = SecurityUtils.requireTenantId();

        if (filter.getBranchId() != null) {
            SecurityUtils.assertBranchAccess(filter.getBranchId());
        } else {
            SecurityUtils.assertBrandAdminOrAbove();
        }

        LocalDate start = filter.getDateFrom() != null ? filter.getDateFrom() : LocalDate.now(ZONE).minusDays(30);
        LocalDate end = filter.getDateTo() != null ? filter.getDateTo() : LocalDate.now(ZONE);
        AttendanceListFilter effective = AttendanceListFilter.builder()
                .branchId(filter.getBranchId())
                .staff(filter.getStaff())
                .branch(filter.getBranch())
                .status(filter.getStatus())
                .dateFrom(start)
                .dateTo(end)
                .page(filter.getPage())
                .size(filter.getSize())
                .build();

        int page = PageUtils.normalizePage(effective.getPage());
        int size = PageUtils.normalizeSize(effective.getSize());
        Specification<AttendanceRecord> spec = AttendanceSpecifications.fromFilter(tenantId, effective);
        Page<AttendanceRecord> result = attendanceRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "workDate", "entryTime"))
        );

        List<AttendanceResponse> content = result.getContent().stream()
                .map(r -> staffRepository.findById(r.getStaffId())
                        .map(s -> toResponse(r, s))
                        .orElse(null))
                .filter(r -> r != null)
                .collect(Collectors.toList());

        return PageResponse.<AttendanceResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    public List<AttendanceResponse> list(UUID branchId, LocalDate startDate, LocalDate endDate) {
        return listPaged(AttendanceListFilter.builder()
                .branchId(branchId)
                .dateFrom(startDate)
                .dateTo(endDate)
                .page(0)
                .size(1000)
                .build()).getContent();
    }

    public List<AttendanceResponse> todayForBranch(UUID branchId) {
        LocalDate today = LocalDate.now(ZONE);
        return list(branchId, today, today);
    }

    AttendanceResponse toResponse(AttendanceRecord record, Staff staff) {
        String branchName = branchRepository.findById(record.getBranchId()).map(Branch::getName).orElse(null);
        Double hours = computeHours(record);
        String status = deriveStatus(record);

        return AttendanceResponse.builder()
                .id(record.getId())
                .staffId(staff.getId())
                .staffName(staff.getName())
                .branchId(record.getBranchId())
                .branchName(branchName)
                .workDate(record.getWorkDate())
                .entryTime(record.getEntryTime())
                .exitTime(record.getExitTime())
                .entryMethod(record.getEntryMethod())
                .exitMethod(record.getExitMethod())
                .manualReason(record.getManualReason())
                .hoursWorked(hours)
                .status(status)
                .build();
    }

    static Double computeHours(AttendanceRecord record) {
        if (record.getEntryTime() == null || record.getExitTime() == null) return null;
        return Duration.between(record.getEntryTime(), record.getExitTime()).toMinutes() / 60.0;
    }

    static String deriveStatus(AttendanceRecord record) {
        if (record.getEntryTime() == null) return "ABSENT";
        if (record.getExitTime() == null) return "PRESENT";
        return "COMPLETED";
    }

    static boolean isLate(AttendanceRecord record) {
        if (record.getEntryTime() == null) return false;
        LocalTime entry = record.getEntryTime().atZone(ZONE).toLocalTime();
        return entry.isAfter(EXPECTED_ENTRY);
    }

    private String formatTime(Instant instant) {
        return instant.atZone(ZONE).toLocalTime().toString().substring(0, 5);
    }
}
