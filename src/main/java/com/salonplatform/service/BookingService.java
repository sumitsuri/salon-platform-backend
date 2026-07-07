package com.salonplatform.service;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.BookingStatus;
import com.salonplatform.domain.enums.PaymentMode;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.billing.BillPreviewResponse;
import com.salonplatform.dto.booking.*;
import com.salonplatform.dto.common.PageResponse;
import com.salonplatform.dto.payment.RecordPaymentRequest;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.exception.ResourceNotFoundException;
import com.salonplatform.repository.BookingSpecifications;
import com.salonplatform.security.SecurityUtils;
import com.salonplatform.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(10, 20, 50, 100);

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
            throw new BadRequestException("At least one service is required");
        }

        Booking booking = bookingRepository.save(Booking.builder()
                .tenantId(tenantId)
                .branchId(request.getBranchId())
                .customerId(customer.getId())
                .createdByUserId(user.getId())
                .status(BookingStatus.IN_PROGRESS)
                .notes(request.getNotes())
                .billDiscountType(request.getBillDiscountType())
                .billDiscountValue(request.getBillDiscountValue())
                .billDiscountNote(request.getBillDiscountNote())
                .build());

        for (BookingLineRequest lineReq : request.getLines()) {
            if (lineReq.getStaffId() == null) {
                throw new BadRequestException("Staff assignment is required for every service");
            }
            BranchService bs = branchServiceRepository.findById(lineReq.getBranchServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Branch service not found"));
            SalonService svc = salonServiceRepository.findById(bs.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
            staffRepository.findById(lineReq.getStaffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

            lineItemRepository.save(BookingLineItem.builder()
                    .bookingId(booking.getId())
                    .branchServiceId(bs.getId())
                    .serviceId(svc.getId())
                    .staffId(lineReq.getStaffId())
                    .serviceName(bs.getDisplayNameOverride() != null ? bs.getDisplayNameOverride() : svc.getName())
                    .unitPrice(bs.getPrice())
                    .quantity(lineReq.getQuantity() != null ? lineReq.getQuantity() : 1)
                    .gstRate(svc.getGstRate())
                    .lineDiscountType(lineReq.getLineDiscountType())
                    .lineDiscountValue(lineReq.getLineDiscountValue())
                    .lineDiscountNote(lineReq.getLineDiscountNote())
                    .build());
        }

        booking.setStatus(BookingStatus.READY_FOR_BILLING);
        bookingRepository.save(booking);
        auditService.log("CREATE_BOOKING", "Booking", booking.getId(), "Walk-in booking created");
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

        int size = ALLOWED_PAGE_SIZES.contains(filter.getSize()) ? filter.getSize() : 20;
        int page = Math.max(filter.getPage(), 0);

        Specification<Booking> spec = BookingSpecifications.fromFilter(tenantId, filter);
        Page<Booking> result = bookingRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<BookingResponse> content = result.getContent().stream()
                .map(b -> {
                    Branch branch = branchRepository.findById(b.getBranchId()).orElse(null);
                    Customer customer = customerRepository.findById(b.getCustomerId()).orElse(null);
                    return toResponse(b, branch, customer);
                })
                .collect(Collectors.toList());

        return PageResponse.<BookingResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
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
        return gstCalculationService.calculate(booking, lines);
    }

    @Transactional
    public BookingResponse completePayment(UUID bookingId, RecordPaymentRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());

        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BadRequestException("Booking already completed");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Cannot pay for cancelled booking");
        }

        invoiceRepository.findByBookingId(bookingId).ifPresent(i -> {
            throw new BadRequestException("Invoice already exists for this booking");
        });

        List<BookingLineItem> lines = lineItemRepository.findByBookingId(bookingId);
        BillPreviewResponse bill = gstCalculationService.calculate(booking, lines);

        if (request.getAmount().compareTo(bill.getGrandTotal()) != 0) {
            throw new BadRequestException("Payment amount must match grand total: " + bill.getGrandTotal());
        }

        Branch branch = branchRepository.findById(booking.getBranchId()).orElseThrow();
        Customer customer = customerRepository.findById(booking.getCustomerId()).orElseThrow();

        String invoiceNumber = nextInvoiceNumber(branch);
        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .tenantId(booking.getTenantId())
                .branchId(booking.getBranchId())
                .bookingId(booking.getId())
                .customerId(customer.getId())
                .invoiceNumber(invoiceNumber)
                .subtotal(bill.getSubtotal())
                .discountAmount(bill.getDiscountAmount())
                .taxableAmount(bill.getTaxableAmount())
                .cgstAmount(bill.getCgstAmount())
                .sgstAmount(bill.getSgstAmount())
                .grandTotal(bill.getGrandTotal())
                .branchGstin(branch.getGstin())
                .customerName(customer.getName())
                .customerPhone(customer.getPhone())
                .customerSociety(customer.getSociety())
                .customerFlat(customer.getFlatUnit())
                .build());

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
        booking.setCompletedAt(Instant.now());
        bookingRepository.save(booking);

        customer.setVisitCount(customer.getVisitCount() + 1);
        customer.setLifetimeSpend(customer.getLifetimeSpend().add(bill.getGrandTotal()));
        customer.setLastVisitAt(Instant.now());
        customerRepository.save(customer);

        auditService.log("COMPLETE_PAYMENT", "Booking", booking.getId(), "Invoice: " + invoiceNumber);
        return toResponse(booking, branch, customer);
    }

    @Transactional
    public void cancel(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        SecurityUtils.assertBranchAccess(booking.getBranchId());
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel completed booking");
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
                    .build();
        }).collect(Collectors.toList());

        BillPreviewResponse billPreview = lines.isEmpty() ? null : gstCalculationService.calculate(booking, lines);

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
                .notes(booking.getNotes())
                .billPreview(billPreview)
                .createdAt(booking.getCreatedAt())
                .completedAt(booking.getCompletedAt())
                .build();
    }
}
