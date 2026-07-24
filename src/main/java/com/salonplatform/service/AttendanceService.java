package com.salonplatform.service;

import com.salonplatform.domain.entity.AttendanceRecord;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.enums.AttendanceMethod;
import com.salonplatform.domain.enums.GeoStatus;
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
import com.salonplatform.util.GeofenceUtil;
import com.salonplatform.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalTime DEFAULT_OPEN = LocalTime.of(9, 30);
    private static final LocalTime DEFAULT_CLOSE = LocalTime.of(18, 0);

    private final AttendanceRecordRepository attendanceRepository;
    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;
    private final LeaveRecordRepository leaveRepository;
    private final AttendancePhotoStorageService photoStorage;

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
        return punchInternal(tenantId, branchId, staff, AttendanceMethod.BIOMETRIC, null, null, null, null, null);
    }

    @Transactional
    public PunchResult verifiedPunch(VerifiedPunchRequest request, MultipartFile photo) {
        UUID tenantId = SecurityUtils.requireTenantId();
        UserPrincipal user = SecurityUtils.currentUser();
        UUID branchId = user.getBranchId();
        if (branchId == null) {
            throw new BadRequestException("Branch context required for verified punch");
        }
        SecurityUtils.assertBranchAccess(branchId);

        Staff staff = staffRepository.findById(request.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        if (!staff.getTenantId().equals(tenantId) || !staff.getBranchId().equals(branchId)) {
            throw new BadRequestException("Staff not in this branch");
        }
        if (!staff.isActive()) {
            throw new BadRequestException("Staff is inactive");
        }

        LocalDate today = LocalDate.now(ZONE);
        if (leaveRepository.existsByStaffIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                staff.getId(), LeaveStatus.APPROVED, today, today)) {
            throw new BadRequestException(staff.getName() + " is on approved leave today");
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        GeoStatus geoStatus = GeofenceUtil.evaluate(
                request.getLatitude(),
                request.getLongitude(),
                request.getAccuracyMeters(),
                request.getLocationHighAccuracy(),
                branch);

        AttendanceRecord record = attendanceRepository.findByStaffIdAndWorkDate(staff.getId(), LocalDate.now(ZONE))
                .orElse(null);
        String action = resolveAction(request.getAction(), record);

        Instant now = Instant.now();
        if ("CHECK_IN".equals(action)) {
            if (record != null && record.getEntryTime() != null) {
                throw new BadRequestException("Already checked in today");
            }
            if (record == null) {
                record = AttendanceRecord.builder()
                        .tenantId(tenantId)
                        .branchId(branchId)
                        .staffId(staff.getId())
                        .workDate(LocalDate.now(ZONE))
                        .build();
            }
            String photoKey = photoStorage.store(tenantId, branchId, staff.getId(), "entry", photo);
            record.setEntryTime(now);
            record.setEntryMethod(AttendanceMethod.VERIFIED);
            record.setEntryLatitude(request.getLatitude());
            record.setEntryLongitude(request.getLongitude());
            record.setEntryAccuracyMeters(request.getAccuracyMeters());
            record.setEntryGeoStatus(geoStatus);
            record.setEntryPhotoKey(photoKey);
            record.setEntryVerified(true);
        } else {
            if (record == null || record.getEntryTime() == null) {
                throw new BadRequestException("Check in first before check out");
            }
            if (record.getExitTime() != null) {
                throw new BadRequestException("Attendance already completed for today");
            }
            String photoKey = photoStorage.store(tenantId, branchId, staff.getId(), "exit", photo);
            record.setExitTime(now);
            record.setExitMethod(AttendanceMethod.VERIFIED);
            record.setExitLatitude(request.getLatitude());
            record.setExitLongitude(request.getLongitude());
            record.setExitAccuracyMeters(request.getAccuracyMeters());
            record.setExitGeoStatus(geoStatus);
            record.setExitPhotoKey(photoKey);
            record.setExitVerified(true);
        }

        record = attendanceRepository.save(record);
        AttendanceResponse response = toResponse(record, staff);
        String message = "CHECK_IN".equals(action)
                ? staff.getName() + " checked in at " + formatTime(now)
                : staff.getName() + " checked out at " + formatTime(now);
        return PunchResult.builder().action(action).record(response).message(message).build();
    }

    private PunchResult punchInternal(UUID tenantId, UUID branchId, Staff staff, AttendanceMethod method,
                                      Double lat, Double lng, Double accuracy, GeoStatus geoStatus, String photoKey) {
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

        AttendanceRecord record = attendanceRepository.findByStaffIdAndWorkDate(staff.getId(), today).orElse(null);
        Instant now = Instant.now();
        String action;
        if (record == null) {
            record = attendanceRepository.save(AttendanceRecord.builder()
                    .tenantId(tenantId)
                    .branchId(branchId)
                    .staffId(staff.getId())
                    .workDate(today)
                    .entryTime(now)
                    .entryMethod(method)
                    .build());
            action = "CHECK_IN";
        } else if (record.getExitTime() == null) {
            record.setExitTime(now);
            record.setExitMethod(method);
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

    private static String resolveAction(String requested, AttendanceRecord record) {
        if (requested != null && !requested.isBlank()) {
            return requested.toUpperCase();
        }
        if (record == null || record.getEntryTime() == null) return "CHECK_IN";
        if (record.getExitTime() == null) return "CHECK_OUT";
        throw new BadRequestException("Attendance already completed for today");
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

    public AttendanceRecord requireRecord(UUID id) {
        UUID tenantId = SecurityUtils.requireTenantId();
        AttendanceRecord record = attendanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found"));
        if (!record.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Attendance record not found");
        }
        SecurityUtils.assertBranchAccess(record.getBranchId());
        return record;
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
        Branch branch = branchRepository.findById(record.getBranchId()).orElse(null);
        String branchName = branch != null ? branch.getName() : null;
        Double hours = computeHours(record);
        String status = deriveStatus(record);
        boolean late = isLate(record, branch);
        boolean earlyExit = isEarlyExit(record, branch);

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
                .entryGeoStatus(record.getEntryGeoStatus())
                .exitGeoStatus(record.getExitGeoStatus())
                .entryVerified(record.getEntryVerified())
                .exitVerified(record.getExitVerified())
                .hasEntryPhoto(record.getEntryPhotoKey() != null)
                .hasExitPhoto(record.getExitPhotoKey() != null)
                .late(late)
                .earlyExit(earlyExit)
                .lateMinutes(late ? computeLateMinutes(record, branch) : null)
                .earlyExitMinutes(earlyExit ? computeEarlyExitMinutes(record, branch) : null)
                .complianceFlags(buildComplianceFlags(record, branch, late, earlyExit))
                .branchLatitude(branch != null ? branch.getLatitude() : null)
                .branchLongitude(branch != null ? branch.getLongitude() : null)
                .geofenceRadiusMeters(branch != null ? branch.getGeofenceRadiusMeters() : null)
                .entryLatitude(record.getEntryLatitude())
                .entryLongitude(record.getEntryLongitude())
                .exitLatitude(record.getExitLatitude())
                .exitLongitude(record.getExitLongitude())
                .entryDistanceMeters(GeofenceUtil.distanceMeters(
                        record.getEntryLatitude(), record.getEntryLongitude(), branch))
                .exitDistanceMeters(GeofenceUtil.distanceMeters(
                        record.getExitLatitude(), record.getExitLongitude(), branch))
                .build();
    }

    @Transactional
    public int resetAllForTenant() {
        SecurityUtils.assertBrandAdmin();
        UUID tenantId = SecurityUtils.requireTenantId();
        List<AttendanceRecord> records = attendanceRepository.findByTenantIdOrderByWorkDateDesc(tenantId);
        for (AttendanceRecord record : records) {
            if (record.getEntryPhotoKey() != null) {
                photoStorage.delete(record.getEntryPhotoKey());
            }
            if (record.getExitPhotoKey() != null) {
                photoStorage.delete(record.getExitPhotoKey());
            }
        }
        attendanceRepository.deleteByTenantId(tenantId);
        return records.size();
    }

    static List<String> buildComplianceFlags(AttendanceRecord record, Branch branch, boolean late, boolean earlyExit) {
        List<String> flags = new ArrayList<>();
        if (late) flags.add("LATE");
        if (earlyExit) flags.add("EARLY_EXIT");
        if (record.getEntryGeoStatus() == GeoStatus.OUT_OF_GEOFENCE
                || record.getExitGeoStatus() == GeoStatus.OUT_OF_GEOFENCE) {
            flags.add("OUT_OF_GEO");
        }
        if (Boolean.TRUE.equals(record.getEntryVerified()) && record.getEntryPhotoKey() == null
                || Boolean.TRUE.equals(record.getExitVerified()) && record.getExitPhotoKey() == null) {
            flags.add("NO_PHOTO");
        }
        if (record.getEntryTime() == null) flags.add("ABSENT");
        return flags;
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

    static boolean isLate(AttendanceRecord record, Branch branch) {
        if (record.getEntryTime() == null) return false;
        LocalTime entry = record.getEntryTime().atZone(ZONE).toLocalTime();
        LocalTime expected = parseTime(branch != null ? branch.getOpenTime() : null, DEFAULT_OPEN);
        int grace = branch != null && branch.getAttendanceGraceMinutes() != null
                ? branch.getAttendanceGraceMinutes() : 15;
        return entry.isAfter(expected.plusMinutes(grace));
    }

    /** Backward-compatible for analytics without branch lookup. */
    static boolean isLate(AttendanceRecord record) {
        return isLate(record, null);
    }

    static boolean isEarlyExit(AttendanceRecord record, Branch branch) {
        if (record.getExitTime() == null) return false;
        LocalTime exit = record.getExitTime().atZone(ZONE).toLocalTime();
        LocalTime expectedClose = parseTime(branch != null ? branch.getCloseTime() : null, DEFAULT_CLOSE);
        int grace = branch != null && branch.getAttendanceGraceMinutes() != null
                ? branch.getAttendanceGraceMinutes() : 15;
        return exit.isBefore(expectedClose.minusMinutes(grace));
    }

    static Integer computeLateMinutes(AttendanceRecord record, Branch branch) {
        if (record.getEntryTime() == null || !isLate(record, branch)) return null;
        LocalTime entry = record.getEntryTime().atZone(ZONE).toLocalTime();
        LocalTime expected = parseTime(branch != null ? branch.getOpenTime() : null, DEFAULT_OPEN);
        int grace = branch != null && branch.getAttendanceGraceMinutes() != null
                ? branch.getAttendanceGraceMinutes() : 15;
        return (int) Duration.between(expected.plusMinutes(grace), entry).toMinutes();
    }

    static Integer computeEarlyExitMinutes(AttendanceRecord record, Branch branch) {
        if (record.getExitTime() == null || !isEarlyExit(record, branch)) return null;
        LocalTime exit = record.getExitTime().atZone(ZONE).toLocalTime();
        LocalTime expectedClose = parseTime(branch != null ? branch.getCloseTime() : null, DEFAULT_CLOSE);
        int grace = branch != null && branch.getAttendanceGraceMinutes() != null
                ? branch.getAttendanceGraceMinutes() : 15;
        return (int) Duration.between(exit, expectedClose.minusMinutes(grace)).toMinutes();
    }

    static boolean hasGeoFlag(AttendanceRecord record) {
        return record.getEntryGeoStatus() == GeoStatus.OUT_OF_GEOFENCE
                || record.getExitGeoStatus() == GeoStatus.OUT_OF_GEOFENCE;
    }

    static LocalTime parseTime(String value, LocalTime fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            if (value.length() <= 5) return LocalTime.parse(value);
            return LocalTime.parse(value.substring(0, 5));
        } catch (Exception e) {
            return fallback;
        }
    }

    static int computeComplianceScore(long daysPresent, long absentDays, long lateDays, long earlyExitDays, long geoFlagDays) {
        int score = 100;
        score -= (int) (lateDays * 5);
        score -= (int) (earlyExitDays * 5);
        score -= (int) (geoFlagDays * 3);
        score -= (int) (absentDays * 10);
        return Math.max(0, Math.min(100, score));
    }

    private String formatTime(Instant instant) {
        return instant.atZone(ZONE).toLocalTime().toString().substring(0, 5);
    }
}
