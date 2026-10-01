package com.salonplatform.demo;

import com.salonplatform.demo.DemoBrandCatalog.BranchDef;
import com.salonplatform.domain.entity.Branch;
import com.salonplatform.domain.entity.Tenant;
import com.salonplatform.domain.entity.User;
import com.salonplatform.domain.enums.BranchStatus;
import com.salonplatform.domain.enums.OutboundMessagingMode;
import com.salonplatform.domain.enums.SalonTier;
import com.salonplatform.domain.enums.TenantStatus;
import com.salonplatform.domain.enums.UserRole;
import com.salonplatform.domain.repository.BranchRepository;
import com.salonplatform.domain.repository.TenantRepository;
import com.salonplatform.domain.repository.UserRepository;
import com.salonplatform.exception.BadRequestException;
import com.salonplatform.service.AttendancePhotoStorageService;
import com.salonplatform.service.ProductionTenantGuard;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Builds (or rebuilds) the demo brand on a single low-priority background thread. Nothing here runs
 * at startup: a platform admin triggers it, and every write is scoped to the demo tenant, batched and
 * throttled so other brands' traffic is unaffected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoDataJobService {

    public enum State { IDLE, RUNNING, SUCCEEDED, FAILED }

    public record Status(State state, String slug, Instant startedAt, Instant finishedAt, String message,
                         Map<String, Integer> rowsWritten) {}

    /** Child tables without tenant_id, purged through their tenant-scoped parent. */
    private static final List<String[]> CHILD_PURGES = List.of(
            new String[]{"booking_line_items", "booking_id", "bookings"},
            new String[]{"payment_splits", "payment_id", "payments"},
            new String[]{"service_package_plan_items", "plan_id", "service_package_plans"},
            new String[]{"customer_package_entitlements", "subscription_id", "customer_package_subscriptions"},
            new String[]{"scratch_campaign_prizes", "campaign_id", "scratch_campaigns"},
            new String[]{"invoice_sequences", "branch_id", "branches"},
            new String[]{"refresh_tokens", "user_id", "users"},
            new String[]{"password_reset_tokens", "user_id", "users"});

    private final DemoDataProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProductionTenantGuard productionTenantGuard;
    private final AttendancePhotoStorageService photoStorage;

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicReference<Status> status =
            new AtomicReference<>(new Status(State.IDLE, null, null, null, null, Map.of()));
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "demo-data-job");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public Status status() {
        return status.get();
    }

    ReentrantLock lock() {
        return lock;
    }

    /** Queue a full rebuild of the configured demo brand; returns immediately. */
    public Status startRebuild() {
        assertEnabled();
        String slug = properties.getSlug();
        assertRebuildable(slug);
        // Bills, payments and attendance notes are attributed to these logins, so both must exist.
        if (isBlank(properties.getOwnerPassword()) || isBlank(properties.getManagerPassword())) {
            throw new BadRequestException("Set APP_DEMO_OWNER_PASSWORD and APP_DEMO_MANAGER_PASSWORD before building the demo brand");
        }
        if (lock.isLocked() || status.get().state() == State.RUNNING) {
            throw new BadRequestException("A demo data job is already running");
        }
        Status running = new Status(State.RUNNING, slug, Instant.now(), null, "Queued", Map.of());
        status.set(running);
        executor.submit(() -> runRebuild(slug));
        return running;
    }

    private void runRebuild(String slug) {
        lock.lock();
        Instant startedAt = status.get().startedAt();
        try {
            Map<String, Integer> rows = rebuild(slug);
            status.set(new Status(State.SUCCEEDED, slug, startedAt, Instant.now(), "Demo brand rebuilt", rows));
            log.info("Demo data rebuild for {} finished: {}", slug, rows);
        } catch (Exception e) {
            log.error("Demo data rebuild for {} failed", slug, e);
            status.set(new Status(State.FAILED, slug, startedAt, Instant.now(), e.getMessage(), Map.of()));
        } finally {
            lock.unlock();
        }
    }

    private Map<String, Integer> rebuild(String slug) {
        Instant now = Instant.now();
        Tenant tenant = tenantRepository.findBySlug(slug).orElse(null);
        if (tenant != null) {
            progress("Removing previous demo data");
            purge(tenant.getId());
        } else {
            tenant = tenantRepository.save(Tenant.builder()
                    .name(properties.getBrandName())
                    .slug(slug)
                    .primaryColor(properties.getPrimaryColor())
                    .status(TenantStatus.ACTIVE)
                    .benchmarkOptIn(true)
                    .marketCity("Bangalore")
                    .salonTier(SalonTier.PREMIUM)
                    .gstEnabled(true)
                    .onlineBookingEnabled(true)
                    .demoTenant(true)
                    .outboundMessagingMode(OutboundMessagingMode.SIMULATE)
                    .build());
        }
        UUID tenantId = tenant.getId();
        tenant.setName(properties.getBrandName());
        tenant.setDemoTenant(true);
        tenant.setOutboundMessagingMode(OutboundMessagingMode.SIMULATE);
        tenantRepository.save(tenant);

        progress("Creating branches and logins");
        UUID ownerId = createUser(tenantId, null, "Brand Owner",
                properties.getBrandName().split("\\s+")[0].toLowerCase(), UserRole.BRAND_ADMIN, properties.getOwnerPassword());
        DemoRowWriter writer = new DemoRowWriter(jdbcTemplate, properties.getBatchSize(), properties.getPauseMillisBetweenBatches());
        DemoHistoryGenerator.declareTables(writer);
        DemoHistoryGenerator generator = new DemoHistoryGenerator(writer, properties.getRandomSeed(), tenantId,
                properties.getBrandName(), now, properties.getHistoryDays(), properties.getBillsPerBranchPerDay(),
                reservedPhones(), ownerId, (st, avatar) -> storeAvatar(tenantId, st, avatar));
        for (BranchDef def : DemoBrandCatalog.BRANCHES) {
            Branch branch = branchRepository.save(Branch.builder()
                    .tenantId(tenantId)
                    .name(properties.getBrandName().split("\\s+")[0] + " " + def.name())
                    .code(def.code())
                    .address(def.address())
                    .societyDefault(def.locality())
                    .gstin(def.gstin())
                    .phone(def.phone())
                    .openTime("10:00")
                    .closeTime("21:00")
                    .latitude(def.lat())
                    .longitude(def.lng())
                    .monthlySalesTarget(DemoHistoryGenerator.money(def.monthlyTarget()))
                    .status(BranchStatus.ACTIVE)
                    .businessType(def.businessType())
                    .googleReviewAutoPublish(false)
                    .googleRating(def.googleRating())
                    .googleReviewCount(def.googleReviewCount())
                    .googleLowRatingReviewCount((int) Math.round(def.googleReviewCount() * (5 - def.googleRating()) * 0.05))
                    .googleReviewsSampleSize(50)
                    .gbpPhotoCount(60 + def.googleReviewCount() / 20)
                    .gbpVideoCount(def.searchRank() <= 2 ? 4 : 1)
                    .gbpHasPhone(true)
                    .gbpHasWebsite(true)
                    .gbpHasHours(true)
                    .gbpHasBookButton(def.searchRank() <= 3)
                    .gbpServicesListedCount(40)
                    .estimatedSearchRank(def.searchRank())
                    // Fictional listing snapshot; the maps link is a plain locality search, not a business.
                    .googlePlaceId("demo-" + slug + "-" + def.code().toLowerCase())
                    .googleMapsUrl(DemoHistoryGenerator.mapsSearchUrl(def.locality() + " Bengaluru"))
                    .googleFormattedAddress(def.address())
                    .digitalPresenceUpdatedAt(now.minus(java.time.Duration.ofHours(3)))
                    .onlineBookingEnabled(true)
                    .build());
            UUID managerId = createUser(tenantId, branch.getId(), def.managerName(), def.managerLocalPart(),
                    UserRole.SALON_MANAGER, properties.getManagerPassword());
            generator.attachBranch(def, branch.getId(), managerId);
        }

        progress("Writing catalog, staff and stock");
        generator.writeCatalog();
        progress("Simulating " + properties.getHistoryDays() + " days of business");
        generator.simulate();
        jdbcTemplate.update("INSERT INTO audit_logs (id, tenant_id, branch_id, user_id, action, entity_type, entity_id, details, created_at) "
                        + "VALUES (?, ?, NULL, NULL, ?, 'TENANT', ?, ?, now())",
                UUID.randomUUID(), tenantId, DemoHistoryGenerator.MARKER_ACTION, tenantId,
                "{\"historyStart\":\"" + generator.startDate() + "\",\"bills\":" + generator.billCount()
                        + ",\"seed\":" + properties.getRandomSeed() + "}");
        return new LinkedHashMap<>(writer.writtenCounts());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private UUID createUser(UUID tenantId, UUID branchId, String name, String localPart, UserRole role, String password) {
        String email = localPart + "@" + properties.getEmailDomain();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BadRequestException("Login " + email + " already belongs to another brand");
        }
        return userRepository.save(User.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .preferredLocale("en-IN")
                .active(true)
                .build()).getId();
    }

    private String storeAvatar(UUID tenantId, DemoHistoryGenerator.St st, String avatar) {
        try (InputStream in = new ClassPathResource("demo/staff-avatars/" + avatar + ".png").getInputStream()) {
            return photoStorage.storeGenerated(tenantId, st.br.id, st.id, in.readAllBytes());
        } catch (IOException e) {
            log.warn("Demo staff portrait {} missing: {}", avatar, e.getMessage());
            return null;
        }
    }

    /** Phones already used by real brands — demo customers must never collide with them. */
    private Set<String> reservedPhones() {
        Set<String> phones = new HashSet<>();
        String realTenants = "SELECT id FROM tenants WHERE COALESCE(demo_tenant, false) = false";
        for (String sql : List.of(
                "SELECT phone FROM customers WHERE tenant_id IN (" + realTenants + ")",
                "SELECT phone FROM staff WHERE tenant_id IN (" + realTenants + ")",
                "SELECT mobile FROM marketing_enquiries WHERE tenant_id IN (" + realTenants + ")")) {
            jdbcTemplate.query(sql, rs -> {
                String digits = rs.getString(1) == null ? "" : rs.getString(1).replaceAll("[^0-9]", "");
                if (digits.length() >= 10) phones.add(digits.substring(digits.length() - 10));
            });
        }
        return phones;
    }

    /** Batched delete of everything the demo tenant owns (tenant row kept so its id stays stable). */
    private void purge(UUID tenantId) {
        jdbcTemplate.query("SELECT DISTINCT entry_photo_key FROM attendance_records WHERE tenant_id = ? AND entry_photo_key IS NOT NULL",
                rs -> {
                    photoStorage.delete(rs.getString(1));
                }, tenantId);
        Set<String> tables = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.columns WHERE table_schema = 'public' AND column_name = 'tenant_id'",
                String.class));
        Set<String> allTables = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class));
        for (String[] child : CHILD_PURGES) {
            if (!allTables.contains(child[0]) || !tables.contains(child[2])) continue;
            deleteInBatches("DELETE FROM " + child[0] + " WHERE ctid IN (SELECT c.ctid FROM " + child[0] + " c JOIN "
                    + child[2] + " p ON p.id = c." + child[1] + " WHERE p.tenant_id = ? LIMIT 5000)", tenantId);
        }
        List<String> ordered = new ArrayList<>(tables);
        ordered.removeIf(t -> t.equals("tenants") || t.startsWith("sales_"));
        // customers reference branches; delete branches last.
        ordered.sort(Comparator.comparing((String t) -> t.equals("branches")).thenComparing(t -> t));
        for (String table : ordered) {
            deleteInBatches("DELETE FROM " + table + " WHERE ctid IN (SELECT ctid FROM " + table
                    + " WHERE tenant_id = ? LIMIT 5000)", tenantId);
        }
    }

    private void deleteInBatches(String sql, UUID tenantId) {
        int deleted;
        do {
            deleted = jdbcTemplate.update(sql, tenantId);
            if (deleted > 0 && properties.getPauseMillisBetweenBatches() > 0) {
                try {
                    Thread.sleep(properties.getPauseMillisBetweenBatches());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Demo purge interrupted", e);
                }
            }
        } while (deleted > 0);
    }

    void assertEnabled() {
        if (!properties.isEnabled()) {
            throw new BadRequestException("Demo data generation is disabled (app.demo-data.enabled=false)");
        }
    }

    /** Only an existing demo brand (or a brand-new slug) may be rebuilt; protected brands never. */
    private void assertRebuildable(String slug) {
        if (productionTenantGuard.isProtectedSlug(slug)) {
            throw new BadRequestException("Refusing to touch protected brand " + slug);
        }
        tenantRepository.findBySlug(slug).ifPresent(t -> {
            if (!t.isDemo()) {
                throw new BadRequestException("Brand " + slug + " exists and is not marked as a demo brand");
            }
        });
    }

    private void progress(String message) {
        Status s = status.get();
        status.set(new Status(State.RUNNING, s.slug(), s.startedAt(), null, message, Map.of()));
        log.info("Demo data: {}", message);
    }

    static LocalDate today() {
        return LocalDate.now(DemoHistoryGenerator.ZONE);
    }
}
