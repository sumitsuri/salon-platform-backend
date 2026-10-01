package com.salonplatform.demo;

import com.salonplatform.demo.DemoBrandCatalog.ServiceDef;
import com.salonplatform.demo.DemoBrandCatalog.StaffDef;
import com.salonplatform.domain.enums.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/**
 * Keeps the demo brand's "today" alive between rebuilds: completed walk-ins as the day goes on,
 * staff punching in and out, and a few upcoming appointments. Only touches tenants marked demo that
 * carry the generator's marker, and runs only when {@code app.demo-data.top-up-enabled=true}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoTopUpService {

    private static final ZoneId ZONE = DemoHistoryGenerator.ZONE;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal HALF_GST = new BigDecimal("9");

    private final DemoDataProperties properties;
    private final DemoDataJobService jobService;
    private final JdbcTemplate jdbc;
    private final Map<UUID, Instant> watermarks = new java.util.concurrent.ConcurrentHashMap<>();

    private record BranchRow(UUID id, String code, String name, String gstin, double lat, double lng, UUID managerId) {}

    private record StaffRow(UUID id, UUID branchId, String name, String skills) {}

    private record ServiceRow(UUID branchServiceId, UUID serviceId, String name, BigDecimal price, int minutes,
                              String skill, String gender, double weight) {}

    @Scheduled(cron = "${app.demo-data.top-up-cron:0 */15 * * * *}", zone = "Asia/Kolkata")
    public void scheduledTopUp() {
        if (!properties.isEnabled() || !properties.isTopUpEnabled()) {
            return;
        }
        try {
            topUp();
        } catch (Exception e) {
            log.warn("Demo top-up failed: {}", e.getMessage());
        }
    }

    /** Returns the number of bills added. Skips silently while a rebuild holds the lock. */
    public int topUp() {
        if (!jobService.lock().tryLock()) {
            return 0;
        }
        try {
            List<UUID> tenants = jdbc.queryForList(
                    "SELECT t.id FROM tenants t WHERE t.demo_tenant = true AND t.slug = ? AND EXISTS "
                            + "(SELECT 1 FROM audit_logs a WHERE a.tenant_id = t.id AND a.action = ?)",
                    UUID.class, properties.getSlug(), DemoHistoryGenerator.MARKER_ACTION);
            int added = 0;
            for (UUID tenantId : tenants) {
                added += topUpTenant(tenantId);
            }
            return added;
        } finally {
            jobService.lock().unlock();
        }
    }

    private int topUpTenant(UUID tenantId) {
        Instant now = Instant.now();
        LocalDate today = now.atZone(ZONE).toLocalDate();
        Random rnd = new Random(now.toEpochMilli());
        List<BranchRow> branches = jdbc.query(
                "SELECT b.id, b.code, b.name, b.gstin, b.latitude, b.longitude, "
                        + "(SELECT u.id FROM users u WHERE u.branch_id = b.id AND u.active = true LIMIT 1) AS manager_id "
                        + "FROM branches b WHERE b.tenant_id = ? AND b.status = 'ACTIVE'",
                (rs, i) -> new BranchRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getDouble(5), rs.getDouble(6), rs.getObject(7, UUID.class)), tenantId);
        Map<String, ServiceDef> catalog = new HashMap<>();
        for (ServiceDef def : DemoBrandCatalog.SERVICES) catalog.put(def.name(), def);

        DemoRowWriter w = new DemoRowWriter(jdbc, properties.getBatchSize(), properties.getPauseMillisBetweenBatches());
        DemoHistoryGenerator.declareTables(w);
        int added = 0;
        for (BranchRow br : branches) {
            List<StaffRow> staff = jdbc.query("SELECT id, branch_id, name, skills FROM staff WHERE branch_id = ? AND active = true",
                    (rs, i) -> new StaffRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                            rs.getString(4)), br.id());
            List<ServiceRow> services = jdbc.query(
                    "SELECT bs.id, s.id, s.name, bs.price, s.duration_minutes FROM branch_services bs "
                            + "JOIN services s ON s.id = bs.service_id WHERE bs.branch_id = ? AND bs.active = true AND s.active = true",
                    (rs, i) -> {
                        ServiceDef def = catalog.get(rs.getString(3));
                        return new ServiceRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                                rs.getBigDecimal(4), rs.getInt(5), def != null ? def.skill() : "Hair",
                                def != null ? def.gender() : "U", def != null ? def.weight() : 0.5);
                    }, br.id());
            if (staff.isEmpty() || services.isEmpty()) continue;

            added += walkIns(tenantId, br, staff, services, now, rnd, w);
            attendance(tenantId, br, staff, today, now, w);
            appointments(tenantId, br, staff, services, today, now, rnd, w);
            monthlyFixedCosts(tenantId, br, staff.size(), today, now, rnd, w);
        }
        w.flushAll();
        if (added > 0) {
            log.info("Demo top-up added {} bills for tenant {}", added, tenantId);
        }
        return added;
    }

    // ---- walk-in bills ------------------------------------------------------------------------

    private int walkIns(UUID tenantId, BranchRow br, List<StaffRow> staff, List<ServiceRow> services, Instant now,
                        Random rnd, DemoRowWriter w) {
        Timestamp last = jdbc.queryForObject(
                "SELECT max(issued_at) FROM invoices WHERE tenant_id = ? AND branch_id = ?", Timestamp.class, tenantId, br.id());
        Instant from = last != null ? last.toInstant() : now.minus(Duration.ofHours(2));
        // Bills land at random times inside the window, so remember where the previous run stopped.
        Instant watermark = watermarks.get(br.id());
        if (watermark != null && watermark.isAfter(from)) from = watermark;
        watermarks.put(br.id(), now);
        if (Duration.between(from, now).toHours() > 48) {
            from = now.minus(Duration.ofHours(48));
        }
        double perDay = properties.getBillsPerBranchPerDay() * branchFactor(br.code());
        double openMinutes = openMinutesBetween(from, now);
        int count = (int) Math.floor(perDay * openMinutes / 660.0 + rnd.nextDouble());
        if (count <= 0) return 0;

        List<Instant> completions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Instant t = randomOpenInstant(from, now, rnd);
            if (t != null) completions.add(t);
        }
        Collections.sort(completions);
        List<Map<String, Object>> regulars = jdbc.queryForList(
                "SELECT id, name, phone, society, flat_unit, whatsapp_opt_in FROM customers WHERE tenant_id = ? AND branch_id = ? "
                        + "AND last_visit_at < now() - interval '14 days' ORDER BY random() LIMIT ?",
                tenantId, br.id(), completions.size());
        int regularIdx = 0;
        for (Instant completed : completions) {
            Map<String, Object> cust;
            if (regularIdx < regulars.size() && rnd.nextDouble() < 0.8) {
                cust = regulars.get(regularIdx++);
            } else {
                cust = newCustomer(tenantId, br, completed, rnd, w);
            }
            bill(tenantId, br, staff, services, cust, completed, rnd, w);
        }
        return completions.size();
    }

    private Map<String, Object> newCustomer(UUID tenantId, BranchRow br, Instant at, Random rnd, DemoRowWriter w) {
        boolean female = rnd.nextDouble() < 0.62;
        String name = pick(female ? DemoBrandCatalog.FEMALE_NAMES : DemoBrandCatalog.MALE_NAMES, rnd) + " "
                + pick(DemoBrandCatalog.SURNAMES, rnd);
        String phone = uniquePhone(rnd);
        UUID id = UUID.randomUUID();
        String token = Long.toHexString(rnd.nextLong()) + Long.toHexString(rnd.nextLong());
        String society = jdbc.queryForObject("SELECT society_default FROM branches WHERE id = ?", String.class, br.id());
        w.add("customers", id, tenantId, br.id(), name, phone, br.code() + "-" + (900_000 + rnd.nextInt(99_999)),
                CustomerIdentityStatus.PHONE_VERIFIED, token, society, null, null, null, true, true, 0,
                BigDecimal.ZERO.setScale(2), null, at, at);
        w.flushAll();
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("phone", phone);
        row.put("society", society);
        row.put("flat_unit", null);
        row.put("whatsapp_opt_in", true);
        return row;
    }

    /** Phones must avoid every brand's customers, including real ones. */
    private String uniquePhone(Random rnd) {
        while (true) {
            String phone = (6 + rnd.nextInt(4)) + String.format("%09d", rnd.nextInt(1_000_000_000));
            Integer taken = jdbc.queryForObject(
                    "SELECT count(*) FROM customers WHERE phone = ? OR phone = ?", Integer.class, phone, "91" + phone);
            if (taken == null || taken == 0) return phone;
        }
    }

    private void bill(UUID tenantId, BranchRow br, List<StaffRow> staff, List<ServiceRow> services,
                      Map<String, Object> cust, Instant completed, Random rnd, DemoRowWriter w) {
        UUID customerId = (UUID) cust.get("id");
        boolean female = rnd.nextDouble() < 0.62;
        List<ServiceRow> lines = new ArrayList<>();
        lines.add(pickService(services, female, rnd, null));
        if (rnd.nextDouble() < 0.3) lines.add(pickService(services, female, rnd, lines.get(0)));

        List<Map<String, Object>> membership = jdbc.queryForList(
                "SELECT ms.id, mp.benefit_percent, mp.name FROM membership_subscriptions ms JOIN membership_plans mp ON mp.id = ms.plan_id "
                        + "WHERE ms.tenant_id = ? AND ms.customer_id = ? AND ms.status = 'ACTIVE' AND ms.ends_on >= ? LIMIT 1",
                tenantId, customerId, java.sql.Date.valueOf(completed.atZone(ZONE).toLocalDate()));
        UUID memberId = membership.isEmpty() ? null : (UUID) membership.get(0).get("id");
        BigDecimal benefit = membership.isEmpty() ? null : (BigDecimal) membership.get(0).get("benefit_percent");

        int minutes = lines.stream().mapToInt(ServiceRow::minutes).sum();
        Instant started = completed.minus(Duration.ofMinutes(minutes + 4));
        Instant arrival = started.minus(Duration.ofMinutes(5));
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal memberOff = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal half = BigDecimal.ZERO;
        for (ServiceRow line : lines) {
            BigDecimal off = benefit != null ? pct(line.price(), benefit) : BigDecimal.ZERO;
            BigDecimal t = line.price().subtract(off);
            gross = gross.add(line.price());
            memberOff = memberOff.add(off);
            taxable = taxable.add(t);
            half = half.add(pct(t, HALF_GST));
        }
        BigDecimal grand = taxable.add(half).add(half);

        UUID bookingId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        w.add("bookings", bookingId, tenantId, br.id(), customerId, br.managerId(), BookingStatus.COMPLETED, null, null,
                DiscountScope.BILL, null, null, null, memberId, memberOff, BigDecimal.ZERO.setScale(2),
                BookingSource.WALK_IN, null, null, null, null, started, started.plus(Duration.ofMinutes(minutes)),
                (int) Duration.between(started, completed).toMinutes(), arrival, completed, completed);
        Instant lineStart = started;
        for (ServiceRow line : lines) {
            StaffRow st = staffFor(staff, line.skill(), rnd);
            Instant lineEnd = lineStart.plus(Duration.ofMinutes(line.minutes()));
            w.add("booking_line_items", UUID.randomUUID(), bookingId, line.branchServiceId(), line.serviceId(), st.id(),
                    line.name(), line.price(), 1, new BigDecimal("18.00"), line.minutes(), lineStart, lineEnd,
                    line.minutes(), null);
            lineStart = lineEnd;
        }
        String membershipLabel = membership.isEmpty() ? null
                : membership.get(0).get("name") + " (" + benefit.stripTrailingZeros().toPlainString() + "%)";
        w.add("invoices", invoiceId, tenantId, br.id(), bookingId, customerId, nextInvoiceNumber(br, completed), gross,
                memberOff, memberOff, BigDecimal.ZERO.setScale(2), null, null, memberId, membershipLabel,
                BigDecimal.ZERO.setScale(2), null, BigDecimal.ZERO.setScale(2), null, null, null, taxable, half, half, grand,
                br.gstin(), cust.get("name"), cust.get("phone"), cust.get("society"), cust.get("flat_unit"), completed);
        PaymentMode mode = rnd.nextDouble() < 0.6 ? PaymentMode.UPI : rnd.nextDouble() < 0.6 ? PaymentMode.CARD : PaymentMode.CASH;
        w.add("payments", UUID.randomUUID(), tenantId, br.id(), bookingId, invoiceId, mode, grand,
                mode == PaymentMode.UPI ? "UPI" + (100_000_000 + rnd.nextInt(900_000_000)) : null, br.managerId(), completed);
        boolean optIn = !Boolean.FALSE.equals(cust.get("whatsapp_opt_in"));
        w.add("message_delivery_logs", UUID.randomUUID(), tenantId, null, null, customerId, invoiceId,
                MessageChannel.WHATSAPP, "91" + cust.get("phone"),
                optIn ? MessageDeliveryStatus.SENT : MessageDeliveryStatus.SKIPPED,
                optIn ? "simulated-" + UUID.randomUUID() : null, optIn ? null : "Customer opted out of WhatsApp",
                completed.plusSeconds(30));
        w.add("review_invitations", UUID.randomUUID(), tenantId, br.id(), br.name(), bookingId, invoiceId, customerId,
                String.valueOf(cust.get("name")).split(" ")[0], null, "PENDING", completed.plus(Duration.ofDays(7)), null,
                completed.plusSeconds(90));
        w.flushAll();
        jdbc.update("UPDATE customers SET visit_count = visit_count + 1, lifetime_spend = lifetime_spend + ?, "
                + "last_visit_at = ?, updated_at = ? WHERE id = ?", grand, Timestamp.from(completed), Timestamp.from(completed), customerId);
    }

    /** Uses the same per-branch, per-fiscal-year sequence as live billing so numbers never collide. */
    private String nextInvoiceNumber(BranchRow br, Instant at) {
        String fy = DemoHistoryGenerator.fiscalYear(at.atZone(ZONE).toLocalDate());
        Long seq = jdbc.query("UPDATE invoice_sequences SET last_sequence = last_sequence + 1 "
                        + "WHERE branch_id = ? AND fiscal_year = ? RETURNING last_sequence",
                rs -> rs.next() ? rs.getLong(1) : null, br.id(), fy);
        if (seq == null) {
            jdbc.update("INSERT INTO invoice_sequences (id, branch_id, fiscal_year, last_sequence) VALUES (?, ?, ?, 1)",
                    UUID.randomUUID(), br.id(), fy);
            seq = 1L;
        }
        return br.code() + "-" + fy + "-" + String.format("%05d", seq);
    }

    // ---- attendance ---------------------------------------------------------------------------

    private void attendance(UUID tenantId, BranchRow br, List<StaffRow> staff, LocalDate today, Instant now, DemoRowWriter w) {
        Map<String, StaffDef> defs = new HashMap<>();
        for (StaffDef def : DemoBrandCatalog.STAFF) defs.put(def.name(), def);
        for (StaffRow st : staff) {
            StaffDef def = defs.get(st.name());
            if (def != null && today.getDayOfWeek() == def.weeklyOff()) continue;
            Random r = new Random(st.id().getMostSignificantBits() ^ today.toEpochDay());
            if (r.nextDouble() < 0.05) continue; // unplanned absence
            int lateMean = def != null && def.lateProne() ? 12 : 0;
            Instant entry = today.atTime(10, 0).atZone(ZONE).toInstant()
                    .plus(Duration.ofMinutes(Math.round(-10 + r.nextGaussian() * 8 + (r.nextDouble() < 0.35 ? lateMean : 0))));
            Instant exit = today.atTime(21, 0).atZone(ZONE).toInstant().plus(Duration.ofMinutes(Math.round(8 + r.nextGaussian() * 12)));
            if (entry.isAfter(now)) continue;
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "SELECT id, exit_time FROM attendance_records WHERE staff_id = ? AND work_date = ?",
                    st.id(), java.sql.Date.valueOf(today));
            String photoKey = jdbc.query("SELECT entry_photo_key FROM attendance_records WHERE staff_id = ? "
                            + "AND entry_photo_key IS NOT NULL ORDER BY work_date DESC LIMIT 1",
                    rs -> rs.next() ? rs.getString(1) : null, st.id());
            if (existing.isEmpty()) {
                w.add("attendance_records", UUID.randomUUID(), tenantId, br.id(), st.id(), today, entry,
                        exit.isAfter(now) ? null : exit, AttendanceMethod.VERIFIED,
                        exit.isAfter(now) ? null : AttendanceMethod.VERIFIED, null, null,
                        br.lat() + (r.nextDouble() - 0.5) * 0.0006, br.lng() + (r.nextDouble() - 0.5) * 0.0006,
                        8 + r.nextDouble() * 15, exit.isAfter(now) ? null : br.lat(), exit.isAfter(now) ? null : br.lng(),
                        exit.isAfter(now) ? null : 10.0, GeoStatus.IN_GEOFENCE, exit.isAfter(now) ? null : GeoStatus.IN_GEOFENCE,
                        photoKey, exit.isAfter(now) ? null : photoKey, photoKey != null, !exit.isAfter(now) && photoKey != null,
                        entry, exit.isAfter(now) ? entry : exit);
            } else if (existing.get(0).get("exit_time") == null && !exit.isAfter(now)) {
                jdbc.update("UPDATE attendance_records SET exit_time = ?, exit_method = 'VERIFIED', exit_geo_status = 'IN_GEOFENCE', "
                                + "exit_latitude = ?, exit_longitude = ?, exit_accuracy_meters = 10, exit_photo_key = entry_photo_key, "
                                + "exit_verified = (entry_photo_key IS NOT NULL), updated_at = ? WHERE id = ?",
                        Timestamp.from(exit), br.lat(), br.lng(), Timestamp.from(exit), existing.get(0).get("id"));
            }
        }
    }

    // ---- appointments -------------------------------------------------------------------------

    private void appointments(UUID tenantId, BranchRow br, List<StaffRow> staff, List<ServiceRow> services,
                              LocalDate today, Instant now, Random rnd, DemoRowWriter w) {
        // Past confirmed appointments the floor never closed become no-shows rather than lingering.
        jdbc.update("UPDATE bookings SET status = 'CANCELLED', void_reason = 'No-show', updated_at = now() "
                + "WHERE tenant_id = ? AND branch_id = ? AND status = 'CONFIRMED' AND scheduled_start_at < ?",
                tenantId, br.id(), Timestamp.from(now.minus(Duration.ofHours(2))));
        for (int ahead = 0; ahead <= 3; ahead++) {
            LocalDate d = today.plusDays(ahead);
            Instant dayStart = d.atStartOfDay(ZONE).toInstant();
            Integer existing = jdbc.queryForObject("SELECT count(*) FROM bookings WHERE tenant_id = ? AND branch_id = ? "
                            + "AND status = 'CONFIRMED' AND scheduled_start_at >= ? AND scheduled_start_at < ?",
                    Integer.class, tenantId, br.id(), Timestamp.from(dayStart), Timestamp.from(dayStart.plus(Duration.ofDays(1))));
            int target = 6 + rnd.nextInt(4);
            if (existing != null && existing >= target) continue;
            List<UUID> customers = jdbc.queryForList("SELECT id FROM customers WHERE tenant_id = ? AND branch_id = ? "
                    + "ORDER BY random() LIMIT ?", UUID.class, tenantId, br.id(), target);
            for (UUID customerId : customers) {
                Instant startAt = d.atTime(10 + rnd.nextInt(10), rnd.nextBoolean() ? 0 : 30).atZone(ZONE).toInstant();
                if (!startAt.isAfter(now.plus(Duration.ofMinutes(30)))) continue;
                ServiceRow svc = pickService(services, rnd.nextDouble() < 0.62, rnd, null);
                StaffRow st = staffFor(staff, svc.skill(), rnd);
                UUID bookingId = UUID.randomUUID();
                boolean online = rnd.nextDouble() < 0.6;
                w.add("bookings", bookingId, tenantId, br.id(), customerId, br.managerId(), BookingStatus.CONFIRMED, null,
                        null, DiscountScope.BILL, null, null, null, null, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                        online ? BookingSource.ONLINE : BookingSource.WALK_IN, startAt,
                        startAt.plus(Duration.ofMinutes(svc.minutes())), st.id(),
                        online ? Long.toHexString(rnd.nextLong()) + Long.toHexString(rnd.nextLong()) : null,
                        null, null, null, now, now, null);
                w.add("booking_line_items", UUID.randomUUID(), bookingId, svc.branchServiceId(), svc.serviceId(), st.id(),
                        svc.name(), svc.price(), 1, new BigDecimal("18.00"), svc.minutes(), null, null, null, null);
            }
        }
    }

    // ---- month rollover ------------------------------------------------------------------------

    /** On a new month, add the fixed P&L lines the rebuild would have written; product cost ≈ 9% of revenue. */
    private void monthlyFixedCosts(UUID tenantId, BranchRow br, int staffCount, LocalDate today, Instant now,
                                   Random rnd, DemoRowWriter w) {
        LocalDate month = today.withDayOfMonth(1);
        Integer rent = jdbc.queryForObject("SELECT count(*) FROM branch_expenditures WHERE branch_id = ? "
                + "AND category = 'RENT' AND expense_month = ?", Integer.class, br.id(), java.sql.Date.valueOf(month));
        if (rent != null && rent > 0) return;
        DemoBrandCatalog.BranchDef def = DemoBrandCatalog.BRANCHES.stream().filter(b -> b.code().equals(br.code()))
                .findFirst().orElse(null);
        if (def == null) return;
        BigDecimal payroll = jdbc.queryForObject("SELECT coalesce(sum(salary), 0) FROM staff WHERE branch_id = ? AND active = true",
                BigDecimal.class, br.id());
        LocalDate previous = month.minusMonths(1);
        BigDecimal lastMonthRevenue = jdbc.queryForObject("SELECT coalesce(sum(grand_total), 0) FROM invoices WHERE branch_id = ? "
                        + "AND deleted_at IS NULL AND issued_at >= ? AND issued_at < ?", BigDecimal.class, br.id(),
                Timestamp.from(previous.atStartOfDay(ZONE).toInstant()), Timestamp.from(month.atStartOfDay(ZONE).toInstant()));
        Integer productCostRows = jdbc.queryForObject("SELECT count(*) FROM branch_expenditures WHERE branch_id = ? "
                + "AND category = 'PRODUCT_COST' AND expense_month = ?", Integer.class, br.id(), java.sql.Date.valueOf(previous));
        if (productCostRows != null && productCostRows == 0 && lastMonthRevenue.signum() > 0) {
            w.add("branch_expenditures", UUID.randomUUID(), tenantId, br.id(), ExpenditureCategory.PRODUCT_COST, previous,
                    lastMonthRevenue.multiply(new BigDecimal("0.09")).setScale(2, RoundingMode.HALF_UP),
                    "Product consumption (usage + wastage)", true, false, now, now);
        }
        Object[][] lines = {
                {ExpenditureCategory.RENT, def.rent(), "Shop rent"},
                {ExpenditureCategory.EMPLOYEE_SALARY, payroll.longValue(), "Payroll — " + staffCount + " staff"},
                {ExpenditureCategory.EMPLOYEE_ACCOMMODATION_RENT, def.accommodation(), "Staff accommodation"},
                {ExpenditureCategory.MISCELLANEOUS, Math.round(def.utilities() * (0.9 + rnd.nextDouble() * 0.2)),
                        "Electricity, water & internet"}};
        for (Object[] line : lines) {
            w.add("branch_expenditures", UUID.randomUUID(), tenantId, br.id(), line[0], month,
                    BigDecimal.valueOf(((Number) line[1]).longValue()).setScale(2), line[2], true, false, now, now);
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private static ServiceRow pickService(List<ServiceRow> services, boolean female, Random rnd, ServiceRow exclude) {
        double total = 0;
        double[] weights = new double[services.size()];
        for (int i = 0; i < services.size(); i++) {
            ServiceRow s = services.get(i);
            if (s == exclude || s.gender().equals("F") && !female || s.gender().equals("M") && female) continue;
            weights[i] = s.weight();
            total += s.weight();
        }
        double roll = rnd.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0 && weights[i] > 0) return services.get(i);
        }
        return services.get(0);
    }

    private static StaffRow staffFor(List<StaffRow> staff, String skill, Random rnd) {
        List<StaffRow> pool = staff.stream().filter(s -> s.skills() != null && s.skills().contains(skill)).toList();
        List<StaffRow> from = pool.isEmpty() ? staff : pool;
        return from.get(rnd.nextInt(from.size()));
    }

    private static double branchFactor(String code) {
        return DemoBrandCatalog.BRANCHES.stream().filter(b -> b.code().equals(code))
                .mapToDouble(DemoBrandCatalog.BranchDef::volumeFactor).findFirst().orElse(1.0);
    }

    /** Minutes of 10:00–21:00 opening hours inside (from, to]. */
    private static double openMinutesBetween(Instant from, Instant to) {
        double total = 0;
        for (LocalDate d = from.atZone(ZONE).toLocalDate(); !d.isAfter(to.atZone(ZONE).toLocalDate()); d = d.plusDays(1)) {
            Instant open = d.atTime(10, 0).atZone(ZONE).toInstant();
            Instant close = d.atTime(21, 0).atZone(ZONE).toInstant();
            Instant s = from.isAfter(open) ? from : open;
            Instant e = to.isBefore(close) ? to : close;
            if (e.isAfter(s)) total += Duration.between(s, e).toMinutes();
        }
        return total;
    }

    private static Instant randomOpenInstant(Instant from, Instant to, Random rnd) {
        for (int attempt = 0; attempt < 20; attempt++) {
            long span = Duration.between(from, to).toSeconds();
            if (span <= 0) return null;
            Instant t = from.plusSeconds((long) (rnd.nextDouble() * span));
            LocalTime local = t.atZone(ZONE).toLocalTime();
            if (!local.isBefore(LocalTime.of(10, 30)) && local.isBefore(LocalTime.of(21, 30))) return t;
        }
        return null;
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal percent) {
        return base.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static <T> T pick(List<T> list, Random rnd) {
        return list.get(rnd.nextInt(list.size()));
    }
}
