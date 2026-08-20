package com.salonplatform.service;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.BookingSource;
import com.salonplatform.domain.enums.BookingStatus;
import com.salonplatform.domain.enums.BranchStatus;
import com.salonplatform.domain.enums.CustomerIdentityStatus;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.catalog.BranchServiceResponse;
import com.salonplatform.dto.publicbook.PublicBookCreateAppointmentRequest;
import com.salonplatform.dto.publicbook.PublicBookModels;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.util.VisitPassUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OnlineBookingService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final StaffRepository staffRepository;
    private final BranchServiceRepository branchServiceRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final BookingRepository bookingRepository;
    private final BookingLineItemRepository lineItemRepository;
    private final CustomerRepository customerRepository;
    private final CatalogService catalogService;
    private final CustomerOtpService customerOtpService;
    private final AppointmentNotificationService appointmentNotificationService;
    private final AuditService auditService;

    @Value("${app.public-frontend-base-url:http://localhost:3000}")
    private String publicFrontendBaseUrl;

    @Value("${app.online-booking.otp-required:false}")
    private boolean onlineBookingOtpRequired;

    public PublicBookModels.TenantBranchesResponse listTenantBranches(String tenantSlug) {
        Tenant tenant = requireTenant(tenantSlug);
        List<PublicBookModels.BranchSummary> branches = branchRepository.findByTenantId(tenant.getId()).stream()
                .filter(b -> b.getStatus() == BranchStatus.ACTIVE)
                .filter(b -> isOnlineBookingEffective(tenant, b))
                .map(this::toBranchSummary)
                .sorted(Comparator.comparing(PublicBookModels.BranchSummary::getName))
                .toList();
        return PublicBookModels.TenantBranchesResponse.builder()
                .tenantName(tenant.getName())
                .tenantSlug(tenant.getSlug())
                .primaryColor(tenant.getPrimaryColor())
                .logoUrl(tenant.getLogoUrl())
                .branches(branches)
                .build();
    }

    public PublicBookModels.ContextResponse getBranchContext(String tenantSlug, String branchCode) {
        ResolvedBranch resolved = resolveBranch(tenantSlug, branchCode);
        Branch branch = resolved.branch();
        Tenant tenant = resolved.tenant();
        return PublicBookModels.ContextResponse.builder()
                .tenantName(tenant.getName())
                .tenantSlug(tenant.getSlug())
                .primaryColor(tenant.getPrimaryColor())
                .logoUrl(tenant.getLogoUrl())
                .branchId(branch.getId())
                .branchName(branch.getName())
                .branchCode(branch.getCode())
                .address(branch.getAddress())
                .phone(branch.getPhone())
                .openTime(branch.getOpenTime())
                .closeTime(branch.getCloseTime())
                .onlineBookingEnabled(isOnlineBookingEffective(tenant, branch))
                .minLeadMinutes(branch.getOnlineBookingMinLeadMinutes() != null ? branch.getOnlineBookingMinLeadMinutes() : 60)
                .maxAdvanceDays(branch.getOnlineBookingMaxAdvanceDays() != null ? branch.getOnlineBookingMaxAdvanceDays() : 30)
                .slotMinutes(branch.getOnlineBookingSlotMinutes() != null ? branch.getOnlineBookingSlotMinutes() : 15)
                .bookBaseUrl(buildBookUrl(tenant.getSlug(), branch.getCode()))
                .phoneNumberRequired(branch.getPhoneNumberRequired() == null || branch.getPhoneNumberRequired())
                .otpRequired(onlineBookingOtpRequired)
                .build();
    }

    public List<PublicBookModels.ServiceResponse> listServices(String tenantSlug, String branchCode) {
        ResolvedBranch resolved = resolveBranch(tenantSlug, branchCode);
        assertOnlineBookingEnabled(resolved.tenant(), resolved.branch());
        return catalogService.listBranchServicesPublic(resolved.branch().getId()).stream()
                .filter(BranchServiceResponse::isActive)
                .map(s -> PublicBookModels.ServiceResponse.builder()
                        .branchServiceId(s.getId())
                        .serviceId(s.getServiceId())
                        .name(s.getDisplayNameOverride() != null ? s.getDisplayNameOverride() : s.getServiceName())
                        .categoryId(s.getCategoryId())
                        .categoryName(s.getCategoryName())
                        .parentCategoryId(s.getParentCategoryId())
                        .parentCategoryName(s.getParentCategoryName())
                        .price(s.getPrice())
                        .durationMinutes(s.getDurationMinutes())
                        .build())
                .sorted(Comparator.comparing(PublicBookModels.ServiceResponse::getName))
                .toList();
    }

    public List<PublicBookModels.StaffResponse> listStaff(String tenantSlug, String branchCode) {
        ResolvedBranch resolved = resolveBranch(tenantSlug, branchCode);
        assertOnlineBookingEnabled(resolved.tenant(), resolved.branch());
        return staffRepository.findByTenantIdAndBranchIdAndActiveTrue(resolved.tenant().getId(), resolved.branch().getId())
                .stream()
                .map(s -> PublicBookModels.StaffResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .skills(s.getSkills())
                        .build())
                .sorted(Comparator.comparing(PublicBookModels.StaffResponse::getName))
                .toList();
    }

    public List<PublicBookModels.SlotResponse> listSlots(
            String tenantSlug, String branchCode, LocalDate date, List<UUID> branchServiceIds, UUID staffId) {
        ResolvedBranch resolved = resolveBranch(tenantSlug, branchCode);
        Branch branch = resolved.branch();
        assertOnlineBookingEnabled(resolved.tenant(), branch);

        if (branchServiceIds == null || branchServiceIds.isEmpty()) {
            throw new BadRequestException("At least one service is required");
        }

        if (date == null) {
            throw new BadRequestException("Date is required");
        }
        List<ResolvedService> selectedServices = resolveSelectedServices(branch, branchServiceIds);
        int totalDuration = selectedServices.stream().mapToInt(s -> effectiveDuration(s.durationMinutes())).sum();

        LocalDate today = LocalDate.now(IST);
        int maxAdvance = branch.getOnlineBookingMaxAdvanceDays() != null ? branch.getOnlineBookingMaxAdvanceDays() : 30;
        if (date.isBefore(today) || date.isAfter(today.plusDays(maxAdvance))) {
            return List.of();
        }

        int slotStep = branch.getOnlineBookingSlotMinutes() != null ? branch.getOnlineBookingSlotMinutes() : 15;
        int minLead = branch.getOnlineBookingMinLeadMinutes() != null ? branch.getOnlineBookingMinLeadMinutes() : 60;

        LocalTime open = parseTime(branch.getOpenTime(), LocalTime.of(9, 0));
        LocalTime close = parseTime(branch.getCloseTime(), LocalTime.of(21, 0));
        Instant windowStart = date.atTime(open).atZone(IST).toInstant();
        Instant windowEnd = date.atTime(close).atZone(IST).toInstant();
        Instant earliest = Instant.now().plus(Duration.ofMinutes(minLead));

        List<Staff> staffPool;
        if (staffId != null) {
            Staff staff = staffRepository.findById(staffId)
                    .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));
            if (!staff.getBranchId().equals(branch.getId()) || !staff.isActive()) {
                throw new BadRequestException("Stylist not available");
            }
            staffPool = List.of(staff);
        } else {
            staffPool = staffRepository.findByTenantIdAndBranchIdAndActiveTrue(resolved.tenant().getId(), branch.getId());
        }

        Map<UUID, List<TimeRange>> busyByStaff = loadBusyRanges(resolved.tenant().getId(), branch.getId(), windowStart, windowEnd);

        List<PublicBookModels.SlotResponse> slots = new ArrayList<>();
        for (Staff staff : staffPool) {
            List<TimeRange> busy = busyByStaff.getOrDefault(staff.getId(), List.of());
            Instant cursor = windowStart;
            while (true) {
                Instant slotEnd = cursor.plus(Duration.ofMinutes(totalDuration));
                if (slotEnd.isAfter(windowEnd)) {
                    break;
                }
                if (!cursor.isBefore(earliest) && isFree(cursor, slotEnd, busy)) {
                    slots.add(PublicBookModels.SlotResponse.builder()
                            .startAt(cursor.toString())
                            .endAt(slotEnd.toString())
                            .staffId(staff.getId())
                            .staffName(staff.getName())
                            .build());
                }
                cursor = cursor.plus(Duration.ofMinutes(slotStep));
            }
        }

        slots.sort(Comparator.comparing(PublicBookModels.SlotResponse::getStartAt));
        return slots;
    }

    @Transactional
    public PublicBookModels.AppointmentResponse createAppointment(
            String tenantSlug, String branchCode, PublicBookCreateAppointmentRequest request) {
        ResolvedBranch resolved = resolveBranch(tenantSlug, branchCode);
        Branch branch = resolved.branch();
        Tenant tenant = resolved.tenant();
        assertOnlineBookingEnabled(tenant, branch);

        List<UUID> branchServiceIds = resolveRequestServiceIds(request);
        if (branchServiceIds.isEmpty() || request.getStartAt() == null || request.getStartAt().isBlank()) {
            throw new BadRequestException("Service and time slot are required");
        }
        boolean phoneRequired = branch.getPhoneNumberRequired() == null || branch.getPhoneNumberRequired();
        String normalizedPhone = normalizePhone(request.getPhone());
        if (phoneRequired && normalizedPhone == null) {
            throw new BadRequestException("Valid phone number is required");
        }
        if (onlineBookingOtpRequired) {
            if (normalizedPhone == null) {
                throw new BadRequestException("Phone number is required for verification");
            }
            customerOtpService.verifyOtp(tenant.getId(), normalizedPhone, request.getOtp());
        }

        Instant scheduledStart = Instant.parse(request.getStartAt());
        List<ResolvedService> selectedServices = resolveSelectedServices(branch, branchServiceIds);
        int totalDuration = selectedServices.stream().mapToInt(s -> effectiveDuration(s.durationMinutes())).sum();
        Instant scheduledEnd = scheduledStart.plus(Duration.ofMinutes(totalDuration));

        UUID staffId = request.getStaffId();
        if (staffId == null) {
            LocalDate day = scheduledStart.atZone(IST).toLocalDate();
            List<PublicBookModels.SlotResponse> matching = listSlots(
                            tenantSlug, branchCode, day, branchServiceIds, null)
                    .stream()
                    .filter(s -> s.getStartAt().equals(scheduledStart.toString()))
                    .toList();
            if (matching.isEmpty()) {
                throw new BadRequestException("Selected time slot is no longer available");
            }
            staffId = matching.get(0).getStaffId();
        }

        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));
        if (!staff.getBranchId().equals(branch.getId())) {
            throw new BadRequestException("Stylist not available at this branch");
        }

        Customer customer = findOrCreateCustomer(tenant, branch, request);
        String manageToken = VisitPassUtils.generatePublicToken();
        List<String> serviceNames = selectedServices.stream().map(ResolvedService::displayName).toList();
        String serviceLabel = String.join(" · ", serviceNames);

        Booking booking = bookingRepository.save(Booking.builder()
                .tenantId(tenant.getId())
                .branchId(branch.getId())
                .customerId(customer.getId())
                .createdByUserId(null)
                .status(BookingStatus.CONFIRMED)
                .source(BookingSource.ONLINE)
                .scheduledStartAt(scheduledStart)
                .scheduledEndAt(scheduledEnd)
                .manageToken(manageToken)
                .notes(request.getNote())
                .build());

        for (ResolvedService selected : selectedServices) {
            lineItemRepository.save(BookingLineItem.builder()
                    .bookingId(booking.getId())
                    .branchServiceId(selected.branchService().getId())
                    .serviceId(selected.salonService().getId())
                    .staffId(staff.getId())
                    .serviceName(selected.displayName())
                    .unitPrice(selected.branchService().getPrice())
                    .quantity(1)
                    .gstRate(selected.salonService().getGstRate())
                    .estimatedDurationMinutes(effectiveDuration(selected.durationMinutes()))
                    .startedAt(scheduledStart)
                    .build());
        }

        auditService.logSystem(tenant.getId(), branch.getId(), "ONLINE_BOOKING", "Booking", booking.getId(), "Customer online appointment");

        appointmentNotificationService.sendConfirmation(booking, branch, customer, serviceLabel, staff.getName());

        String confirmationCode = booking.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return PublicBookModels.AppointmentResponse.builder()
                .bookingId(booking.getId())
                .confirmationCode(confirmationCode)
                .manageUrl(buildManageUrl(tenant.getSlug(), branch.getCode(), manageToken))
                .scheduledStartAt(scheduledStart.toString())
                .scheduledEndAt(scheduledEnd.toString())
                .serviceName(serviceLabel)
                .serviceNames(serviceNames)
                .staffName(staff.getName())
                .branchName(branch.getName())
                .customerName(customer.getName())
                .visitPassId(customer.getVisitPassId())
                .visitPassUrl(buildVisitPassUrl(customer.getPassPublicToken()))
                .build();
    }

    private static List<UUID> resolveRequestServiceIds(PublicBookCreateAppointmentRequest request) {
        if (request.getBranchServiceIds() != null && !request.getBranchServiceIds().isEmpty()) {
            return request.getBranchServiceIds();
        }
        if (request.getBranchServiceId() != null) {
            return List.of(request.getBranchServiceId());
        }
        return List.of();
    }

    private List<ResolvedService> resolveSelectedServices(Branch branch, List<UUID> branchServiceIds) {
        Set<UUID> seen = new HashSet<>();
        List<ResolvedService> resolved = new ArrayList<>();
        for (UUID branchServiceId : branchServiceIds) {
            if (!seen.add(branchServiceId)) {
                throw new BadRequestException("Duplicate service in booking");
            }
            BranchService bs = branchServiceRepository.findById(branchServiceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
            if (!bs.getBranchId().equals(branch.getId()) || !bs.isActive()) {
                throw new BadRequestException("Service not available at this branch");
            }
            SalonService svc = salonServiceRepository.findById(bs.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
            if (!svc.isActive()) {
                throw new BadRequestException("Service not available");
            }
            String displayName = bs.getDisplayNameOverride() != null ? bs.getDisplayNameOverride() : svc.getName();
            resolved.add(new ResolvedService(bs, svc, displayName));
        }
        return resolved;
    }

    private record ResolvedService(BranchService branchService, SalonService salonService, String displayName) {
        int durationMinutes() {
            return salonService.getDurationMinutes();
        }
    }

    @Transactional
    public void checkInOnlineBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException("Only confirmed online appointments can be checked in");
        }
        Instant now = Instant.now();
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setServiceStartedAt(now);
        booking.setEstimatedEndAt(booking.getScheduledEndAt() != null
                ? booking.getScheduledEndAt()
                : now.plus(Duration.ofMinutes(45)));
        bookingRepository.save(booking);

        for (BookingLineItem line : lineItemRepository.findByBookingId(bookingId)) {
            line.setStartedAt(now);
            lineItemRepository.save(line);
        }
        auditService.log("CHECK_IN_ONLINE", "Booking", bookingId, "Online appointment checked in");
    }

    public PublicBookModels.OtpResponse sendOtp(String tenantSlug, String branchCode, String phone) {
        ResolvedBranch resolved = resolveBranch(tenantSlug, branchCode);
        assertOnlineBookingEnabled(resolved.tenant(), resolved.branch());
        return customerOtpService.sendOtp(resolved.tenant().getId(), phone);
    }

    public String buildBookUrl(String tenantSlug, String branchCode) {
        String base = publicFrontendBaseUrl.endsWith("/")
                ? publicFrontendBaseUrl.substring(0, publicFrontendBaseUrl.length() - 1)
                : publicFrontendBaseUrl;
        return base + "/book/" + tenantSlug + "/" + branchCode.toLowerCase(Locale.ROOT) + "/";
    }

    private String buildManageUrl(String tenantSlug, String branchCode, String token) {
        return buildBookUrl(tenantSlug, branchCode) + "?manage=" + token;
    }

    private Customer findOrCreateCustomer(Tenant tenant, Branch branch, PublicBookCreateAppointmentRequest request) {
        boolean phoneRequired = branch.getPhoneNumberRequired() == null || branch.getPhoneNumberRequired();
        String phone = normalizePhone(request.getPhone());
        if (phoneRequired && phone == null) {
            throw new BadRequestException("Valid phone number is required");
        }

        String name = request.getCustomerName() != null && !request.getCustomerName().isBlank()
                ? request.getCustomerName().trim() : "Guest";

        if (phone != null) {
            Optional<Customer> existing = customerRepository.findByTenantIdAndPhone(tenant.getId(), phone);
            if (existing.isPresent()) {
                Customer c = existing.get();
                c.setName(name);
                if (request.getSociety() != null && !request.getSociety().isBlank()) {
                    c.setSociety(request.getSociety().trim());
                }
                if (request.getFlatUnit() != null && !request.getFlatUnit().isBlank()) {
                    c.setFlatUnit(request.getFlatUnit().trim());
                }
                c.setWhatsappOptIn(true);
                c.setSmsOptIn(true);
                return customerRepository.save(c);
            }
        }

        CustomerIdentityStatus status = phone != null
                ? CustomerIdentityStatus.PHONE_VERIFIED
                : CustomerIdentityStatus.PASS_ONLY;

        return customerRepository.save(Customer.builder()
                .tenantId(tenant.getId())
                .name(name)
                .phone(phone)
                .visitPassId(uniqueVisitPassId(tenant, branch.getCode()))
                .identityStatus(status)
                .passPublicToken(VisitPassUtils.generatePublicToken())
                .society(request.getSociety())
                .flatUnit(request.getFlatUnit())
                .whatsappOptIn(phone != null)
                .smsOptIn(phone != null)
                .visitCount(0)
                .lifetimeSpend(java.math.BigDecimal.ZERO)
                .build());
    }

    private String uniqueVisitPassId(Tenant tenant, String branchCode) {
        for (int i = 0; i < 20; i++) {
            String candidate = VisitPassUtils.generateVisitPassId(tenant, branchCode);
            if (customerRepository.findByTenantIdAndVisitPassId(tenant.getId(), candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BadRequestException("Could not generate visit pass — please try again");
    }

    private String buildVisitPassUrl(String passPublicToken) {
        if (passPublicToken == null || passPublicToken.isBlank()) {
            return null;
        }
        String base = publicFrontendBaseUrl.endsWith("/")
                ? publicFrontendBaseUrl.substring(0, publicFrontendBaseUrl.length() - 1)
                : publicFrontendBaseUrl;
        return base + "/pass/?token=" + passPublicToken;
    }

    private Map<UUID, List<TimeRange>> loadBusyRanges(UUID tenantId, UUID branchId, Instant windowStart, Instant windowEnd) {
        List<Booking> bookings = new ArrayList<>();
        bookings.addAll(bookingRepository.findByBranchAndDateRange(tenantId, branchId, windowStart, windowEnd));
        bookings.addAll(bookingRepository.findConfirmedScheduledBetween(tenantId, branchId, windowStart, windowEnd));

        Set<UUID> seen = new HashSet<>();
        bookings = bookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .filter(b -> seen.add(b.getId()))
                .toList();

        Map<UUID, List<BookingLineItem>> linesByBooking = bookings.stream()
                .collect(Collectors.toMap(Booking::getId, b -> lineItemRepository.findByBookingId(b.getId())));

        Map<UUID, List<TimeRange>> busy = new HashMap<>();
        for (Booking booking : bookings) {
            Instant start = resolveBookingStart(booking, linesByBooking.get(booking.getId()));
            Instant end = resolveBookingEnd(booking, start, linesByBooking.get(booking.getId()));
            for (BookingLineItem line : linesByBooking.getOrDefault(booking.getId(), List.of())) {
                if (line.getStaffId() == null) continue;
                busy.computeIfAbsent(line.getStaffId(), k -> new ArrayList<>())
                        .add(new TimeRange(start, end));
            }
        }
        return busy;
    }

    private static Instant resolveBookingStart(Booking booking, List<BookingLineItem> lines) {
        if (booking.getScheduledStartAt() != null) {
            return booking.getScheduledStartAt();
        }
        return lines.stream()
                .map(BookingLineItem::getStartedAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(booking.getServiceStartedAt() != null ? booking.getServiceStartedAt() : booking.getCreatedAt());
    }

    private static Instant resolveBookingEnd(Booking booking, Instant start, List<BookingLineItem> lines) {
        if (booking.getScheduledEndAt() != null) {
            return booking.getScheduledEndAt();
        }
        if (booking.getEstimatedEndAt() != null) {
            return booking.getEstimatedEndAt();
        }
        int mins = lines.stream()
                .mapToInt(l -> effectiveDuration(l.getEstimatedDurationMinutes()) * Math.max(1, l.getQuantity() != null ? l.getQuantity() : 1))
                .sum();
        return start.plus(Duration.ofMinutes(Math.max(mins, 30)));
    }

    private static boolean isFree(Instant start, Instant end, List<TimeRange> busy) {
        for (TimeRange range : busy) {
            if (start.isBefore(range.end()) && end.isAfter(range.start())) {
                return false;
            }
        }
        return true;
    }

    private ResolvedBranch resolveBranch(String tenantSlug, String branchCode) {
        Tenant tenant = requireTenant(tenantSlug);
        String normalizedCode = branchCode != null ? branchCode.trim().toUpperCase(Locale.ROOT) : "";
        Branch branch = branchRepository.findByTenantIdAndCode(tenant.getId(), normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new BadRequestException("Branch is not accepting bookings");
        }
        return new ResolvedBranch(tenant, branch);
    }

    private Tenant requireTenant(String tenantSlug) {
        return tenantRepository.findBySlug(tenantSlug.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Salon not found"));
    }

    private boolean isOnlineBookingEffective(Tenant tenant, Branch branch) {
        return Boolean.TRUE.equals(tenant.getOnlineBookingEnabled())
                && Boolean.TRUE.equals(branch.getOnlineBookingEnabled());
    }

    private void assertOnlineBookingEnabled(Tenant tenant, Branch branch) {
        if (!isOnlineBookingEffective(tenant, branch)) {
            if (tenant.getOnlineBookingEnabled() != null && !tenant.getOnlineBookingEnabled()) {
                throw new BadRequestException("Online booking is disabled for this brand");
            }
            throw new BadRequestException("Online booking is not enabled for this branch");
        }
    }

    private void assertOnlineBookingEnabled(Branch branch) {
        Tenant tenant = tenantRepository.findById(branch.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Salon not found"));
        assertOnlineBookingEnabled(tenant, branch);
    }

    private PublicBookModels.BranchSummary toBranchSummary(Branch branch) {
        return PublicBookModels.BranchSummary.builder()
                .id(branch.getId())
                .name(branch.getName())
                .code(branch.getCode())
                .address(branch.getAddress())
                .phone(branch.getPhone())
                .openTime(branch.getOpenTime())
                .closeTime(branch.getCloseTime())
                .build();
    }

    private static int effectiveDuration(Integer minutes) {
        return minutes != null && minutes > 0 ? minutes : 30;
    }

    private static LocalTime parseTime(String raw, LocalTime fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalTime.parse(raw.trim().length() == 5 ? raw.trim() : raw.trim().substring(0, 5));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String normalizePhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() >= 10) {
            return digits.substring(digits.length() - 10);
        }
        return digits.isEmpty() ? null : digits;
    }

    private record ResolvedBranch(Tenant tenant, Branch branch) {}
    private record TimeRange(Instant start, Instant end) {}
}
