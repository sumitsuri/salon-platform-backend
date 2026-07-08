package com.salonplatform.seed;

import com.salonplatform.domain.entity.AttendanceRecord;
import com.salonplatform.domain.entity.LeaveRecord;
import com.salonplatform.domain.entity.Staff;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.enums.AttendanceMethod;
import com.salonplatform.domain.enums.LeaveStatus;
import com.salonplatform.domain.enums.LeaveType;
import com.salonplatform.domain.enums.TenantStatus;
import com.salonplatform.domain.repository.AttendanceRecordRepository;
import com.salonplatform.domain.repository.LeaveRecordRepository;
import com.salonplatform.domain.repository.StaffRepository;
import com.salonplatform.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Random;

@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class AttendanceSeeder implements CommandLineRunner {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final TenantRepository tenantRepository;
    private final StaffRepository staffRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final LeaveRecordRepository leaveRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Tenant tenant = tenantRepository.findBySlug("demo-brand").orElse(null);
        if (tenant == null || tenant.getStatus() != TenantStatus.ACTIVE) return;

        long existing = attendanceRepository.findByTenantIdAndWorkDateBetweenOrderByEntryTimeDesc(
                tenant.getId(), LocalDate.now(ZONE).minusDays(60), LocalDate.now(ZONE)).size();
        if (existing >= 50) {
            log.info("Attendance demo data already seeded ({} records)", existing);
            return;
        }

        List<Staff> staffList = staffRepository.findByTenantId(tenant.getId());

        for (Staff staff : staffList) {
            if (staff.getBiometricId() == null) {
                String fp = switch (staff.getName()) {
                    case "Amit" -> "FP-AMIT-LITHOS";
                    case "Priya" -> "FP-PRIYA-LITHOS";
                    case "Ravi" -> "FP-RAVI-WEBCITY";
                    case "Sneha" -> "FP-SNEHA-WEBCITY";
                    default -> "FP-" + staff.getName().toUpperCase().replace(" ", "-");
                };
                staff.setBiometricId(fp);
                staffRepository.save(staff);
            }
        }

        Random random = new Random(42);
        LocalDate today = LocalDate.now(ZONE);

        for (int dayOffset = 59; dayOffset >= 0; dayOffset--) {
            LocalDate workDate = today.minusDays(dayOffset);
            for (Staff staff : staffList) {
                if (random.nextDouble() < 0.12) continue;

                boolean late = random.nextDouble() < 0.2;
                LocalTime entryTime = late
                        ? LocalTime.of(9, 45).plusMinutes(random.nextInt(45))
                        : LocalTime.of(9, 0).plusMinutes(random.nextInt(25));
                LocalTime exitTime = LocalTime.of(18, random.nextInt(60));

                Instant entry = workDate.atTime(entryTime).atZone(ZONE).toInstant();
                Instant exit = workDate.atTime(exitTime).atZone(ZONE).toInstant();

                attendanceRepository.save(AttendanceRecord.builder()
                        .tenantId(tenant.getId())
                        .branchId(staff.getBranchId())
                        .staffId(staff.getId())
                        .workDate(workDate)
                        .entryTime(entry)
                        .exitTime(exit)
                        .entryMethod(random.nextBoolean() ? AttendanceMethod.BIOMETRIC : AttendanceMethod.MANUAL)
                        .exitMethod(AttendanceMethod.BIOMETRIC)
                        .build());
            }
        }

        Staff priyaL = staffList.stream().filter(s -> "Priya".equals(s.getName())).findFirst().orElse(null);
        if (priyaL != null) {
            leaveRepository.save(LeaveRecord.builder()
                    .tenantId(tenant.getId())
                    .branchId(priyaL.getBranchId())
                    .staffId(priyaL.getId())
                    .startDate(today.minusDays(3))
                    .endDate(today.minusDays(1))
                    .leaveType(LeaveType.FULL_DAY)
                    .status(LeaveStatus.APPROVED)
                    .reason("Family function")
                    .build());
        }

        Staff ravi = staffList.stream().filter(s -> "Ravi".equals(s.getName())).findFirst().orElse(null);
        if (ravi != null) {
            leaveRepository.save(LeaveRecord.builder()
                    .tenantId(tenant.getId())
                    .branchId(ravi.getBranchId())
                    .staffId(ravi.getId())
                    .startDate(today.plusDays(2))
                    .endDate(today.plusDays(4))
                    .leaveType(LeaveType.FULL_DAY)
                    .status(LeaveStatus.APPROVED)
                    .reason("Personal leave")
                    .build());
        }

        log.info("Seeded attendance demo data for {} staff", staffList.size());
    }
}
