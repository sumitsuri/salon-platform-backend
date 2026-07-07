package com.salonplatform.seed;

import com.salonplatform.domain.entity.*;
import com.salonplatform.domain.enums.*;
import com.salonplatform.domain.repository.*;
import com.salonplatform.dto.billing.BillPreviewResponse;
import com.salonplatform.service.GstCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DemoBookingSeeder implements CommandLineRunner {

    private static final String SEED_ACTION = "SEED_DEMO_BOOKINGS_60D_V3";
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int HISTORY_DAYS = 60;

    private final AuditLogRepository auditLogRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final CustomerRepository customerRepository;
    private final BranchServiceRepository branchServiceRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final BookingRepository bookingRepository;
    private final BookingLineItemRepository lineItemRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceSequenceRepository invoiceSequenceRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final GstCalculationService gstCalculationService;
    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private record ServiceInfo(BranchService branchService, SalonService salonService) {}

    @Override
    @Transactional
    public void run(String... args) {
        Tenant tenant = tenantRepository.findBySlug("demo-brand").orElse(null);
        if (tenant == null) {
            return;
        }

        UUID tenantId = tenant.getId();

        if (auditLogRepository.existsByAction(SEED_ACTION) && hasHealthyDateDistribution(tenantId)) {
            return;
        }

        List<Branch> branches = branchRepository.findByTenantId(tenantId);
        if (branches.size() < 2) {
            return;
        }

        log.info("Cleaning up demo booking data before re-seeding with backdated timestamps...");
        cleanupDemoBookingData(tenantId);

        List<Customer> customers = ensureCustomers(tenantId, branches);
        Map<UUID, List<ServiceInfo>> servicesByBranch = loadServices(tenantId, branches);
        Map<UUID, UUID> managerByBranch = loadManagers(tenantId, branches);

        int totalBookings = 0;
        Map<String, Long> seqByBranchFy = initializeInvoiceSequences(tenantId, branches);

        LocalDate today = LocalDate.now(ZONE);
        for (int dayOffset = HISTORY_DAYS - 1; dayOffset >= 0; dayOffset--) {
            LocalDate date = today.minusDays(dayOffset);
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;

            for (Branch branch : branches) {
                int count = bookingsForDay(branch.getCode(), weekend);
                for (int i = 0; i < count; i++) {
                    Instant visitTime = randomVisitTime(date);
                    totalBookings += seedBooking(
                            tenantId, branch, customers, servicesByBranch.get(branch.getId()),
                            managerByBranch.get(branch.getId()), visitTime, seqByBranchFy);
                }
            }
        }

        syncInvoiceSequences(branches, seqByBranchFy);

        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .action(SEED_ACTION)
                .entityType("System")
                .details("Seeded " + totalBookings + " completed bookings over " + HISTORY_DAYS + " days")
                .build());

        log.info("Seeded {} demo bookings across {} days for Lithos + Webcity", totalBookings, HISTORY_DAYS);
    }

    private boolean hasHealthyDateDistribution(UUID tenantId) {
        Long distinctDays = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT DATE(issued_at AT TIME ZONE 'Asia/Kolkata')) FROM invoices WHERE tenant_id = ?",
                Long.class, tenantId);
        return distinctDays != null && distinctDays >= 30;
    }

    private void cleanupDemoBookingData(UUID tenantId) {
        jdbcTemplate.update(
                "DELETE FROM payment_splits WHERE payment_id IN (SELECT id FROM payments WHERE tenant_id = ?)",
                tenantId);
        jdbcTemplate.update("DELETE FROM payments WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM invoices WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update(
                "DELETE FROM booking_line_items WHERE booking_id IN (SELECT id FROM bookings WHERE tenant_id = ?)",
                tenantId);
        jdbcTemplate.update("DELETE FROM bookings WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update(
                "UPDATE customers SET visit_count = 0, lifetime_spend = 0, last_visit_at = NULL WHERE tenant_id = ?",
                tenantId);
    }

    private int bookingsForDay(String branchCode, boolean weekend) {
        if ("WEB".equals(branchCode)) {
            return weekend
                    ? ThreadLocalRandom.current().nextInt(4, 7)
                    : ThreadLocalRandom.current().nextInt(2, 5);
        }
        return weekend
                ? ThreadLocalRandom.current().nextInt(3, 6)
                : ThreadLocalRandom.current().nextInt(2, 4);
    }

    private Instant randomVisitTime(LocalDate date) {
        int hour = ThreadLocalRandom.current().nextInt(9, 21);
        int minute = ThreadLocalRandom.current().nextInt(0, 60);
        return date.atTime(hour, minute).atZone(ZONE).toInstant();
    }

    private int seedBooking(UUID tenantId, Branch branch, List<Customer> customers,
                            List<ServiceInfo> services, UUID managerId, Instant visitTime,
                            Map<String, Long> seqByBranchFy) {
        if (services == null || services.isEmpty() || managerId == null) {
            return 0;
        }

        Customer customer = customers.get(ThreadLocalRandom.current().nextInt(customers.size()));
        int lineCount = ThreadLocalRandom.current().nextInt(100) < 35 ? 2 : 1;
        List<Staff> staff = staffRepository.findByTenantIdAndBranchId(tenantId, branch.getId());

        Booking booking = Booking.builder()
                .tenantId(tenantId)
                .branchId(branch.getId())
                .customerId(customer.getId())
                .createdByUserId(managerId)
                .status(BookingStatus.IN_PROGRESS)
                .build();

        if (ThreadLocalRandom.current().nextInt(100) < 10) {
            if (ThreadLocalRandom.current().nextBoolean()) {
                booking.setBillDiscountType(DiscountType.FLAT);
                booking.setBillDiscountValue(new BigDecimal(ThreadLocalRandom.current().nextInt(50, 201)));
            } else {
                booking.setBillDiscountType(DiscountType.PERCENT);
                booking.setBillDiscountValue(new BigDecimal(ThreadLocalRandom.current().nextInt(5, 16)));
            }
            booking.setBillDiscountNote("Manager discretion");
        }

        booking = bookingRepository.save(booking);

        Set<Integer> picked = new HashSet<>();
        for (int i = 0; i < lineCount; i++) {
            int idx = ThreadLocalRandom.current().nextInt(services.size());
            while (!picked.add(idx) && picked.size() < services.size()) {
                idx = ThreadLocalRandom.current().nextInt(services.size());
            }
            ServiceInfo info = services.get(idx);
            Staff stylist = staff.get(ThreadLocalRandom.current().nextInt(staff.size()));

            lineItemRepository.save(BookingLineItem.builder()
                    .bookingId(booking.getId())
                    .branchServiceId(info.branchService().getId())
                    .serviceId(info.salonService().getId())
                    .staffId(stylist.getId())
                    .serviceName(info.salonService().getName())
                    .unitPrice(info.branchService().getPrice())
                    .quantity(1)
                    .gstRate(info.salonService().getGstRate())
                    .build());
        }

        List<BookingLineItem> lines = lineItemRepository.findByBookingId(booking.getId());
        BillPreviewResponse bill = gstCalculationService.calculate(booking, lines);

        String fy = fiscalYear(visitTime.atZone(ZONE).toLocalDate());
        String seqKey = branch.getId() + ":" + fy;
        long seq = seqByBranchFy.merge(seqKey, 1L, Long::sum);
        String invoiceNumber = branch.getCode() + "-" + fy + "-" + String.format("%05d", seq);

        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .tenantId(tenantId)
                .branchId(branch.getId())
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

        PaymentMode mode = pickPaymentMode();
        Payment payment = paymentRepository.save(Payment.builder()
                .tenantId(tenantId)
                .branchId(branch.getId())
                .bookingId(booking.getId())
                .invoiceId(invoice.getId())
                .mode(mode)
                .amount(bill.getGrandTotal())
                .reference(mode == PaymentMode.UPI ? "UPI" + ThreadLocalRandom.current().nextInt(100000, 999999) : null)
                .recordedByUserId(managerId)
                .build());

        if (mode == PaymentMode.SPLIT) {
            BigDecimal half = bill.getGrandTotal().divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal remainder = bill.getGrandTotal().subtract(half);
            paymentSplitRepository.save(PaymentSplit.builder()
                    .paymentId(payment.getId()).mode(PaymentMode.CASH).amount(half).build());
            paymentSplitRepository.save(PaymentSplit.builder()
                    .paymentId(payment.getId()).mode(PaymentMode.UPI).amount(remainder).build());
        }

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(visitTime);

        entityManager.flush();
        backdateTimestamps(booking.getId(), invoice.getId(), payment.getId(), visitTime);

        booking.setCreatedAt(visitTime);
        booking.setUpdatedAt(visitTime);
        invoice.setIssuedAt(visitTime);
        payment.setPaidAt(visitTime);
        entityManager.detach(booking);
        entityManager.detach(invoice);
        entityManager.detach(payment);

        customer.setVisitCount(customer.getVisitCount() + 1);
        customer.setLifetimeSpend(customer.getLifetimeSpend().add(bill.getGrandTotal()));
        customer.setLastVisitAt(visitTime);
        customerRepository.save(customer);

        return 1;
    }

    private void backdateTimestamps(UUID bookingId, UUID invoiceId, UUID paymentId, Instant visitTime) {
        Timestamp ts = Timestamp.from(visitTime);
        jdbcTemplate.update(
                "UPDATE bookings SET status = 'COMPLETED', created_at = ?, updated_at = ?, completed_at = ? WHERE id = ?",
                ts, ts, ts, bookingId);
        jdbcTemplate.update("UPDATE invoices SET issued_at = ? WHERE id = ?", ts, invoiceId);
        jdbcTemplate.update("UPDATE payments SET paid_at = ? WHERE id = ?", ts, paymentId);
    }

    private PaymentMode pickPaymentMode() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 55) return PaymentMode.CASH;
        if (roll < 82) return PaymentMode.UPI;
        if (roll < 96) return PaymentMode.CARD;
        return PaymentMode.SPLIT;
    }

    private String fiscalYear(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        return month >= 4
                ? year + "-" + String.valueOf(year + 1).substring(2)
                : (year - 1) + "-" + String.valueOf(year).substring(2);
    }

    private Map<String, Long> initializeInvoiceSequences(UUID tenantId, List<Branch> branches) {
        Map<String, Long> seqByBranchFy = new HashMap<>();
        List<Invoice> existingInvoices = invoiceRepository.findByTenantIdOrderByIssuedAtDesc(tenantId);

        for (Branch branch : branches) {
            for (InvoiceSequence seq : invoiceSequenceRepository.findByBranchId(branch.getId())) {
                String key = branch.getId() + ":" + seq.getFiscalYear();
                seqByBranchFy.merge(key, seq.getLastSequence(), Math::max);
            }

            existingInvoices.stream()
                    .filter(i -> i.getBranchId().equals(branch.getId()))
                    .forEach(inv -> parseInvoiceSequence(branch.getId(), inv.getInvoiceNumber())
                            .ifPresent(parsed -> seqByBranchFy.merge(parsed.key(), parsed.seq(), Math::max)));
        }

        return seqByBranchFy;
    }

    private Optional<ParsedInvoiceSeq> parseInvoiceSequence(UUID branchId, String invoiceNumber) {
        if (invoiceNumber == null) {
            return Optional.empty();
        }
        String[] parts = invoiceNumber.split("-");
        if (parts.length < 4) {
            return Optional.empty();
        }
        try {
            String fy = parts[parts.length - 3] + "-" + parts[parts.length - 2];
            long seq = Long.parseLong(parts[parts.length - 1]);
            return Optional.of(new ParsedInvoiceSeq(branchId + ":" + fy, seq));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private record ParsedInvoiceSeq(String key, long seq) {}

    private void syncInvoiceSequences(List<Branch> branches, Map<String, Long> seqByBranchFy) {
        for (var entry : seqByBranchFy.entrySet()) {
            String[] parts = entry.getKey().split(":");
            UUID branchId = UUID.fromString(parts[0]);
            String fy = parts[1];
            long lastSeq = entry.getValue();

            InvoiceSequence seq = invoiceSequenceRepository.findByBranchIdAndFiscalYear(branchId, fy)
                    .orElseGet(() -> InvoiceSequence.builder().branchId(branchId).fiscalYear(fy).lastSequence(0L).build());
            if (lastSeq > seq.getLastSequence()) {
                seq.setLastSequence(lastSeq);
                invoiceSequenceRepository.save(seq);
            }
        }
    }

    private Map<UUID, UUID> loadManagers(UUID tenantId, List<Branch> branches) {
        Map<UUID, UUID> map = new HashMap<>();
        for (Branch branch : branches) {
            userRepository.findByTenantIdAndBranchId(tenantId, branch.getId()).stream()
                    .filter(u -> u.getRole() == UserRole.SALON_MANAGER)
                    .findFirst()
                    .ifPresent(u -> map.put(branch.getId(), u.getId()));
        }
        return map;
    }

    private Map<UUID, List<ServiceInfo>> loadServices(UUID tenantId, List<Branch> branches) {
        Map<UUID, List<ServiceInfo>> map = new HashMap<>();
        for (Branch branch : branches) {
            List<ServiceInfo> list = branchServiceRepository.findByTenantIdAndBranchId(tenantId, branch.getId()).stream()
                    .filter(BranchService::isActive)
                    .map(bs -> salonServiceRepository.findById(bs.getServiceId())
                            .map(svc -> new ServiceInfo(bs, svc))
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .toList();
            map.put(branch.getId(), list);
        }
        return map;
    }

    private List<Customer> ensureCustomers(UUID tenantId, List<Branch> branches) {
        String[] firstNames = {"Rahul", "Priya", "Amit", "Sneha", "Vikram", "Ananya", "Karthik", "Divya",
                "Arjun", "Meera", "Rohan", "Kavya", "Sanjay", "Pooja", "Nikhil", "Lakshmi", "Aditya", "Neha",
                "Manish", "Shreya", "Deepak", "Isha", "Varun", "Tanvi", "Gaurav", "Ritu", "Harsh", "Nidhi"};
        String[] societies = {"Tower A", "Tower B", "Block 1", "Block 2", "Villa 12", "Phase 2"};

        List<Customer> customers = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            String phone = String.format("98%08d", 10000000 + i);
            Customer existing = customerRepository.findByTenantIdAndPhone(tenantId, phone).orElse(null);
            if (existing != null) {
                customers.add(existing);
                continue;
            }
            Branch branch = branches.get(i % branches.size());
            String name = firstNames[i % firstNames.length] + " " + (char) ('A' + (i % 26));
            Customer c = customerRepository.save(Customer.builder()
                    .tenantId(tenantId)
                    .name(name)
                    .phone(phone)
                    .society(branch.getSocietyDefault())
                    .flatUnit(societies[i % societies.length] + "-" + (100 + i))
                    .visitCount(0)
                    .lifetimeSpend(BigDecimal.ZERO)
                    .build());
            customers.add(c);
        }
        return customers;
    }
}
