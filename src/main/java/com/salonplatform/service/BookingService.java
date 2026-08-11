package com.salonplatform.service;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.BookingStatus;
import com.salonplatform.domain.enums.PaymentMode;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.billing.BillPreviewResponse;
import com.salonplatform.dto.booking.*;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.membership.SellMembershipRequest;
import com.salonplatform.dto.payment.RecordPaymentRequest;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.repository.BookingSpecifications;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import com.salonplatform.util.InvoiceBillUtils;
import com.salonplatform.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final List<Integer> ALLOWED_PAGE_SIZES = PageUtils.ALLOWED_PAGE_SIZES;

    private final BookingRepository bookingRepository;
    private final BookingLineItemRepository lineItemRepository;
    private final BranchRepository branchRepository;
    private final CustomerRepository customerRepository;
    private final BranchServiceRepository branchServiceRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final StaffRepository staffRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceSequenceRepository invoiceSequenceRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final GstCalculationService gstCalculationService;
    private final AuditService auditService;
    private final BillReceiptNotificationService billReceiptNotificationService;
    private final PromoResolutionService promoResolutionService;
    private final MembershipService membershipService;
    private final InvoicePdfService invoicePdfService;
    private final com.salonplatform.reviews.domain.port.ReviewInvitationPort reviewInvitationPort;

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBranchAccess(request.getBranchId());

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        if (request.getLines().isEmpty()) {
            throw new BadRequestException("error.booking.servicesRequired");
        }
        if (request.getCouponId() != null && request.getOfferId() != null) {
            throw new BadRequestException("Select either a coupon or an offer, not both");
        }

        GstCalculationService.PromoContext promo = promoResolutionService.resolveForBooking(
                tenantId, request.getBranchId(), customer.getId(),
                request.getCouponId(), request.getOfferId(), request.getPendingMembershipPlanId());

        assertPendingMembershipAllowed(tenantId, request.getBranchId(), customer.getId(), request.getPendingMembershipPlanId());

        Booking booking = bookingRepository.save(Booking.builder()
                .tenantId(tenantId)
                .branchId(request.getBranchId())
                .customerId(customer.getId())
                .createdByUserId(user.getId())
                .status(BookingStatus.IN_PROGRESS)
                .serviceStartedAt(Instant.now())
                .notes(request.getNotes())
                .billDiscountType(promo.getCoupon() == null && promo.getOffer() == null
                        ? request.getBillDiscountType() : null)
                .billDiscountValue(promo.getCoupon() == null && promo.getOffer() == null
                        ? request.getBillDiscountValue() : null)
                .billDiscountNote(promo.getCoupon() == null && promo.getOffer() == null
                        ? request.getBillDiscountNote() : null)
                .couponId(promo.getCoupon() != null ? promo.getCoupon().getId() : null)
                .offerId(promo.getOffer() != null ? promo.getOffer().getId() : null)
                .membershipSubscriptionId(promo.getMembershipSubscription() != null
                        ? promo.getMembershipSubscription().getId() : null)
                .pendingMembershipPlanId(request.getPendingMembershipPlanId())
                .build());

        for (BookingLineRequest lineReq : request.getLines()) {
            saveLine(booking.getId(), lineReq, booking.getServiceStartedAt());
        }
        refreshEstimatedEnd(booking);

        boolean keepOpen = Boolean.TRUE.equals(request.getKeepOpen());
        booking.setStatus(keepOpen ? BookingStatus.IN_PROGRESS : BookingStatus.READY_FOR_BILLING);
        persistPromoAmounts(booking, promo);
        bookingRepository.save(booking);
        auditService.log(
                keepOpen ? "OPEN_VISIT" : "CREATE_BOOKING",
                "Booking",
                booking.getId(),
                keepOpen ? "Walk-in visit kept open" : "Walk-in ready for billing");
        return toResponse(booking, branch, customer);
    }

    @Transactional
    public BookingResponse replaceLines(UUID bookingId, UpdateBookingLinesRequest request) {
        Booking booking = requireEditableBooking(bookingId);
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BadRequestException("error.booking.servicesRequired");
        }

        lineItemRepository.deleteByBookingId(bookingId);
        Instant start = booking.getServiceStartedAt() != null ? booking.getServiceStartedAt() : Instant.now();
        if (booking.getServiceStartedAt() == null) {
            booking.setServiceStartedAt(start);
        }
        for (BookingLineRequest lineReq : request.getLines()) {
            saveLine(bookingId, lineReq, start);
        }
        refreshEstimatedEnd(booking);

        // Editing services brings the visit back to in-progress until billed.
        if (booking.getStatus() == BookingStatus.READY_FOR_BILLING) {
            booking.setStatus(BookingStatus.IN_PROGRESS);
        }
        persistPromoAmounts(booking, promoContextFor(booking));
        bookingRepository.save(booking);
        auditService.log("UPDATE_BOOKING_LINES", "Booking", bookingId, "Services updated on open visit");

        Branch branch = branchRepository.findById(booking.getBranchId()).orElseThrow();
        Customer customer = customerRepository.findById(booking.getCustomerId()).orElseThrow();
        return toResponse(booking, branch, customer);
    }

    @Transactional
    public BookingResponse markReadyForBilling(UUID bookingId) {
        Booking booking = requireEditableBooking(bookingId);
        List<BookingLineItem> lines = lineItemRepository.findByBookingId(bookingId);
        if (lines.isEmpty()) {
            throw new BadRequestException("error.booking.servicesRequired");
        }
        for (BookingLineItem line : lines) {
            if (line.getStaffId() == null) {
                throw new BadRequestException("error.booking.staffRequired");
            }
        }
        booking.setStatus(BookingStatus.READY_FOR_BILLING);
        persistPromoAmounts(booking, promoContextFor(booking));
        bookingRepository.save(booking);
        auditService.log("READY_FOR_BILLING", "Booking", bookingId, null);

        Branch branch = branchRepository.findById(booking.getBranchId()).orElseThrow();
        Customer customer = customerRepository.findById(booking.getCustomerId()).orElseThrow();
        return toResponse(booking, branch, customer);
    }

    @Transactional
    public BookingResponse reopenVisit(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Cannot reopen this booking");
        }
        booking.setStatus(BookingStatus.IN_PROGRESS);
        bookingRepository.save(booking);
        auditService.log("REOPEN_VISIT", "Booking", bookingId, null);

        Branch branch = branchRepository.findById(booking.getBranchId()).orElseThrow();
        Customer customer = customerRepository.findById(booking.getCustomerId()).orElseThrow();
        return toResponse(booking, branch, customer);
    }

    private Booking requireEditableBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Cannot change services on this booking");
        }
        return booking;
    }

    private void saveLine(UUID bookingId, BookingLineRequest lineReq, Instant startedAt) {
        if (lineReq.getStaffId() == null) {
            throw new BadRequestException("error.booking.staffRequired");
        }
        BranchService bs = branchServiceRepository.findById(lineReq.getBranchServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch service not found"));
        SalonService svc = salonServiceRepository.findById(bs.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        staffRepository.findById(lineReq.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        int duration = svc.getDurationMinutes() != null && svc.getDurationMinutes() > 0
                ? svc.getDurationMinutes() : 30;

        BigDecimal unitPrice = bs.getPrice();
        if (lineReq.getUnitPrice() != null) {
            if (!svc.isVariablePricing()) {
                throw new BadRequestException("Unit price override is not allowed for this service");
            }
            if (lineReq.getUnitPrice().compareTo(bs.getPrice()) < 0) {
                throw new BadRequestException("Unit price cannot be below the list price");
            }
            unitPrice = lineReq.getUnitPrice();
        }

        lineItemRepository.save(BookingLineItem.builder()
                .bookingId(bookingId)
                .branchServiceId(bs.getId())
                .serviceId(svc.getId())
                .staffId(lineReq.getStaffId())
                .serviceName(bs.getDisplayNameOverride() != null ? bs.getDisplayNameOverride() : svc.getName())
                .unitPrice(unitPrice)
                .quantity(lineReq.getQuantity() != null ? lineReq.getQuantity() : 1)
                .gstRate(svc.getGstRate())
                .lineDiscountType(lineReq.getLineDiscountType())
                .lineDiscountValue(lineReq.getLineDiscountValue())
                .lineDiscountNote(lineReq.getLineDiscountNote())
                .estimatedDurationMinutes(duration)
                .startedAt(startedAt)
                .build());
    }

    private void refreshEstimatedEnd(Booking booking) {
        List<BookingLineItem> lines = lineItemRepository.findByBookingId(booking.getId());
        Instant start = booking.getServiceStartedAt() != null ? booking.getServiceStartedAt() : Instant.now();
        Map<UUID, Integer> loadByStaff = new HashMap<>();
        for (BookingLineItem line : lines) {
            int mins = (line.getEstimatedDurationMinutes() != null ? line.getEstimatedDurationMinutes() : 30)
                    * Math.max(1, line.getQuantity() != null ? line.getQuantity() : 1);
            loadByStaff.merge(line.getStaffId(), mins, Integer::sum);
        }
        int maxLoad = loadByStaff.values().stream().mapToInt(Integer::intValue).max().orElse(30);
        booking.setEstimatedEndAt(start.plus(Duration.ofMinutes(maxLoad)));
    }

    @Transactional
    public BookingResponse applyPromo(UUID bookingId, ApplyPromoRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Cannot change promo on this booking");
        }

        UUID couponId = Boolean.TRUE.equals(request.getClearPromo()) ? null : request.getCouponId();
        UUID offerId = Boolean.TRUE.equals(request.getClearPromo()) ? null : request.getOfferId();
        if (couponId != null && offerId != null) {
            throw new BadRequestException("Select either a coupon or an offer, not both");
        }

        GstCalculationService.PromoContext promo = promoResolutionService.resolveForBooking(
                booking.getTenantId(), booking.getBranchId(), booking.getCustomerId(),
                couponId, offerId);

        booking.setCouponId(promo.getCoupon() != null ? promo.getCoupon().getId() : null);
        booking.setOfferId(promo.getOffer() != null ? promo.getOffer().getId() : null);
        booking.setMembershipSubscriptionId(promo.getMembershipSubscription() != null
                ? promo.getMembershipSubscription().getId() : null);
        // Coupon/offer XOR manager bill discount.
        if (booking.getCouponId() != null || booking.getOfferId() != null) {
            booking.setBillDiscountType(null);
            booking.setBillDiscountValue(null);
            booking.setBillDiscountNote(null);
        }
        persistPromoAmounts(booking, promo);
        bookingRepository.save(booking);

        Branch branch = branchRepository.findById(booking.getBranchId()).orElseThrow();
        Customer customer = customerRepository.findById(booking.getCustomerId()).orElseThrow();
        return toResponse(booking, branch, customer);
    }

    @Transactional
    public BookingResponse applyBillDiscount(UUID bookingId, ApplyBillDiscountRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Cannot change discount on this booking");
        }

        if (Boolean.TRUE.equals(request.getClearDiscount())) {
            booking.setBillDiscountType(null);
            booking.setBillDiscountValue(null);
            booking.setBillDiscountNote(null);
        } else {
            if (request.getBillDiscountType() == null || request.getBillDiscountValue() == null
                    || request.getBillDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("error.booking.billDiscountInvalid");
            }
            booking.setBillDiscountType(request.getBillDiscountType());
            booking.setBillDiscountValue(request.getBillDiscountValue().setScale(2, RoundingMode.HALF_UP));
            booking.setBillDiscountNote(request.getBillDiscountNote());
            // Manager discount XOR coupon/offer.
            booking.setCouponId(null);
            booking.setOfferId(null);
        }

        GstCalculationService.PromoContext promo = promoContextFor(booking);
        persistPromoAmounts(booking, promo);
        bookingRepository.save(booking);

        Branch branch = branchRepository.findById(booking.getBranchId()).orElseThrow();
        Customer customer = customerRepository.findById(booking.getCustomerId()).orElseThrow();
        return toResponse(booking, branch, customer);
    }

    public BookingResponse getById(UUID id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());
        Branch branch = branchRepository.findById(booking.getBranchId()).orElseThrow();
        Customer customer = customerRepository.findById(booking.getCustomerId()).orElseThrow();
        return toResponse(booking, branch, customer);
    }

    public PageResponse<BookingResponse> listPaged(BookingListFilter filter) {
        UUID tenantId = SecurityUtils.requireTenantId();

        if (filter.getBranchId() != null) {
            SecurityUtils.assertBranchAccess(filter.getBranchId());
        } else {
            SecurityUtils.assertBrandAdminOrAbove();
        }

        int size = PageUtils.normalizeSize(filter.getSize());
        int page = PageUtils.normalizePage(filter.getPage());

        Specification<Booking> spec = BookingSpecifications.fromFilter(tenantId, filter);
        Page<Booking> result = bookingRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<Booking> bookings = result.getContent();
        List<BookingResponse> content = bookings.isEmpty()
                ? List.of()
                : toListResponses(bookings);

        return PageResponse.<BookingResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    /** Fast path for tables — batch-load relations; skip live GST recompute on every row. */
    private List<BookingResponse> toListResponses(List<Booking> bookings) {
        List<UUID> bookingIds = bookings.stream().map(Booking::getId).toList();

        Set<UUID> branchIds = bookings.stream().map(Booking::getBranchId).collect(Collectors.toSet());
        Set<UUID> customerIds = bookings.stream().map(Booking::getCustomerId).collect(Collectors.toSet());

        Map<UUID, Branch> branchMap = branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, b -> b));
        Map<UUID, Customer> customerMap = customerRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, c -> c));

        Map<UUID, List<BookingLineItem>> linesByBooking = lineItemRepository.findByBookingIdIn(bookingIds).stream()
                .collect(Collectors.groupingBy(BookingLineItem::getBookingId));

        Set<UUID> staffIds = linesByBooking.values().stream()
                .flatMap(List::stream)
                .map(BookingLineItem::getStaffId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> staffNames = staffIds.isEmpty()
                ? Map.of()
                : staffRepository.findAllById(staffIds).stream()
                        .collect(Collectors.toMap(Staff::getId, Staff::getName));

        Map<UUID, Invoice> invoiceByBooking = invoiceRepository.findByBookingIdIn(bookingIds).stream()
                .collect(Collectors.toMap(Invoice::getBookingId, inv -> inv, (a, b) -> a));

        return bookings.stream()
                .map(b -> toListResponse(
                        b,
                        branchMap.get(b.getBranchId()),
                        customerMap.get(b.getCustomerId()),
                        linesByBooking.getOrDefault(b.getId(), List.of()),
                        staffNames,
                        invoiceByBooking.get(b.getId())))
                .collect(Collectors.toList());
    }

    private BookingResponse toListResponse(
            Booking booking,
            Branch branch,
            Customer customer,
            List<BookingLineItem> lines,
            Map<UUID, String> staffNames,
            Invoice invoice) {
        List<BookingLineResponse> lineResponses = lines.stream()
                .map(line -> BookingLineResponse.builder()
                        .id(line.getId())
                        .branchServiceId(line.getBranchServiceId())
                        .serviceId(line.getServiceId())
                        .staffId(line.getStaffId())
                        .staffName(line.getStaffId() != null ? staffNames.get(line.getStaffId()) : null)
                        .serviceName(line.getServiceName())
                        .unitPrice(line.getUnitPrice())
                        .quantity(line.getQuantity())
                        .gstRate(line.getGstRate())
                        .lineDiscountType(line.getLineDiscountType())
                        .lineDiscountValue(line.getLineDiscountValue())
                        .estimatedDurationMinutes(line.getEstimatedDurationMinutes())
                        .actualDurationMinutes(line.getActualDurationMinutes())
                        .build())
                .collect(Collectors.toList());

        BillPreviewResponse billPreview = invoice != null
                ? billPreviewFromInvoice(invoice)
                : lines.isEmpty()
                        ? null
                        : gstCalculationService.calculate(booking, lines, promoContextFor(booking));

        return BookingResponse.builder()
                .id(booking.getId())
                .branchId(booking.getBranchId())
                .branchName(branch != null ? branch.getName() : null)
                .customerId(booking.getCustomerId())
                .customerName(customer != null ? customer.getName() : null)
                .customerPhone(customer != null ? customer.getPhone() : null)
                .status(booking.getStatus())
                .lines(lineResponses)
                .billDiscountType(booking.getBillDiscountType())
                .billDiscountValue(booking.getBillDiscountValue())
                .billDiscountNote(booking.getBillDiscountNote())
                .couponId(booking.getCouponId())
                .offerId(booking.getOfferId())
                .membershipSubscriptionId(booking.getMembershipSubscriptionId())
                .pendingMembershipPlanId(booking.getPendingMembershipPlanId())
                .notes(booking.getNotes())
                .billPreview(billPreview)
                .createdAt(booking.getCreatedAt())
                .serviceStartedAt(booking.getServiceStartedAt())
                .estimatedEndAt(booking.getEstimatedEndAt())
                .completedAt(booking.getCompletedAt())
                .actualDurationMinutes(booking.getActualDurationMinutes())
                .invoiceId(invoice != null ? invoice.getId() : null)
                .build();
    }

    private BillPreviewResponse billPreviewFromInvoice(Invoice invoice) {
        InvoiceBillUtils.MembershipFeeView fee = InvoiceBillUtils.resolveMembershipFee(invoice);
        return BillPreviewResponse.builder()
                .subtotal(invoice.getSubtotal())
                .membershipDiscountAmount(invoice.getMembershipDiscountAmount())
                .promoDiscountAmount(invoice.getPromoDiscountAmount())
                .discountAmount(invoice.getDiscountAmount())
                .taxableAmount(invoice.getTaxableAmount())
                .cgstAmount(invoice.getCgstAmount())
                .sgstAmount(invoice.getSgstAmount())
                .grandTotal(invoice.getGrandTotal())
                .membershipLabel(invoice.getMembershipLabel())
                .promoLabel(invoice.getPromoLabel())
                .membershipFeeAmount(fee.amount())
                .membershipFeeLabel(fee.label())
                .build();
    }

    private BillPreviewResponse estimateBillPreviewFromLines(List<BookingLineItem> lines) {
        if (lines.isEmpty()) {
            return null;
        }
        BigDecimal subtotal = lines.stream()
                .map(line -> line.getUnitPrice().multiply(
                        BigDecimal.valueOf(line.getQuantity() != null ? line.getQuantity() : 1)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return BillPreviewResponse.builder()
                .subtotal(subtotal)
                .grandTotal(subtotal)
                .build();
    }

    public List<BookingResponse> listByBranch(UUID branchId, BookingStatus status) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBranchAccess(branchId);
        List<Booking> bookings = status != null
                ? bookingRepository.findByTenantIdAndBranchIdAndStatus(tenantId, branchId, status)
                : bookingRepository.findByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, branchId);
        return bookings.stream().map(b -> {
            Branch branch = branchRepository.findById(b.getBranchId()).orElse(null);
            Customer customer = customerRepository.findById(b.getCustomerId()).orElse(null);
            return toResponse(b, branch, customer);
        }).collect(Collectors.toList());
    }

    public List<BookingResponse> listAll(BookingStatus status) {
        UUID tenantId = SecurityUtils.requireTenantId();
        SecurityUtils.assertBrandAdminOrAbove();
        List<Booking> bookings = bookingRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        if (status != null) {
            bookings = bookings.stream().filter(b -> b.getStatus() == status).collect(Collectors.toList());
        }
        return bookings.stream().map(b -> {
            Branch branch = branchRepository.findById(b.getBranchId()).orElse(null);
            Customer customer = customerRepository.findById(b.getCustomerId()).orElse(null);
            return toResponse(b, branch, customer);
        }).collect(Collectors.toList());
    }

    public BillPreviewResponse previewBill(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());
        List<BookingLineItem> lines = lineItemRepository.findByBookingId(bookingId);
        return gstCalculationService.calculate(booking, lines, promoContextFor(booking));
    }

    @Transactional
    public BookingResponse completePayment(UUID bookingId, RecordPaymentRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());

        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BadRequestException("error.booking.alreadyCompleted");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("error.booking.cancelled");
        }
        if (booking.getStatus() != BookingStatus.IN_PROGRESS
                && booking.getStatus() != BookingStatus.READY_FOR_BILLING
                && booking.getStatus() != BookingStatus.DRAFT) {
            throw new BadRequestException("error.booking.alreadyCompleted");
        }

        invoiceRepository.findByBookingId(bookingId).ifPresent(i -> {
            throw new BadRequestException("error.booking.invoiceExists");
        });

        List<BookingLineItem> lines = lineItemRepository.findByBookingId(bookingId);

        UUID pendingPlanId = booking.getPendingMembershipPlanId();
        BigDecimal membershipFeeCollected = BigDecimal.ZERO;
        String membershipFeeLabel = null;
        if (pendingPlanId != null
                && membershipService.findActive(booking.getTenantId(), booking.getCustomerId()).isEmpty()) {
            var plan = membershipService.loadPlan(pendingPlanId);
            membershipFeeCollected = plan.getFeeAmount() != null ? plan.getFeeAmount() : BigDecimal.ZERO;
            if (membershipFeeCollected.compareTo(BigDecimal.ZERO) > 0) {
                membershipFeeLabel = "Membership · " + plan.getName();
            }
            SellMembershipRequest sellReq = new SellMembershipRequest();
            sellReq.setCustomerId(booking.getCustomerId());
            sellReq.setPlanId(pendingPlanId);
            sellReq.setBranchId(booking.getBranchId());
            sellReq.setPaymentMode(request.getMode());
            sellReq.setPaymentReference(request.getReference());
            sellReq.setAmount(membershipFeeCollected);
            membershipService.sell(sellReq);
            booking.setPendingMembershipPlanId(null);
            bookingRepository.save(booking);
        }

        GstCalculationService.PromoContext promo = promoContextFor(booking);
        BillPreviewResponse bill = gstCalculationService.calculate(booking, lines, promo);

        BigDecimal cgstAmount = bill.getCgstAmount() != null ? bill.getCgstAmount() : BigDecimal.ZERO;
        BigDecimal sgstAmount = bill.getSgstAmount() != null ? bill.getSgstAmount() : BigDecimal.ZERO;
        BigDecimal billCgst = bill.getCgstAmount() != null ? bill.getCgstAmount() : BigDecimal.ZERO;
        BigDecimal billSgst = bill.getSgstAmount() != null ? bill.getSgstAmount() : BigDecimal.ZERO;
        BigDecimal grandTotal = bill.getGrandTotal()
                .subtract(billCgst)
                .subtract(billSgst)
                .add(cgstAmount)
                .add(sgstAmount)
                .add(membershipFeeCollected)
                .setScale(2, RoundingMode.HALF_UP);

        boolean overrideCgst = request.getCgstAmount() != null;
        boolean overrideSgst = request.getSgstAmount() != null;
        if (overrideCgst || overrideSgst) {
            if (!overrideCgst || !overrideSgst) {
                throw new BadRequestException("error.booking.taxOverrideBothRequired");
            }
            if (request.getCgstAmount().compareTo(BigDecimal.ZERO) < 0
                    || request.getSgstAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("error.booking.taxOverridePositive");
            }
            cgstAmount = request.getCgstAmount().setScale(2, RoundingMode.HALF_UP);
            sgstAmount = request.getSgstAmount().setScale(2, RoundingMode.HALF_UP);
            grandTotal = bill.getGrandTotal()
                    .subtract(billCgst)
                    .subtract(billSgst)
                    .add(cgstAmount)
                    .add(sgstAmount)
                    .add(membershipFeeCollected)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        if (request.getAmount().compareTo(grandTotal) != 0) {
            throw new BadRequestException("error.booking.paymentMismatch");
        }

        Branch branch = branchRepository.findById(booking.getBranchId()).orElseThrow();
        Customer customer = customerRepository.findById(booking.getCustomerId()).orElseThrow();

        String invoiceNumber = nextInvoiceNumber(branch);
        Instant issuedAt = Instant.now();
        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .tenantId(booking.getTenantId())
                .branchId(booking.getBranchId())
                .bookingId(booking.getId())
                .customerId(customer.getId())
                .invoiceNumber(invoiceNumber)
                .subtotal(bill.getSubtotal())
                .discountAmount(bill.getDiscountAmount())
                .membershipDiscountAmount(bill.getMembershipDiscountAmount() != null
                        ? bill.getMembershipDiscountAmount() : BigDecimal.ZERO)
                .promoDiscountAmount(
                        (bill.getPromoDiscountAmount() != null ? bill.getPromoDiscountAmount() : BigDecimal.ZERO)
                                .add(bill.getManualDiscountAmount() != null ? bill.getManualDiscountAmount() : BigDecimal.ZERO))
                .couponId(bill.getCouponId())
                .offerId(bill.getOfferId())
                .membershipSubscriptionId(bill.getMembershipSubscriptionId())
                .membershipLabel(bill.getMembershipLabel())
                .promoLabel(bill.getPromoLabel() != null ? bill.getPromoLabel() : bill.getManualDiscountLabel())
                .membershipFeeAmount(membershipFeeCollected)
                .membershipFeeLabel(membershipFeeLabel)
                .taxableAmount(bill.getTaxableAmount())
                .cgstAmount(cgstAmount)
                .sgstAmount(sgstAmount)
                .grandTotal(grandTotal)
                .branchGstin(branch.getGstin() != null ? branch.getGstin() : "")
                .customerName(customer.getName())
                .customerPhone(customer.getPhone() != null ? customer.getPhone() : "")
                .customerSociety(customer.getSociety())
                .customerFlat(customer.getFlatUnit())
                .issuedAt(issuedAt)
                .build());

        try {
            invoicePdfService.persistPdf(invoice);
        } catch (Exception e) {
            // Payment must succeed even if PDF persistence fails; download regenerates on demand.
            org.slf4j.LoggerFactory.getLogger(BookingService.class)
                    .warn("Invoice PDF persistence failed for {}: {}", invoice.getId(), e.toString());
        }

        Payment payment = paymentRepository.save(Payment.builder()
                .tenantId(booking.getTenantId())
                .branchId(booking.getBranchId())
                .bookingId(booking.getId())
                .invoiceId(invoice.getId())
                .mode(request.getMode())
                .amount(request.getAmount())
                .reference(request.getReference())
                .recordedByUserId(user.getId())
                .build());

        if (request.getMode() == PaymentMode.SPLIT && request.getSplits() != null) {
            for (var split : request.getSplits()) {
                paymentSplitRepository.save(PaymentSplit.builder()
                        .paymentId(payment.getId())
                        .mode(split.getMode())
                        .amount(split.getAmount())
                        .reference(split.getReference())
                        .build());
            }
        }

        booking.setStatus(BookingStatus.COMPLETED);
        Instant completedAt = Instant.now();
        booking.setCompletedAt(completedAt);
        Instant started = booking.getServiceStartedAt() != null ? booking.getServiceStartedAt() : booking.getCreatedAt();
        if (started != null) {
            long mins = Duration.between(started, completedAt).toMinutes();
            booking.setActualDurationMinutes((int) Math.max(1, mins));
        }
        booking.setMembershipDiscountAmount(bill.getMembershipDiscountAmount());
        booking.setPromoDiscountAmount(bill.getPromoDiscountAmount());
        bookingRepository.save(booking);

        // Allocate visit duration across lines by estimated weight (per staff sequential share).
        List<BookingLineItem> paidLines = lineItemRepository.findByBookingId(booking.getId());
        int totalEst = paidLines.stream()
                .mapToInt(l -> (l.getEstimatedDurationMinutes() != null ? l.getEstimatedDurationMinutes() : 30)
                        * Math.max(1, l.getQuantity() != null ? l.getQuantity() : 1))
                .sum();
        int visitMins = booking.getActualDurationMinutes() != null ? booking.getActualDurationMinutes() : 0;
        for (BookingLineItem line : paidLines) {
            int lineEst = (line.getEstimatedDurationMinutes() != null ? line.getEstimatedDurationMinutes() : 30)
                    * Math.max(1, line.getQuantity() != null ? line.getQuantity() : 1);
            int allocated = totalEst > 0
                    ? Math.max(1, (int) Math.round(visitMins * (lineEst / (double) totalEst)))
                    : visitMins;
            line.setEndedAt(completedAt);
            line.setActualDurationMinutes(allocated);
            lineItemRepository.save(line);
        }

        promoResolutionService.incrementRedemptions(booking.getCouponId(), booking.getOfferId());

        customer.setVisitCount(customer.getVisitCount() + 1);
        customer.setLifetimeSpend(customer.getLifetimeSpend().add(grandTotal));
        customer.setLastVisitAt(Instant.now());
        customerRepository.save(customer);

        auditService.log("COMPLETE_PAYMENT", "Booking", booking.getId(), "Invoice: " + invoiceNumber);
        billReceiptNotificationService.sendAfterPayment(invoice, customer);
        BookingResponse response = toResponse(booking, branch, customer);
        response.setInvoiceId(invoice.getId());
        response.setReceiptQueued(true);
        var invitation = reviewInvitationPort.createAfterPayment(invoice, branch, customer);
        response.setReviewInvitationUrl(invitation.getReviewUrl());
        response.setReviewInvitationToken(invitation.getToken());
        return response;
    }

    @Transactional
    public void cancel(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BadRequestException("error.booking.cannotCancel");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        auditService.log("CANCEL_BOOKING", "Booking", bookingId, null);
    }

    private String nextInvoiceNumber(Branch branch) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        int year = now.getYear();
        int month = now.getMonthValue();
        String fy = month >= 4 ? year + "-" + String.valueOf(year + 1).substring(2)
                : (year - 1) + "-" + String.valueOf(year).substring(2);

        InvoiceSequence seq = invoiceSequenceRepository.findByBranchIdAndFiscalYear(branch.getId(), fy)
                .orElseGet(() -> invoiceSequenceRepository.save(InvoiceSequence.builder()
                        .branchId(branch.getId())
                        .fiscalYear(fy)
                        .lastSequence(0L)
                        .build()));
        seq.setLastSequence(seq.getLastSequence() + 1);
        invoiceSequenceRepository.save(seq);
        return branch.getCode() + "-" + fy + "-" + String.format("%05d", seq.getLastSequence());
    }

    private BookingResponse toResponse(Booking booking, Branch branch, Customer customer) {
        List<BookingLineItem> lines = lineItemRepository.findByBookingId(booking.getId());
        List<BookingLineResponse> lineResponses = lines.stream().map(line -> {
            String staffName = staffRepository.findById(line.getStaffId()).map(Staff::getName).orElse(null);
            return BookingLineResponse.builder()
                    .id(line.getId())
                    .branchServiceId(line.getBranchServiceId())
                    .serviceId(line.getServiceId())
                    .staffId(line.getStaffId())
                    .staffName(staffName)
                    .serviceName(line.getServiceName())
                    .unitPrice(line.getUnitPrice())
                    .quantity(line.getQuantity())
                    .gstRate(line.getGstRate())
                    .lineDiscountType(line.getLineDiscountType())
                    .lineDiscountValue(line.getLineDiscountValue())
                    .estimatedDurationMinutes(line.getEstimatedDurationMinutes())
                    .actualDurationMinutes(line.getActualDurationMinutes())
                    .build();
        }).collect(Collectors.toList());

        Invoice invoice = invoiceRepository.findByBookingId(booking.getId()).orElse(null);
        BillPreviewResponse billPreview = lines.isEmpty()
                ? null
                : invoice != null
                        ? billPreviewFromInvoice(invoice)
                        : gstCalculationService.calculate(booking, lines, promoContextFor(booking));

        UUID invoiceId = invoice != null ? invoice.getId()
                : invoiceRepository.findByBookingId(booking.getId()).map(Invoice::getId).orElse(null);

        return BookingResponse.builder()
                .id(booking.getId())
                .branchId(booking.getBranchId())
                .branchName(branch != null ? branch.getName() : null)
                .customerId(booking.getCustomerId())
                .customerName(customer != null ? customer.getName() : null)
                .customerPhone(customer != null ? customer.getPhone() : null)
                .status(booking.getStatus())
                .lines(lineResponses)
                .billDiscountType(booking.getBillDiscountType())
                .billDiscountValue(booking.getBillDiscountValue())
                .billDiscountNote(booking.getBillDiscountNote())
                .couponId(booking.getCouponId())
                .offerId(booking.getOfferId())
                .membershipSubscriptionId(booking.getMembershipSubscriptionId())
                .pendingMembershipPlanId(booking.getPendingMembershipPlanId())
                .notes(booking.getNotes())
                .billPreview(billPreview)
                .createdAt(booking.getCreatedAt())
                .serviceStartedAt(booking.getServiceStartedAt())
                .estimatedEndAt(booking.getEstimatedEndAt())
                .completedAt(booking.getCompletedAt())
                .actualDurationMinutes(booking.getActualDurationMinutes())
                .invoiceId(invoiceId)
                .build();
    }

    private GstCalculationService.PromoContext promoContextFor(Booking booking) {
        return promoResolutionService.resolveForBooking(
                booking.getTenantId(),
                booking.getBranchId(),
                booking.getCustomerId(),
                booking.getCouponId(),
                booking.getOfferId(),
                booking.getPendingMembershipPlanId());
    }

    @Transactional
    public BookingResponse setPendingMembershipPlan(UUID bookingId, SetPendingMembershipPlanRequest request) {
        Booking booking = requireEditableBooking(bookingId);
        UUID planId = request != null ? request.getPlanId() : null;
        assertPendingMembershipAllowed(booking.getTenantId(), booking.getBranchId(), booking.getCustomerId(), planId);
        booking.setPendingMembershipPlanId(planId);
        persistPromoAmounts(booking, promoContextFor(booking));
        bookingRepository.save(booking);
        Branch branch = branchRepository.findById(booking.getBranchId()).orElseThrow();
        Customer customer = customerRepository.findById(booking.getCustomerId()).orElseThrow();
        auditService.log("SET_PENDING_MEMBERSHIP", "Booking", bookingId,
                planId != null ? "Membership plan queued on visit" : "Pending membership cleared");
        return toResponse(booking, branch, customer);
    }

    private void assertPendingMembershipAllowed(
            UUID tenantId, UUID branchId, UUID customerId, UUID pendingPlanId) {
        if (pendingPlanId == null) {
            return;
        }
        if (membershipService.findActive(tenantId, customerId).isPresent()) {
            throw new BadRequestException("Customer already has an active membership");
        }
        var plan = membershipService.loadPlan(pendingPlanId);
        if (plan.getStatus() != com.salonplatform.domain.enums.PromoStatus.ACTIVE) {
            throw new BadRequestException("Membership plan is not active");
        }
        if (!com.salonplatform.util.PromoScopeUtils.branchAllowed(plan.getBranchIds(), branchId)) {
            throw new BadRequestException("Plan not available at this branch");
        }
    }

    private void persistPromoAmounts(Booking booking, GstCalculationService.PromoContext promo) {
        List<BookingLineItem> lines = lineItemRepository.findByBookingId(booking.getId());
        if (lines.isEmpty()) {
            return;
        }
        BillPreviewResponse bill = gstCalculationService.calculate(booking, lines, promo);
        booking.setMembershipDiscountAmount(bill.getMembershipDiscountAmount());
        booking.setPromoDiscountAmount(bill.getPromoDiscountAmount());
    }
}
