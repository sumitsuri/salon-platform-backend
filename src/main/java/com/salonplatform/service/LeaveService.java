package com.salonplatform.service;

import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.LeaveRecord;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.enums.LeaveStatus;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.LeaveRecordRepository;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.leave.CreateLeaveRequest;
import com.salonplatform.dto.leave.LeaveListFilter;
import com.salonplatform.dto.leave.LeaveResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.repository.LeaveSpecifications;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final LeaveRecordRepository leaveRepository;
    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;

    @Transactional
    public LeaveResponse create(CreateLeaveRequest request) {
        UUID tenantId = SecurityUtils.requireTenantId();
        UserPrincipal user = SecurityUtils.currentUser();

        Staff staff = staffRepository.findById(request.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        if (!staff.getTenantId().equals(tenantId)) {
            throw new BadRequestException("Staff not in tenant");
        }
        if (SecurityUtils.isSalonStaff()) {
            Staff self = staffRepository.findByTenantIdAndUserId(tenantId, user.getId())
                    .orElseThrow(() -> new BadRequestException("No employee profile linked to this login"));
            if (!self.getId().equals(staff.getId())) {
                throw new com.salonplatform.exception.ForbiddenException("You can only apply leave for yourself");
            }
        } else {
            SecurityUtils.assertBranchAccess(staff.getBranchId());
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date must be on or after start date");
        }

        LeaveStatus initialStatus;
        if (SecurityUtils.isSalonStaff()) {
            initialStatus = LeaveStatus.PENDING;
        } else if (SecurityUtils.isManagerRole()) {
            initialStatus = LeaveStatus.PENDING;
        } else {
            initialStatus = LeaveStatus.APPROVED;
        }

        LeaveRecord record = leaveRepository.save(LeaveRecord.builder()
                .tenantId(tenantId)
                .branchId(staff.getBranchId())
                .staffId(staff.getId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .leaveType(request.getLeaveType() != null ? request.getLeaveType() : com.salonplatform.domain.enums.LeaveType.FULL_DAY)
                .status(initialStatus)
                .reason(request.getReason())
                .createdByUserId(user.getId())
                .approvedByUserId(initialStatus == LeaveStatus.APPROVED ? user.getId() : null)
                .build());

        return toResponse(record, staff);
    }

    @Transactional
    public LeaveResponse approve(UUID leaveId) {
        return updateStatus(leaveId, LeaveStatus.APPROVED);
    }

    @Transactional
    public LeaveResponse reject(UUID leaveId) {
        return updateStatus(leaveId, LeaveStatus.REJECTED);
    }

    private LeaveResponse updateStatus(UUID leaveId, LeaveStatus status) {
        UUID tenantId = SecurityUtils.requireTenantId();
        UserPrincipal user = SecurityUtils.currentUser();
        if (!SecurityUtils.isManagerRole() && !SecurityUtils.isBrandAdmin()) {
            throw new com.salonplatform.exception.ForbiddenException("Manager or brand admin access required");
        }

        LeaveRecord record = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave record not found"));
        if (!record.getTenantId().equals(tenantId)) {
            throw new BadRequestException("Leave not in tenant");
        }
        if (SecurityUtils.isManagerRole()) {
            SecurityUtils.assertBranchAccess(record.getBranchId());
        }
        record.setStatus(status);
        record.setApprovedByUserId(user.getId());
        leaveRepository.save(record);

        Staff staff = staffRepository.findById(record.getStaffId()).orElseThrow();
        return toResponse(record, staff);
    }

    public List<LeaveResponse> listForStaff(UUID staffId) {
        UUID tenantId = SecurityUtils.requireTenantId();
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        if (!staff.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Staff not found");
        }
        if (SecurityUtils.isSalonStaff()) {
            Staff self = staffRepository.findByTenantIdAndUserId(tenantId, SecurityUtils.currentUserId())
                    .orElseThrow(() -> new BadRequestException("No employee profile linked to this login"));
            if (!self.getId().equals(staffId)) {
                throw new com.salonplatform.exception.ForbiddenException("Access denied");
            }
        } else if (SecurityUtils.isManagerRole()) {
            SecurityUtils.assertBranchAccess(staff.getBranchId());
        } else {
            SecurityUtils.assertBrandAdminOrAbove();
        }
        LocalDate start = LocalDate.now(ZONE).minusMonths(6);
        LocalDate end = LocalDate.now(ZONE).plusMonths(3);
        return leaveRepository.findByStaffIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        staffId, end, start).stream()
                .sorted(Comparator.comparing(LeaveRecord::getStartDate).reversed())
                .map(r -> toResponse(r, staff))
                .collect(Collectors.toList());
    }

    public PageResponse<LeaveResponse> listPaged(LeaveListFilter filter) {
        UUID tenantId = SecurityUtils.requireTenantId();

        if (filter.getStaffId() != null) {
            Staff staff = staffRepository.findById(filter.getStaffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
            if (!staff.getTenantId().equals(tenantId)) {
                throw new ResourceNotFoundException("Staff not found");
            }
            if (SecurityUtils.isSalonStaff()) {
                Staff self = staffRepository.findByTenantIdAndUserId(tenantId, SecurityUtils.currentUserId())
                        .orElseThrow(() -> new BadRequestException("No employee profile linked to this login"));
                if (!self.getId().equals(filter.getStaffId())) {
                    throw new com.salonplatform.exception.ForbiddenException("Access denied");
                }
            } else if (SecurityUtils.isManagerRole()) {
                SecurityUtils.assertBranchAccess(staff.getBranchId());
            }
        } else if (filter.getBranchId() != null) {
            SecurityUtils.assertBranchAccess(filter.getBranchId());
        } else if (SecurityUtils.isSalonStaff()) {
            throw new com.salonplatform.exception.ForbiddenException("Staff id required");
        } else {
            SecurityUtils.assertBrandAdminOrAbove();
        }

        LocalDate start = filter.getDateFrom() != null ? filter.getDateFrom() : LocalDate.now(ZONE).minusDays(30);
        LocalDate end = filter.getDateTo() != null ? filter.getDateTo() : LocalDate.now(ZONE).plusDays(30);
        LeaveListFilter effective = LeaveListFilter.builder()
                .branchId(filter.getBranchId())
                .staffId(filter.getStaffId())
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
        Specification<LeaveRecord> spec = LeaveSpecifications.fromFilter(tenantId, effective);
        Page<LeaveRecord> result = leaveRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startDate"))
        );

        List<LeaveResponse> content = result.getContent().stream()
                .map(r -> staffRepository.findById(r.getStaffId())
                        .map(s -> toResponse(r, s))
                        .orElse(null))
                .filter(r -> r != null)
                .collect(Collectors.toList());

        return PageResponse.<LeaveResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    public List<LeaveResponse> list(UUID branchId, LocalDate startDate, LocalDate endDate) {
        return listPaged(LeaveListFilter.builder()
                .branchId(branchId)
                .dateFrom(startDate)
                .dateTo(endDate)
                .page(0)
                .size(1000)
                .build()).getContent();
    }

    public LeaveResponse toResponse(LeaveRecord record, Staff staff) {
        String branchName = branchRepository.findById(record.getBranchId()).map(Branch::getName).orElse(null);
        return LeaveResponse.builder()
                .id(record.getId())
                .staffId(staff.getId())
                .staffName(staff.getName())
                .branchId(record.getBranchId())
                .branchName(branchName)
                .startDate(record.getStartDate())
                .endDate(record.getEndDate())
                .leaveType(record.getLeaveType())
                .status(record.getStatus())
                .reason(record.getReason())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
