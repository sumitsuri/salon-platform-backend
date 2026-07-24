package com.salonplatform.service;

import com.salonplatform.domain.entity.AttendanceIncident;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.repository.AttendanceIncidentRepository;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.dto.attendance.AttendanceIncidentResponse;
import com.salonplatform.dto.attendance.CreateAttendanceIncidentRequest;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import com.salonplatform.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceIncidentService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final AttendanceIncidentRepository incidentRepository;
    private final StaffRepository staffRepository;

    @Transactional
    public AttendanceIncidentResponse create(CreateAttendanceIncidentRequest request) {
        UUID tenantId = SecurityUtils.requireTenantId();
        UserPrincipal user = SecurityUtils.currentUser();

        Staff staff = staffRepository.findById(request.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        if (!staff.getTenantId().equals(tenantId)) {
            throw new BadRequestException("Staff not in tenant");
        }
        if (user.getBranchId() != null) {
            SecurityUtils.assertBranchAccess(staff.getBranchId());
            if (request.getType() != com.salonplatform.domain.enums.IncidentType.NOTE) {
                SecurityUtils.assertBrandAdminOrAbove();
            }
        } else {
            SecurityUtils.assertBrandAdminOrAbove();
        }

        LocalDate workDate = request.getWorkDate() != null ? request.getWorkDate() : LocalDate.now(ZONE);

        AttendanceIncident incident = incidentRepository.save(AttendanceIncident.builder()
                .tenantId(tenantId)
                .staffId(staff.getId())
                .branchId(staff.getBranchId())
                .attendanceRecordId(request.getAttendanceRecordId())
                .workDate(workDate)
                .type(request.getType())
                .note(request.getNote().trim())
                .penaltyAmount(request.getPenaltyAmount())
                .createdByUserId(user.getId())
                .build());

        return toResponse(incident, staff.getName());
    }

    public PageResponse<AttendanceIncidentResponse> listForStaff(UUID staffId, int page, int size) {
        UUID tenantId = SecurityUtils.requireTenantId();
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        if (!staff.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Staff not found");
        }
        SecurityUtils.assertBranchAccess(staff.getBranchId());

        int p = PageUtils.normalizePage(page);
        int s = PageUtils.normalizeSize(size);
        Page<AttendanceIncident> result = incidentRepository.findByTenantIdAndStaffIdOrderByCreatedAtDesc(
                tenantId, staffId, PageRequest.of(p, s));

        List<AttendanceIncidentResponse> content = result.getContent().stream()
                .map(i -> toResponse(i, staff.getName()))
                .collect(Collectors.toList());

        return PageResponse.<AttendanceIncidentResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    private AttendanceIncidentResponse toResponse(AttendanceIncident i, String staffName) {
        return AttendanceIncidentResponse.builder()
                .id(i.getId())
                .staffId(i.getStaffId())
                .staffName(staffName)
                .branchId(i.getBranchId())
                .attendanceRecordId(i.getAttendanceRecordId())
                .workDate(i.getWorkDate())
                .type(i.getType())
                .note(i.getNote())
                .penaltyAmount(i.getPenaltyAmount())
                .createdByUserId(i.getCreatedByUserId())
                .createdAt(i.getCreatedAt())
                .build();
    }
}
