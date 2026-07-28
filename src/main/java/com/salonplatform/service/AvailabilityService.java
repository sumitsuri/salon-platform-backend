package com.salonplatform.service;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.BookingStatus;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.availability.*;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int DEFAULT_DURATION = 30;
    private static final int METRICS_LOOKBACK_DAYS = 30;

    private final BranchRepository branchRepository;
    private final StaffRepository staffRepository;
    private final BookingRepository bookingRepository;
    private final BookingLineItemRepository lineItemRepository;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public BranchAvailabilityResponse getBranchDay(UUID branchId, LocalDate date) {
        SecurityUtils.assertBranchAccess(branchId);
        UUID tenantId = SecurityUtils.requireTenantId();
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        LocalDate day = date != null ? date : LocalDate.now(IST);
        Instant dayStart = day.atStartOfDay(IST).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(IST).toInstant();
        Instant now = Instant.now();
        boolean isToday = day.equals(LocalDate.now(IST));

        List<Staff> staffList = staffRepository.findByTenantIdAndBranchIdAndActiveTrue(tenantId, branchId);
        List<Booking> dayBookings = bookingRepository.findByBranchAndDateRange(tenantId, branchId, dayStart, dayEnd)
                .stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.toCollection(ArrayList::new));

        if (isToday) {
            Set<UUID> seen = dayBookings.stream().map(Booking::getId).collect(Collectors.toSet());
            for (Booking open : bookingRepository.findByTenantIdAndBranchIdAndStatus(tenantId, branchId, BookingStatus.IN_PROGRESS)) {
                if (seen.add(open.getId())) dayBookings.add(open);
            }
            for (Booking open : bookingRepository.findByTenantIdAndBranchIdAndStatus(tenantId, branchId, BookingStatus.READY_FOR_BILLING)) {
                if (seen.add(open.getId())) dayBookings.add(open);
            }
        }

        Map<UUID, List<BookingLineItem>> linesByBooking = new HashMap<>();
        Map<UUID, Customer> customers = new HashMap<>();
        for (Booking b : dayBookings) {
            linesByBooking.put(b.getId(), lineItemRepository.findByBookingId(b.getId()));
            customers.computeIfAbsent(b.getCustomerId(), id -> customerRepository.findById(id).orElse(null));
        }

        LocalTime open = parseTime(branch.getOpenTime(), LocalTime.of(9, 0));
        LocalTime close = parseTime(branch.getCloseTime(), LocalTime.of(21, 0));
        Instant windowStart = day.atTime(open).atZone(IST).toInstant();
        Instant windowEnd = day.atTime(close).atZone(IST).toInstant();

        List<StaffAvailabilityColumn> columns = new ArrayList<>();
        int free = 0;
        int busy = 0;

        for (Staff staff : staffList) {
            List<StaffTimeBlock> blocks = new ArrayList<>();
            for (Booking booking : dayBookings) {
                List<BookingLineItem> lines = linesByBooking.getOrDefault(booking.getId(), List.of()).stream()
                        .filter(l -> staff.getId().equals(l.getStaffId()))
                        .toList();
                if (lines.isEmpty()) continue;

                Instant start = resolveStart(booking, lines);
                int estMinutes = lines.stream()
                        .mapToInt(l -> effectiveDuration(l) * Math.max(1, l.getQuantity() != null ? l.getQuantity() : 1))
                        .sum();
                Instant end = resolveEnd(booking, start, estMinutes);
                boolean openVisit = booking.getStatus() == BookingStatus.IN_PROGRESS
                        || booking.getStatus() == BookingStatus.READY_FOR_BILLING;
                boolean overdue = openVisit && end.isBefore(now);

                Customer customer = customers.get(booking.getCustomerId());
                blocks.add(StaffTimeBlock.builder()
                        .bookingId(booking.getId())
                        .customerId(booking.getCustomerId())
                        .customerName(customer != null ? customer.getName() : "Guest")
                        .customerPhone(customer != null ? customer.getPhone() : null)
                        .status(booking.getStatus().name())
                        .startAt(start)
                        .endAt(end)
                        .estimatedMinutes(estMinutes)
                        .actualMinutes(booking.getActualDurationMinutes())
                        .overdue(overdue)
                        .services(lines.stream().map(BookingLineItem::getServiceName).distinct().toList())
                        .build());
            }
            blocks.sort(Comparator.comparing(StaffTimeBlock::getStartAt));

            Instant busyUntil = null;
            Integer remaining = null;
            String occupancy = "FREE";
            if (isToday) {
                Optional<StaffTimeBlock> active = blocks.stream()
                        .filter(b -> isOpenStatus(b.getStatus()))
                        .filter(b -> !b.getStartAt().isAfter(now))
                        .filter(b -> b.isOverdue() || !b.getEndAt().isBefore(now))
                        .max(Comparator.comparing(StaffTimeBlock::getEndAt));

                if (active.isPresent()) {
                    StaffTimeBlock b = active.get();
                    busyUntil = b.isOverdue() ? now : b.getEndAt();
                    remaining = b.isOverdue() ? 0 : (int) Duration.between(now, b.getEndAt()).toMinutes();
                    occupancy = b.isOverdue() ? "OVERDUE" : "BUSY";
                    busy++;
                } else {
                    free++;
                }
            } else {
                free++;
            }

            columns.add(StaffAvailabilityColumn.builder()
                    .staffId(staff.getId())
                    .staffName(staff.getName())
                    .skills(staff.getSkills())
                    .occupancy(occupancy)
                    .busyUntil(busyUntil)
                    .remainingMinutes(remaining)
                    .blocks(blocks)
                    .freeSlots(computeFreeSlots(blocks, windowStart, windowEnd, now, isToday))
                    .build());
        }

        return BranchAvailabilityResponse.builder()
                .branchId(branch.getId())
                .branchName(branch.getName())
                .date(day)
                .openTime(open.toString().substring(0, 5))
                .closeTime(close.toString().substring(0, 5))
                .now(now)
                .freeStaffCount(free)
                .busyStaffCount(busy)
                .staff(columns)
                .metrics(buildMetrics(tenantId, branchId, now))
                .build();
    }

    private DurationMetrics buildMetrics(UUID tenantId, UUID branchId, Instant now) {
        Instant from = now.minus(Duration.ofDays(METRICS_LOOKBACK_DAYS));
        List<Booking> completed = bookingRepository.findByBranchAndDateRange(tenantId, branchId, from, now).stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .filter(b -> b.getActualDurationMinutes() != null || b.getCompletedAt() != null)
                .toList();

        List<Integer> visitMinutes = completed.stream()
                .map(b -> b.getActualDurationMinutes() != null
                        ? b.getActualDurationMinutes()
                        : (int) Duration.between(
                        b.getServiceStartedAt() != null ? b.getServiceStartedAt() : b.getCreatedAt(),
                        b.getCompletedAt()).toMinutes())
                .filter(m -> m > 0 && m < 12 * 60)
                .filter(m -> m >= 5) // ignore near-instant demo / mis-timed visits
                .sorted()
                .toList();

        Double avg = visitMinutes.isEmpty() ? null
                : visitMinutes.stream().mapToInt(Integer::intValue).average().orElse(0);
        Double median = visitMinutes.isEmpty() ? null
                : (visitMinutes.size() % 2 == 1
                ? (double) visitMinutes.get(visitMinutes.size() / 2)
                : (visitMinutes.get(visitMinutes.size() / 2 - 1) + visitMinutes.get(visitMinutes.size() / 2)) / 2.0);

        Map<String, List<BookingLineItem>> grouped = new LinkedHashMap<>();
        Map<UUID, String> staffNames = new HashMap<>();
        for (Booking b : completed) {
            for (BookingLineItem line : lineItemRepository.findByBookingId(b.getId())) {
                if (line.getActualDurationMinutes() == null && line.getEstimatedDurationMinutes() == null) {
                    continue;
                }
                String key = line.getStaffId() + "|" + line.getServiceId();
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
                staffNames.computeIfAbsent(line.getStaffId(),
                        id -> staffRepository.findById(id).map(Staff::getName).orElse("Staff"));
            }
        }

        List<StaffServiceDurationStat> stats = grouped.entrySet().stream()
                .map(e -> {
                    List<BookingLineItem> lines = e.getValue();
                    BookingLineItem sample = lines.get(0);
                    double avgEst = lines.stream()
                            .mapToInt(l -> l.getEstimatedDurationMinutes() != null ? l.getEstimatedDurationMinutes() : DEFAULT_DURATION)
                            .average().orElse(DEFAULT_DURATION);
                    List<Integer> actuals = lines.stream()
                            .map(BookingLineItem::getActualDurationMinutes)
                            .filter(Objects::nonNull)
                            .filter(m -> m > 0)
                            .toList();
                    Double avgAct = actuals.isEmpty() ? null
                            : actuals.stream().mapToInt(Integer::intValue).average().orElse(0);
                    return StaffServiceDurationStat.builder()
                            .staffId(sample.getStaffId())
                            .staffName(staffNames.get(sample.getStaffId()))
                            .serviceId(sample.getServiceId())
                            .serviceName(sample.getServiceName())
                            .sampleCount(lines.size())
                            .avgEstimatedMinutes(round1(avgEst))
                            .avgActualMinutes(avgAct != null ? round1(avgAct) : null)
                            .build();
                })
                .sorted(Comparator.comparing(StaffServiceDurationStat::getSampleCount).reversed())
                .limit(12)
                .toList();

        return DurationMetrics.builder()
                .sampleVisitCount(visitMinutes.size())
                .avgVisitMinutes(avg != null ? round1(avg) : null)
                .medianVisitMinutes(median != null ? round1(median) : null)
                .byStaffService(stats)
                .build();
    }

    private static List<FreeSlot> computeFreeSlots(
            List<StaffTimeBlock> blocks, Instant windowStart, Instant windowEnd, Instant now, boolean isToday) {
        Instant cursor = windowStart;
        if (isToday && now.isAfter(cursor)) {
            cursor = now;
        }
        if (!cursor.isBefore(windowEnd)) {
            return List.of();
        }
        final Instant slotCursor = cursor;

        List<StaffTimeBlock> relevant = blocks.stream()
                .filter(b -> b.getEndAt().isAfter(slotCursor) && b.getStartAt().isBefore(windowEnd))
                .sorted(Comparator.comparing(StaffTimeBlock::getStartAt))
                .toList();

        List<FreeSlot> slots = new ArrayList<>();
        Instant walk = slotCursor;
        for (StaffTimeBlock block : relevant) {
            Instant blockStart = block.getStartAt().isBefore(walk) ? walk : block.getStartAt();
            if (walk.isBefore(blockStart)) {
                int mins = (int) Duration.between(walk, blockStart).toMinutes();
                if (mins >= 10) {
                    slots.add(FreeSlot.builder().startAt(walk).endAt(blockStart).minutes(mins).build());
                }
            }
            if (block.getEndAt().isAfter(walk)) {
                walk = block.getEndAt();
            }
        }
        if (walk.isBefore(windowEnd)) {
            int mins = (int) Duration.between(walk, windowEnd).toMinutes();
            if (mins >= 10) {
                slots.add(FreeSlot.builder().startAt(walk).endAt(windowEnd).minutes(mins).build());
            }
        }
        return slots;
    }

    private static Instant resolveStart(Booking booking, List<BookingLineItem> lines) {
        return lines.stream()
                .map(BookingLineItem::getStartedAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(booking.getServiceStartedAt() != null ? booking.getServiceStartedAt() : booking.getCreatedAt());
    }

    private static Instant resolveEnd(Booking booking, Instant start, int estMinutes) {
        if (booking.getCompletedAt() != null) {
            return booking.getCompletedAt();
        }
        return start.plus(Duration.ofMinutes(Math.max(estMinutes, DEFAULT_DURATION)));
    }

    private static int effectiveDuration(BookingLineItem line) {
        return line.getEstimatedDurationMinutes() != null && line.getEstimatedDurationMinutes() > 0
                ? line.getEstimatedDurationMinutes()
                : DEFAULT_DURATION;
    }

    private static boolean isOpenStatus(String status) {
        return "IN_PROGRESS".equals(status) || "READY_FOR_BILLING".equals(status);
    }

    private static LocalTime parseTime(String raw, LocalTime fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalTime.parse(raw.trim().length() == 5 ? raw.trim() : raw.trim().substring(0, 5));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
