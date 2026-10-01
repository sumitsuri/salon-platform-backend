package com.salonplatform.demo;

import com.salonplatform.demo.DemoBrandCatalog.BranchDef;
import com.salonplatform.demo.DemoBrandCatalog.CampaignDef;
import com.salonplatform.demo.DemoBrandCatalog.CampaignKind;
import com.salonplatform.demo.DemoBrandCatalog.CompetitorDef;
import com.salonplatform.demo.DemoBrandCatalog.MembershipDef;
import com.salonplatform.demo.DemoBrandCatalog.PackageDef;
import com.salonplatform.demo.DemoBrandCatalog.ProductDef;
import com.salonplatform.demo.DemoBrandCatalog.ServiceDef;
import com.salonplatform.demo.DemoBrandCatalog.StaffDef;
import com.salonplatform.demo.DemoBrandCatalog.Story;
import com.salonplatform.domain.enums.*;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Simulates a year of the demo chain day by day: customers with visit rhythms and churn, bills with
 * GST/membership/package/promo math matching {@code GstCalculationService}, campaigns that pull
 * customers back, reviews, attendance, stock movements and monthly P&L lines. All rows belong to
 * one demo tenant and are streamed through {@link DemoRowWriter}.
 */
@Slf4j
class DemoHistoryGenerator {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final String MARKER_ACTION = "DEMO_DATA_GENERATED";
    private static final BigDecimal GST_RATE = new BigDecimal("18.00");
    private static final BigDecimal HALF_GST_PERCENT = new BigDecimal("9");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Set<String> WASTAGE_PRONE = Set.of("CLRT", "DEV6", "BLCH", "KERK", "SMTK", "SPAC", "RWAX", "MOIL");
    private static final Set<String> COST_SPIKE_SKUS = Set.of("CLRT", "DEV6", "BLCH", "KERK", "SMTK");
    private static final double[] HOUR_WEIGHTS = {0.5, 0.8, 1.1, 1.2, 1.0, 0.8, 0.9, 1.2, 1.4, 1.3, 0.8};

    enum Segment { LOYAL, REGULAR, OCCASIONAL, ONE_TIME }

    // ---- simulation state -------------------------------------------------------------------

    static final class Br {
        BranchDef def;
        UUID id;
        UUID managerUserId;
        double priceFactor;
        final List<St> staff = new ArrayList<>();
        final Map<Svc, UUID> branchServiceIds = new HashMap<>();
        final Map<Svc, BigDecimal> prices = new HashMap<>();
        final PriorityQueue<Cust> due = new PriorityQueue<>(Comparator.comparing((Cust c) -> c.nextDue));
        final List<Cust> customers = new ArrayList<>();
        final Map<String, Long> invoiceSeq = new HashMap<>();
        double[] balance;
        double[] weekUsage;
        final Map<YearMonth, Double> productCost = new HashMap<>();
        int passSeq = 100_000;

        BranchDef def() {
            return def;
        }
    }

    static final class St {
        StaffDef def;
        UUID id;
        Br br;
        Set<String> skills;
        LocalDate joined;
        String photoKey;
        final Set<LocalDate> leaveDays = new HashSet<>();
        final Map<YearMonth, Integer> lateCount = new HashMap<>();
        final Map<YearMonth, BigDecimal> sales = new HashMap<>();
    }

    static final class Svc {
        ServiceDef def;
        UUID id;
        UUID categoryId;
        final Map<String, Double> usage = new LinkedHashMap<>();
    }

    static final class Prod {
        ProductDef def;
        UUID id;
        int idx;
    }

    static final class Cust {
        UUID id;
        Br br;
        String name;
        String phone;
        boolean female;
        Segment segment;
        Instant createdAt;
        LocalDate nextDue;
        boolean churned;
        boolean comeback;
        boolean renewMembership;
        boolean whatsappOptIn;
        boolean smsOptIn;
        String email;
        String society;
        String flat;
        String passId;
        String passToken;
        int visits;
        BigDecimal spend = BigDecimal.ZERO;
        Instant lastVisit;
        Member member;
        final List<Pkg> packages = new ArrayList<>();
        final List<Member> pastMembers = new ArrayList<>();
    }

    static final class Member {
        UUID id;
        int planIdx;
        LocalDate startsOn;
        LocalDate endsOn;
        BigDecimal paid;
        PaymentMode mode;
        UUID soldByStaff;
        UUID soldByUser;
        Instant createdAt;
        Br br;
        String cardNumber;
    }

    static final class Pkg {
        UUID id;
        int planIdx;
        LocalDate purchasedOn;
        LocalDate expiresOn;
        UUID invoiceId;
        UUID bookingId;
        UUID soldByStaff;
        UUID soldByUser;
        Br br;
        Instant createdAt;
        final Map<Svc, int[]> remaining = new LinkedHashMap<>();
        final Map<Svc, UUID> entitlementIds = new LinkedHashMap<>();
    }

    private record Line(Svc svc, St staff, BigDecimal unitPrice, Pkg pkg) {}

    // ---- inputs -------------------------------------------------------------------------------

    private final DemoRowWriter w;
    private final Random rnd;
    private final UUID tenantId;
    private final String brandShort;
    private final Instant now;
    private final LocalDate today;
    private final LocalDate start;
    private final int billsPerBranchPerDay;
    private final Set<String> reservedPhones;
    private final UUID ownerUserId;
    private final BiFunction<St, String, String> avatarStore;

    final List<Br> branches = new ArrayList<>();
    final List<St> allStaff = new ArrayList<>();
    final List<Svc> services = new ArrayList<>();
    final List<Prod> products = new ArrayList<>();
    final List<UUID> membershipPlanIds = new ArrayList<>();
    final List<UUID> packagePlanIds = new ArrayList<>();
    final List<Cust> allCustomers = new ArrayList<>();
    private final Map<String, Svc> serviceByName = new HashMap<>();
    private final Map<String, Prod> productBySku = new HashMap<>();
    private final Set<String> usedPhones = new HashSet<>();
    private final Set<String> usedPassTokens = new HashSet<>();

    private UUID couponWelcomeId;
    private UUID couponComebackId;
    private UUID offerFestiveId;
    private UUID offerMonsoonId;
    private UUID offerWeekdayId;
    private int welcomeRedemptions;
    private int comebackRedemptions;
    private int festiveRedemptions;
    private int monsoonRedemptions;
    private int weekdayRedemptions;
    private int bills;

    DemoHistoryGenerator(DemoRowWriter writer, long seed, UUID tenantId, String brandName, Instant now,
                         int historyDays, int billsPerBranchPerDay, Set<String> reservedPhones,
                         UUID ownerUserId, BiFunction<St, String, String> avatarStore) {
        this.w = writer;
        this.rnd = new Random(seed);
        this.tenantId = tenantId;
        this.brandShort = brandName.split("\\s+")[0];
        this.now = now;
        this.today = now.atZone(ZONE).toLocalDate();
        this.start = today.minusDays(historyDays);
        this.billsPerBranchPerDay = billsPerBranchPerDay;
        this.reservedPhones = reservedPhones;
        this.ownerUserId = ownerUserId;
        this.avatarStore = avatarStore;
    }

    LocalDate startDate() {
        return start;
    }

    int billCount() {
        return bills;
    }

    // ---- table declarations (parents first) ---------------------------------------------------

    static void declareTables(DemoRowWriter w) {
        w.table("service_categories", "id", "tenant_id", "name", "parent_category_id", "sort_order", "active", "created_at");
        w.table("services", "id", "tenant_id", "category_id", "name", "description", "sac_code", "gst_rate",
                "duration_minutes", "list_price", "active", "variable_pricing", "created_at");
        w.table("branch_services", "id", "tenant_id", "branch_id", "service_id", "price", "display_name_override",
                "active", "manual_price_override", "created_at", "updated_at");
        w.table("staff", "id", "tenant_id", "branch_id", "name", "phone", "role", "skills", "biometric_id", "salary",
                "joining_date", "id_proof_collected", "id_proof_reference", "monthly_sales_target", "incentive_percent",
                "active", "created_at", "updated_at");
        w.table("vendors", "id", "tenant_id", "name", "contact_phone", "contact_email", "notes", "active", "created_at", "updated_at");
        w.table("inventory_products", "id", "tenant_id", "vendor_id", "name", "sku", "category", "unit", "unit_cost",
                "retail_price", "reorder_level", "active", "created_at", "updated_at");
        w.table("membership_plans", "id", "tenant_id", "name", "description", "cadence", "fee_amount", "benefit_percent",
                "service_scope", "scope_ids", "branch_ids", "status", "created_by_user_id", "created_at", "updated_at");
        w.table("service_package_plans", "id", "tenant_id", "name", "description", "list_price_total", "package_price",
                "validity_days", "plan_type", "credit_value", "redemption_mode", "branch_ids", "status", "predefined_rank",
                "created_by_user_id", "created_at", "updated_at");
        w.table("service_package_plan_items", "id", "plan_id", "service_id", "quantity", "sort_order");
        w.table("local_competitors", "id", "tenant_id", "branch_id", "name", "competitor_type", "address", "notes",
                "revenue_per_branch_day", "avg_ticket", "retail_attach_percent", "net_margin_percent", "repeat_visit_rate",
                "google_rating", "google_review_count", "google_low_rating_review_count", "google_reviews_sample_size",
                "gbp_photo_count", "gbp_video_count", "gbp_has_phone", "estimated_search_rank", "google_place_id",
                "google_maps_url", "google_auto_discovered", "google_synced_at", "active", "created_at", "updated_at");
        w.table("bookings", "id", "tenant_id", "branch_id", "customer_id", "created_by_user_id", "status",
                "bill_discount_type", "bill_discount_value", "bill_discount_scope", "bill_discount_note", "coupon_id",
                "offer_id", "membership_subscription_id", "membership_discount_amount", "promo_discount_amount", "source",
                "scheduled_start_at", "scheduled_end_at", "staff_id", "manage_token", "service_started_at",
                "estimated_end_at", "actual_duration_minutes", "created_at", "updated_at", "completed_at");
        w.table("booking_line_items", "id", "booking_id", "branch_service_id", "service_id", "staff_id", "service_name",
                "unit_price", "quantity", "gst_rate", "estimated_duration_minutes", "started_at", "ended_at",
                "actual_duration_minutes", "package_subscription_id");
        w.table("invoices", "id", "tenant_id", "branch_id", "booking_id", "customer_id", "invoice_number", "subtotal",
                "discount_amount", "membership_discount_amount", "promo_discount_amount", "coupon_id", "offer_id",
                "membership_subscription_id", "membership_label", "membership_fee_amount", "membership_fee_label",
                "package_fee_amount", "package_fee_label", "customer_package_subscription_id", "promo_label",
                "taxable_amount", "cgst_amount", "sgst_amount", "grand_total", "branch_gstin", "customer_name",
                "customer_phone", "customer_society", "customer_flat", "issued_at");
        w.table("payments", "id", "tenant_id", "branch_id", "booking_id", "invoice_id", "mode", "amount", "reference",
                "recorded_by_user_id", "paid_at");
        w.table("payment_splits", "id", "payment_id", "mode", "amount", "reference");
        w.table("message_delivery_logs", "id", "tenant_id", "campaign_id", "campaign_run_id", "customer_id", "invoice_id",
                "channel", "recipient_phone", "status", "provider_message_id", "error_message", "created_at");
        w.table("review_invitations", "id", "tenant_id", "branch_id", "branch_name", "visit_id", "invoice_id",
                "customer_id", "customer_first_name", "google_review_url", "status", "expires_at", "submitted_at", "created_at");
        w.table("reviews", "id", "invitation_id", "tenant_id", "branch_id", "visit_id", "overall_rating", "service_rating",
                "ambience_rating", "staff_rating", "cleanliness_rating", "value_rating", "improvement_tags", "comment",
                "google_review_redirected", "submitted_at");
        w.table("review_recoveries", "id", "review_id", "tenant_id", "branch_id", "visit_id", "overall_rating", "status",
                "notes", "created_at", "updated_at", "resolved_at");
        w.table("attendance_records", "id", "tenant_id", "branch_id", "staff_id", "work_date", "entry_time", "exit_time",
                "entry_method", "exit_method", "manual_reason", "recorded_by_user_id", "entry_latitude", "entry_longitude",
                "entry_accuracy_meters", "exit_latitude", "exit_longitude", "exit_accuracy_meters", "entry_geo_status",
                "exit_geo_status", "entry_photo_key", "exit_photo_key", "entry_verified", "exit_verified", "created_at",
                "updated_at");
        w.table("attendance_incidents", "id", "tenant_id", "staff_id", "branch_id", "attendance_record_id", "work_date",
                "type", "note", "penalty_amount", "created_by_user_id", "created_at");
        w.table("leave_records", "id", "tenant_id", "branch_id", "staff_id", "start_date", "end_date", "leave_type",
                "status", "reason", "created_by_user_id", "approved_by_user_id", "created_at", "updated_at");
        w.table("inventory_movements", "id", "tenant_id", "branch_id", "product_id", "movement_type", "quantity",
                "unit_cost", "total_cost", "movement_date", "note", "recorded_by_user_id", "created_at");
        w.table("branch_expenditures", "id", "tenant_id", "branch_id", "category", "expense_month", "amount",
                "description", "active", "manager_recorded", "created_at", "updated_at");
        w.table("marketing_campaigns", "id", "tenant_id", "name", "channel", "status", "message_text",
                "filter_last_visit_from", "filter_last_visit_to", "filter_whatsapp_opt_in_only", "filter_sms_opt_in_only",
                "filter_branch_id", "filter_membership_filter", "filter_membership_expiring_within_days",
                "recipient_count", "sent_count", "failed_count", "created_by_user_id", "sent_at", "created_at", "updated_at");
        w.table("campaign_runs", "id", "tenant_id", "campaign_id", "status", "recipient_count", "sent_count",
                "failed_count", "started_at", "completed_at");
        w.table("marketing_enquiries", "id", "tenant_id", "name", "mobile", "email", "society", "message", "created_at");
        w.table("customers", "id", "tenant_id", "branch_id", "name", "phone", "visit_pass_id", "identity_status",
                "pass_public_token", "society", "flat_unit", "notes", "email", "whatsapp_opt_in", "sms_opt_in",
                "visit_count", "lifetime_spend", "last_visit_at", "created_at", "updated_at");
        w.table("membership_subscriptions", "id", "tenant_id", "customer_id", "plan_id", "branch_id", "card_number",
                "starts_on", "ends_on", "status", "amount_paid", "payment_mode", "payment_reference", "sold_by_user_id",
                "sold_by_staff_id", "created_at", "updated_at");
        w.table("customer_package_subscriptions", "id", "tenant_id", "customer_id", "branch_id", "plan_id", "plan_name",
                "plan_type", "credit_total", "credit_remaining", "redemption_mode", "amount_paid", "purchase_invoice_id",
                "purchase_booking_id", "purchased_on", "expires_on", "status", "sold_by_user_id", "sold_by_staff_id",
                "created_at");
        w.table("customer_package_entitlements", "id", "subscription_id", "service_id", "service_name",
                "quantity_total", "quantity_remaining");
        w.table("coupons", "id", "tenant_id", "name", "code", "description", "discount_type", "discount_value",
                "starts_at", "ends_at", "service_scope", "scope_ids", "branch_ids", "status", "max_redemptions_total",
                "redemption_count", "created_by_user_id", "created_at", "updated_at");
        w.table("offers", "id", "tenant_id", "name", "description", "discount_type", "discount_value", "starts_at",
                "ends_at", "service_scope", "scope_ids", "branch_ids", "status", "max_redemptions_total",
                "redemption_count", "created_by_user_id", "created_at", "updated_at");
        w.table("invoice_sequences", "id", "branch_id", "fiscal_year", "last_sequence");
        w.table("branch_inventory", "id", "tenant_id", "branch_id", "product_id", "quantity", "updated_at");
    }

    // ---- setup --------------------------------------------------------------------------------

    /** Registers branch ids created by the job (branches/users are written outside the generator). */
    void attachBranch(BranchDef def, UUID branchId, UUID managerUserId) {
        Br br = new Br();
        br.def = def;
        br.id = branchId;
        br.managerUserId = managerUserId;
        br.priceFactor = switch (def.story()) {
            case FLAGSHIP, GROWTH -> 1.05;
            case RETENTION_DIP -> 1.0;
            case COST_SPIKE -> 0.97;
            case MEMBERSHIP_HEAVY -> 0.95;
        };
        branches.add(br);
    }

    void writeCatalog() {
        Instant catalogAt = start.minusDays(420).atStartOfDay(ZONE).toInstant();
        Map<String, UUID> categoryIds = new LinkedHashMap<>();
        int sort = 1;
        for (String category : DemoBrandCatalog.CATEGORIES) {
            UUID id = UUID.randomUUID();
            categoryIds.put(category, id);
            w.add("service_categories", id, tenantId, category, null, sort++, true, catalogAt);
        }
        for (ServiceDef def : DemoBrandCatalog.SERVICES) {
            Svc svc = new Svc();
            svc.def = def;
            svc.id = UUID.randomUUID();
            svc.categoryId = categoryIds.get(def.category());
            for (String part : def.usage().split(";")) {
                if (part.isBlank()) continue;
                String[] kv = part.split(":");
                svc.usage.put(kv[0], Double.parseDouble(kv[1]));
            }
            services.add(svc);
            serviceByName.put(def.name(), svc);
            w.add("services", svc.id, tenantId, svc.categoryId, def.name(), null, "999721", GST_RATE,
                    def.minutes(), money(def.price()), true, false, catalogAt);
        }
        for (Br br : branches) {
            for (Svc svc : services) {
                BigDecimal price = money(Math.round(svc.def.price() * br.priceFactor / 10.0) * 10.0);
                UUID bsId = UUID.randomUUID();
                br.branchServiceIds.put(svc, bsId);
                br.prices.put(svc, price);
                w.add("branch_services", bsId, tenantId, br.id, svc.id, price, null, true,
                        price.compareTo(money(svc.def.price())) != 0, catalogAt, catalogAt);
            }
        }

        int staffNo = 1;
        for (StaffDef def : DemoBrandCatalog.STAFF) {
            Br br = branch(def.branchCode());
            St st = new St();
            st.def = def;
            st.id = UUID.randomUUID();
            st.br = br;
            st.skills = new HashSet<>(Arrays.asList(def.skills().split(",")));
            st.joined = today.minusDays(def.joinedDaysAgo());
            br.staff.add(st);
            allStaff.add(st);
            Instant createdAt = st.joined.atTime(9, 0).atZone(ZONE).toInstant();
            w.add("staff", st.id, tenantId, br.id, def.name(), null, StaffRole.STYLIST, def.skills(),
                    "AUR-" + String.format("%03d", staffNo++), money(def.salary()), st.joined, true,
                    "Aadhaar XXXX" + (1000 + rnd.nextInt(9000)), money(def.monthlyTarget()),
                    BigDecimal.valueOf(def.incentivePercent()).setScale(2, RoundingMode.HALF_UP), true, createdAt, createdAt);
        }

        List<UUID> vendorIds = new ArrayList<>();
        for (String vendor : DemoBrandCatalog.VENDORS) {
            UUID id = UUID.randomUUID();
            vendorIds.add(id);
            w.add("vendors", id, tenantId, vendor, null, null, null, true, catalogAt, catalogAt);
        }
        int idx = 0;
        for (ProductDef def : DemoBrandCatalog.PRODUCTS) {
            Prod p = new Prod();
            p.def = def;
            p.id = UUID.randomUUID();
            p.idx = idx++;
            products.add(p);
            productBySku.put(def.sku(), p);
            w.add("inventory_products", p.id, tenantId, vendorIds.get(def.vendor()), def.name(), "AUR-" + def.sku(),
                    def.category(), def.unit(), BigDecimal.valueOf(def.unitCost()).setScale(2, RoundingMode.HALF_UP),
                    def.retailPrice() != null ? money(def.retailPrice()) : null, BigDecimal.valueOf(def.reorderLevel()),
                    true, catalogAt, catalogAt);
        }
        for (Br br : branches) {
            br.balance = new double[products.size()];
            br.weekUsage = new double[products.size()];
        }

        for (MembershipDef def : DemoBrandCatalog.MEMBERSHIPS) {
            UUID id = UUID.randomUUID();
            membershipPlanIds.add(id);
            w.add("membership_plans", id, tenantId, rebrand(def.name()), def.description(), def.cadence(),
                    money(def.fee()), BigDecimal.valueOf(def.benefitPercent()).setScale(2, RoundingMode.HALF_UP),
                    ServiceScopeType.ALL, null, null, PromoStatus.ACTIVE, ownerUserId, catalogAt, catalogAt);
        }
        for (PackageDef def : DemoBrandCatalog.PACKAGES) {
            UUID id = UUID.randomUUID();
            packagePlanIds.add(id);
            BigDecimal listTotal = BigDecimal.ZERO;
            for (String item : def.items()) {
                Svc svc = packageItemService(item);
                listTotal = listTotal.add(money(svc.def.price()).multiply(BigDecimal.valueOf(packageItemQty(item))));
            }
            w.add("service_package_plans", id, tenantId, def.name(), def.description(), listTotal,
                    money(def.packagePrice()), def.validityDays(), PackagePlanType.SERVICE_BUNDLE, null,
                    PackageRedemptionMode.MULTI_VISIT, null, PromoStatus.ACTIVE, null, ownerUserId, catalogAt, catalogAt);
            int itemSort = 0;
            for (String item : def.items()) {
                w.add("service_package_plan_items", UUID.randomUUID(), id, packageItemService(item).id,
                        packageItemQty(item), itemSort++);
            }
        }

        for (CompetitorDef def : DemoBrandCatalog.COMPETITORS) {
            Br br = branch(def.branchCode());
            w.add("local_competitors", UUID.randomUUID(), tenantId, br.id, def.name(),
                    def.aspirational() ? CompetitorType.ASPIRATIONAL : CompetitorType.LOCAL, def.address(),
                    "Fictional competitor for the demo brand", money(def.revenuePerDay()), money(def.avgTicket()),
                    BigDecimal.valueOf(def.aspirational() ? 14 : 8), BigDecimal.valueOf(def.aspirational() ? 22 : 15),
                    BigDecimal.valueOf(def.repeatRate() * 100).setScale(1, RoundingMode.HALF_UP), def.rating(), def.reviews(),
                    (int) Math.round(def.reviews() * (5 - def.rating()) * 0.06), Math.min(def.reviews(), 50),
                    def.aspirational() ? 140 : 40, def.aspirational() ? 6 : 1, true, def.searchRank(),
                    "demo-rival-" + Math.abs(def.name().hashCode()), mapsSearchUrl(def.name() + " " + def.address()),
                    true, now.minus(Duration.ofHours(3)), true, catalogAt, catalogAt);
        }

        couponWelcomeId = UUID.randomUUID();
        couponComebackId = UUID.randomUUID();
        offerFestiveId = UUID.randomUUID();
        offerMonsoonId = UUID.randomUUID();
        offerWeekdayId = UUID.randomUUID();

        for (St st : allStaff) {
            planLeaves(st);
            st.photoKey = avatarStore.apply(st, st.def.avatar());
        }
        w.flushAll();
    }

    // ---- main loop ----------------------------------------------------------------------------

    void simulate() {
        seedOpeningStock();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            int dayIdx = (int) ChronoUnit.DAYS.between(start, d);
            for (CampaignDef campaign : DemoBrandCatalog.CAMPAIGNS) {
                if (campaign.dayOffset() == dayIdx) {
                    runCampaign(campaign, d);
                }
            }
            for (Br br : branches) {
                simulateBranchDay(br, d);
            }
            for (St st : allStaff) {
                attendance(st, d);
            }
            enquiries(d);
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY || d.equals(today)) {
                for (Br br : branches) {
                    closeInventoryWeek(br, d);
                }
            }
            if (d.equals(d.withDayOfMonth(d.lengthOfMonth())) || d.equals(today)) {
                for (Br br : branches) {
                    monthlyExpenditure(br, YearMonth.from(d));
                }
                monthlyPenalties(YearMonth.from(d));
            }
            if (dayIdx % 30 == 0) {
                log.info("Demo data: simulated through {} ({} bills)", d, bills);
            }
        }
        upcomingAppointments();
        inProgressVisits();
        pendingLeaves();
        writeFinalState();
        w.flushAll();
    }

    // ---- bills --------------------------------------------------------------------------------

    private void simulateBranchDay(Br br, LocalDate d) {
        double expected = billsPerBranchPerDay * br.def().volumeFactor() * monthFactor(d) * weekdayFactor(d)
                * storyVolume(br, d) * (0.85 + rnd.nextDouble() * 0.3);
        int count = (int) Math.round(expected);
        List<Instant> arrivals = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            arrivals.add(arrival(d));
        }
        Collections.sort(arrivals);
        for (Instant arrivalAt : arrivals) {
            if (!arrivalAt.isBefore(now.minus(Duration.ofMinutes(75)))) {
                continue;
            }
            Cust cust = pickCustomer(br, d, arrivalAt);
            bill(br, cust, d, arrivalAt);
        }
    }

    private Cust pickCustomer(Br br, LocalDate d, Instant at) {
        long dayIdx = ChronoUnit.DAYS.between(start, d);
        double pNew = dayIdx < 45 ? 0.55 - dayIdx * 0.006 : 0.17;
        while (!br.due.isEmpty() && !br.due.peek().nextDue.isAfter(d)) {
            Cust c = br.due.poll();
            // Long-overdue guests mostly lapse quietly — they become the win-back pool.
            if (ChronoUnit.DAYS.between(c.nextDue, d) > 40 && rnd.nextDouble() < 0.6) {
                c.churned = true;
                continue;
            }
            if (rnd.nextDouble() >= pNew) {
                return c;
            }
            br.due.add(c);
            break;
        }
        return newCustomer(br, at);
    }

    private Cust newCustomer(Br br, Instant at) {
        Cust c = new Cust();
        c.id = UUID.randomUUID();
        c.br = br;
        c.female = rnd.nextDouble() < 0.62;
        String first = pick(c.female ? DemoBrandCatalog.FEMALE_NAMES : DemoBrandCatalog.MALE_NAMES);
        String last = pick(DemoBrandCatalog.SURNAMES);
        c.name = first + " " + last;
        c.phone = newPhone();
        double s = rnd.nextDouble();
        double loyalShare = br.def().story() == Story.MEMBERSHIP_HEAVY ? 0.2 : 0.12;
        c.segment = s < loyalShare ? Segment.LOYAL : s < loyalShare + 0.3 ? Segment.REGULAR
                : s < loyalShare + 0.63 ? Segment.OCCASIONAL : Segment.ONE_TIME;
        c.createdAt = at;
        c.whatsappOptIn = rnd.nextDouble() < 0.94;
        c.smsOptIn = rnd.nextDouble() < 0.8;
        c.email = rnd.nextDouble() < 0.3
                ? (first + "." + last).toLowerCase().replaceAll("[^a-z.]", "") + rnd.nextInt(100) + "@example.com" : null;
        c.society = pick(br.def().societies());
        c.flat = rnd.nextDouble() < 0.55 ? (char) ('A' + rnd.nextInt(6)) + "-" + (100 + rnd.nextInt(1400)) : null;
        c.passId = br.def().code() + "-" + (br.passSeq++);
        c.passToken = passToken();
        br.customers.add(c);
        allCustomers.add(c);
        return c;
    }

    private void bill(Br br, Cust cust, LocalDate d, Instant arrival) {
        boolean isNew = cust.visits == 0;
        if (cust.member != null && cust.member.endsOn.isBefore(d)) {
            cust.pastMembers.add(cust.member);
            cust.member = null;
        }
        for (Iterator<Pkg> it = cust.packages.iterator(); it.hasNext(); ) {
            Pkg p = it.next();
            if (p.expiresOn.isBefore(d) || p.remaining.values().stream().allMatch(r -> r[0] <= 0)) {
                closedPackages.computeIfAbsent(cust, k -> new ArrayList<>()).add(p);
                it.remove();
            }
        }

        // --- lines
        List<Line> lines = new ArrayList<>();
        Pkg redeemFrom = cust.packages.isEmpty() || rnd.nextDouble() > 0.75 ? null : cust.packages.get(0);
        if (redeemFrom != null) {
            Svc svc = redeemFrom.remaining.entrySet().stream().filter(e -> e.getValue()[0] > 0)
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (svc != null) {
                redeemFrom.remaining.get(svc)[0]--;
                lines.add(new Line(svc, staffFor(br, svc, d), BigDecimal.ZERO.setScale(2), redeemFrom));
            }
        }
        if (lines.isEmpty()) {
            Svc primary = pickService(cust, d, null);
            lines.add(new Line(primary, staffFor(br, primary, d), br.prices.get(primary), null));
        }
        if (rnd.nextDouble() < 0.3) {
            Svc addOn = pickService(cust, d, lines.get(0).svc());
            lines.add(new Line(addOn, staffFor(br, addOn, d), br.prices.get(addOn), null));
            if (rnd.nextDouble() < 0.2) {
                Svc third = pickService(cust, d, addOn);
                if (third != lines.get(0).svc()) {
                    lines.add(new Line(third, staffFor(br, third, d), br.prices.get(third), null));
                }
            }
        }

        // --- membership sale / benefit
        UUID managerId = br.managerUserId;
        int durationMinutes = lines.stream().mapToInt(l -> l.svc().def.minutes()).sum();
        Instant started = arrival.plus(Duration.ofMinutes(3 + rnd.nextInt(10)));
        Instant completed = started.plus(Duration.ofMinutes(durationMinutes + rnd.nextInt(12)));
        if (completed.isAfter(now)) {
            // Long services that began just before the cutoff: move the whole visit earlier so it has finished.
            Duration overflow = Duration.between(now, completed).plusMinutes(5 + rnd.nextInt(20));
            arrival = arrival.minus(overflow);
            started = started.minus(overflow);
            completed = completed.minus(overflow);
        }

        Member soldMembership = null;
        double membershipSaleP = br.def().story() == Story.MEMBERSHIP_HEAVY ? 0.05 : 0.022;
        if (cust.member == null && (cust.renewMembership || (!isNew && rnd.nextDouble() < membershipSaleP)
                || (isNew && rnd.nextDouble() < membershipSaleP / 3))) {
            soldMembership = sellMembership(cust, br, lines.get(0).staff(), d, completed);
            cust.renewMembership = false;
        }
        Member member = cust.member;
        BigDecimal benefit = member != null
                ? BigDecimal.valueOf(DemoBrandCatalog.MEMBERSHIPS.get(member.planIdx).benefitPercent()) : null;

        // --- promo: coupon XOR offer
        UUID couponId = null;
        UUID offerId = null;
        BigDecimal promoPercent = null;
        String promoLabel = null;
        String promoCategory = null;
        if (cust.comeback) {
            couponId = couponComebackId;
            promoPercent = BigDecimal.valueOf(15);
            promoLabel = "Coupon COMEBACK15 · We miss you";
            comebackRedemptions++;
            cust.comeback = false;
        } else if (isNew && rnd.nextDouble() < 0.25) {
            couponId = couponWelcomeId;
            promoPercent = BigDecimal.TEN;
            promoLabel = "Coupon WELCOME10 · First visit";
            welcomeRedemptions++;
        } else if (member == null) {
            long dayIdx = ChronoUnit.DAYS.between(start, d);
            if (dayIdx >= 15 && dayIdx <= 45 && rnd.nextDouble() < 0.5
                    && lines.stream().anyMatch(l -> l.svc().def.category().equals("Skin & Facials"))) {
                offerId = offerFestiveId;
                promoPercent = BigDecimal.valueOf(15);
                promoCategory = "Skin & Facials";
                promoLabel = "Offer · Festive Facial Fiesta";
                festiveRedemptions++;
            } else if (dayIdx >= 268 && dayIdx <= 305 && rnd.nextDouble() < 0.55
                    && lines.stream().anyMatch(l -> l.svc().def.category().equals("Hair Treatments"))) {
                offerId = offerMonsoonId;
                promoPercent = BigDecimal.valueOf(20);
                promoCategory = "Hair Treatments";
                promoLabel = "Offer · Monsoon Hair Rescue";
                monsoonRedemptions++;
            } else if ((d.getDayOfWeek() == DayOfWeek.TUESDAY || d.getDayOfWeek() == DayOfWeek.WEDNESDAY)
                    && rnd.nextDouble() < 0.18) {
                offerId = offerWeekdayId;
                promoPercent = BigDecimal.TEN;
                promoLabel = "Offer · Weekday Happy Hours";
                weekdayRedemptions++;
            }
        }

        // --- GST math (mirrors GstCalculationService: membership, then promo, GST, then bill discount)
        BigDecimal grossTotal = BigDecimal.ZERO;
        BigDecimal membershipDiscount = BigDecimal.ZERO;
        BigDecimal promoDiscount = BigDecimal.ZERO;
        BigDecimal taxableTotal = BigDecimal.ZERO;
        BigDecimal cgstTotal = BigDecimal.ZERO;
        for (Line line : lines) {
            BigDecimal gross = line.unitPrice();
            BigDecimal memberOff = benefit != null ? pct(gross, benefit) : BigDecimal.ZERO;
            BigDecimal afterMember = gross.subtract(memberOff);
            BigDecimal promoOff = BigDecimal.ZERO;
            if (promoPercent != null && line.pkg() == null
                    && (promoCategory == null || promoCategory.equals(line.svc().def.category()))) {
                promoOff = pct(afterMember, promoPercent);
            }
            BigDecimal taxable = afterMember.subtract(promoOff);
            BigDecimal half = pct(taxable, HALF_GST_PERCENT);
            grossTotal = grossTotal.add(gross);
            membershipDiscount = membershipDiscount.add(memberOff);
            promoDiscount = promoDiscount.add(promoOff);
            taxableTotal = taxableTotal.add(taxable);
            cgstTotal = cgstTotal.add(half);
        }
        if (promoDiscount.signum() == 0) {
            couponId = null;
            offerId = null;
            promoLabel = null;
        }
        BigDecimal preBill = taxableTotal.add(cgstTotal).add(cgstTotal);
        DiscountType billDiscountType = null;
        BigDecimal billDiscountValue = null;
        BigDecimal manual = BigDecimal.ZERO;
        if (preBill.compareTo(BigDecimal.valueOf(800)) > 0 && rnd.nextDouble() < 0.05) {
            if (rnd.nextBoolean()) {
                billDiscountType = DiscountType.FLAT;
                billDiscountValue = BigDecimal.valueOf(50 * (2 + rnd.nextInt(5)));
                manual = billDiscountValue.min(preBill);
            } else {
                billDiscountType = DiscountType.PERCENT;
                billDiscountValue = BigDecimal.valueOf(5 + rnd.nextInt(6));
                manual = pct(preBill, billDiscountValue);
            }
        }
        BigDecimal grand = preBill.subtract(manual).max(BigDecimal.ZERO);

        // --- package sale on this bill
        Pkg soldPackage = null;
        if (cust.packages.isEmpty() && !isNew && rnd.nextDouble() < 0.013) {
            soldPackage = sellPackage(cust, br, lines.get(0).staff(), d, completed);
        }
        BigDecimal membershipFee = soldMembership != null ? soldMembership.paid : BigDecimal.ZERO;
        BigDecimal packageFee = soldPackage != null
                ? money(DemoBrandCatalog.PACKAGES.get(soldPackage.planIdx).packagePrice()) : BigDecimal.ZERO;
        grand = grand.add(membershipFee).add(packageFee);

        // --- rows
        UUID bookingId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        if (soldPackage != null) {
            soldPackage.invoiceId = invoiceId;
            soldPackage.bookingId = bookingId;
        }
        boolean online = rnd.nextDouble() < 0.18;
        St primaryStaff = lines.get(0).staff();
        w.add("bookings", bookingId, tenantId, br.id, cust.id, managerId, BookingStatus.COMPLETED,
                billDiscountType, billDiscountValue, DiscountScope.BILL,
                billDiscountType != null ? "Manager discretion" : null, couponId, offerId,
                member != null ? member.id : null, membershipDiscount, promoDiscount,
                online ? BookingSource.ONLINE : BookingSource.WALK_IN,
                online ? arrival : null, online ? arrival.plus(Duration.ofMinutes(durationMinutes)) : null,
                online ? primaryStaff.id : null, online ? passToken() : null, started,
                started.plus(Duration.ofMinutes(durationMinutes)),
                (int) Duration.between(started, completed).toMinutes(), arrival, completed, completed);
        Instant lineStart = started;
        for (Line line : lines) {
            Instant lineEnd = lineStart.plus(Duration.ofMinutes(line.svc().def.minutes()));
            w.add("booking_line_items", UUID.randomUUID(), bookingId, br.branchServiceIds.get(line.svc()),
                    line.svc().id, line.staff().id, line.svc().def.name(), line.unitPrice(), 1, GST_RATE,
                    line.svc().def.minutes(), lineStart, lineEnd, line.svc().def.minutes(),
                    line.pkg() != null ? line.pkg().id : null);
            lineStart = lineEnd;
            consume(br, line.svc(), d);
            line.staff().sales.merge(YearMonth.from(d), line.unitPrice(), BigDecimal::add);
        }
        String invoiceNumber = nextInvoiceNumber(br, d);
        String membershipLabel = null;
        if (member != null) {
            MembershipDef def = DemoBrandCatalog.MEMBERSHIPS.get(member.planIdx);
            membershipLabel = rebrand(def.name()) + " (" + def.benefitPercent() + "%" + (soldMembership != null ? " new" : "") + ")";
        }
        w.add("invoices", invoiceId, tenantId, br.id, bookingId, cust.id, invoiceNumber,
                grossTotal, membershipDiscount.add(promoDiscount).add(manual), membershipDiscount, promoDiscount,
                couponId, offerId, member != null ? member.id : null, membershipLabel, membershipFee,
                soldMembership != null ? "Membership · " + rebrand(DemoBrandCatalog.MEMBERSHIPS.get(soldMembership.planIdx).name()) : null,
                packageFee, soldPackage != null ? "Package · " + DemoBrandCatalog.PACKAGES.get(soldPackage.planIdx).name() : null,
                soldPackage != null ? soldPackage.id : null, promoLabel, taxableTotal, cgstTotal, cgstTotal, grand,
                br.def().gstin(), cust.name, cust.phone, cust.society, cust.flat, completed);

        PaymentMode mode = paymentMode();
        UUID paymentId = UUID.randomUUID();
        w.add("payments", paymentId, tenantId, br.id, bookingId, invoiceId, mode, grand,
                mode == PaymentMode.UPI ? "UPI" + (100_000_000 + rnd.nextInt(900_000_000)) : null, managerId, completed);
        if (mode == PaymentMode.SPLIT) {
            BigDecimal cash = grand.multiply(BigDecimal.valueOf(0.3 + rnd.nextDouble() * 0.4)).setScale(0, RoundingMode.HALF_UP)
                    .setScale(2, RoundingMode.HALF_UP).min(grand);
            w.add("payment_splits", UUID.randomUUID(), paymentId, PaymentMode.CASH, cash, null);
            w.add("payment_splits", UUID.randomUUID(), paymentId, PaymentMode.UPI, grand.subtract(cash), null);
        }

        receipt(cust, invoiceId, completed);
        review(br, cust, bookingId, invoiceId, completed, d);

        // --- customer state
        cust.visits++;
        cust.spend = cust.spend.add(grand);
        cust.lastVisit = completed;
        scheduleNextVisit(cust, d);
        bills++;
    }

    private void scheduleNextVisit(Cust c, LocalDate d) {
        double churnBoost = c.br.def().story() == Story.RETENTION_DIP && inWindow(d, 150, 20) ? 2.4 : 1.0;
        double churn = switch (c.segment) {
            case LOYAL -> 0.02;
            case REGULAR -> 0.06;
            case OCCASIONAL -> 0.15;
            case ONE_TIME -> 0.8;
        } * churnBoost;
        if (rnd.nextDouble() < Math.min(0.95, churn)) {
            c.churned = true;
            return;
        }
        double mean = switch (c.segment) {
            case LOYAL -> 24;
            case REGULAR -> 45;
            case OCCASIONAL -> 90;
            case ONE_TIME -> 120;
        };
        int gap = (int) Math.max(7, Math.round(mean + rnd.nextGaussian() * mean * 0.25));
        c.nextDue = d.plusDays(gap);
        c.churned = false;
        c.br.due.add(c);
    }

    private Member sellMembership(Cust cust, Br br, St seller, LocalDate d, Instant at) {
        double roll = rnd.nextDouble();
        int planIdx = roll < 0.5 ? 0 : roll < 0.88 ? 1 : 2;
        MembershipDef def = DemoBrandCatalog.MEMBERSHIPS.get(planIdx);
        Member m = new Member();
        m.id = UUID.randomUUID();
        m.planIdx = planIdx;
        m.startsOn = d;
        m.endsOn = d.plusMonths(def.cadence() == MembershipCadence.MONTHS_6 ? 6 : 12).minusDays(1);
        m.paid = money(def.fee());
        m.mode = rnd.nextDouble() < 0.7 ? PaymentMode.UPI : PaymentMode.CARD;
        m.soldByStaff = seller.id;
        m.soldByUser = br.managerUserId;
        m.createdAt = at;
        m.br = br;
        m.cardNumber = "AUR-M-" + (100_000 + rnd.nextInt(900_000));
        cust.member = m;
        return m;
    }

    private Pkg sellPackage(Cust cust, Br br, St seller, LocalDate d, Instant at) {
        List<Integer> options = cust.female ? List.of(0, 1, 3, 4) : List.of(2, 0);
        int planIdx = options.get(rnd.nextInt(options.size()));
        PackageDef def = DemoBrandCatalog.PACKAGES.get(planIdx);
        Pkg p = new Pkg();
        p.id = UUID.randomUUID();
        p.planIdx = planIdx;
        p.purchasedOn = d;
        p.expiresOn = d.plusDays(def.validityDays());
        p.soldByStaff = seller.id;
        p.soldByUser = br.managerUserId;
        p.br = br;
        p.createdAt = at;
        for (String item : def.items()) {
            Svc svc = packageItemService(item);
            p.remaining.put(svc, new int[]{packageItemQty(item), packageItemQty(item)});
            p.entitlementIds.put(svc, UUID.randomUUID());
        }
        cust.packages.add(p);
        return p;
    }

    private void receipt(Cust cust, UUID invoiceId, Instant at) {
        double roll = rnd.nextDouble();
        MessageDeliveryStatus status = !cust.whatsappOptIn ? MessageDeliveryStatus.SKIPPED
                : roll < 0.97 ? MessageDeliveryStatus.SENT : MessageDeliveryStatus.FAILED;
        String error = status == MessageDeliveryStatus.SKIPPED ? "Customer opted out of WhatsApp"
                : status == MessageDeliveryStatus.FAILED ? "Number is not on WhatsApp" : null;
        w.add("message_delivery_logs", UUID.randomUUID(), tenantId, null, null, cust.id, invoiceId,
                MessageChannel.WHATSAPP, "91" + cust.phone, status,
                status == MessageDeliveryStatus.SENT ? "simulated-" + UUID.randomUUID() : null, error,
                at.plusSeconds(20 + rnd.nextInt(60)));
    }

    private void review(Br br, Cust cust, UUID bookingId, UUID invoiceId, Instant issued, LocalDate d) {
        UUID invitationId = UUID.randomUUID();
        Instant createdAt = issued.plusSeconds(60 + rnd.nextInt(120));
        Instant expiresAt = createdAt.plus(Duration.ofDays(7));
        boolean submitted = rnd.nextDouble() < 0.24;
        Instant submittedAt = submitted ? createdAt.plus(Duration.ofMinutes(30 + rnd.nextInt(36 * 60))) : null;
        if (submittedAt != null && submittedAt.isAfter(now)) {
            submitted = false;
            submittedAt = null;
        }
        ReviewStatusHolder status = submitted ? ReviewStatusHolder.SUBMITTED
                : expiresAt.isBefore(now) ? ReviewStatusHolder.EXPIRED : ReviewStatusHolder.PENDING;
        w.add("review_invitations", invitationId, tenantId, br.id, br.def().name(), bookingId, invoiceId, cust.id,
                cust.name.split(" ")[0], null, status.name(), expiresAt, submittedAt, createdAt);
        if (!submitted) {
            return;
        }
        double quality = switch (br.def().story()) {
            case FLAGSHIP -> 4.55;
            case GROWTH -> 4.5;
            case RETENTION_DIP -> inWindow(d, 110, 35) ? 3.45 : 4.35;
            case COST_SPIKE -> 4.4;
            case MEMBERSHIP_HEAVY -> 4.65;
        };
        int rating = clamp((int) Math.round(quality + rnd.nextGaussian() * 0.75), 1, 5);
        String tags = null;
        String comment;
        if (rating <= 3) {
            List<String> pool = br.def().story() == Story.RETENTION_DIP
                    ? List.of("WAIT_TIME", "STAFF_ATTITUDE", "WAIT_TIME", "SERVICE_QUALITY")
                    : List.of("WAIT_TIME", "SERVICE_QUALITY", "CLEANLINESS", "VALUE_FOR_MONEY", "STAFF_ATTITUDE");
            String first = pick(pool);
            String second = pick(pool);
            tags = first.equals(second) ? first : first + "," + second;
            comment = pick(DemoBrandCatalog.LOW_COMMENTS);
        } else if (rating == 4) {
            comment = pick(DemoBrandCatalog.MID_COMMENTS);
        } else {
            comment = pick(DemoBrandCatalog.FIVE_STAR_COMMENTS);
        }
        UUID reviewId = UUID.randomUUID();
        w.add("reviews", reviewId, invitationId, tenantId, br.id, bookingId, rating, sub(rating), sub(rating),
                sub(rating), sub(rating), sub(rating), tags, comment.isBlank() ? null : comment,
                rating >= 4 && rnd.nextDouble() < 0.5, submittedAt);
        if (rating <= 3) {
            boolean old = submittedAt.isBefore(now.minus(Duration.ofDays(10)));
            double roll = rnd.nextDouble();
            RecoveryStatusHolder rs = old
                    ? (roll < 0.75 ? RecoveryStatusHolder.RESOLVED : roll < 0.9 ? RecoveryStatusHolder.ACKNOWLEDGED : RecoveryStatusHolder.OPEN)
                    : (roll < 0.6 ? RecoveryStatusHolder.OPEN : RecoveryStatusHolder.ACKNOWLEDGED);
            Instant resolvedAt = rs == RecoveryStatusHolder.RESOLVED
                    ? submittedAt.plus(Duration.ofHours(6 + rnd.nextInt(72))) : null;
            String notes = rs == RecoveryStatusHolder.RESOLVED
                    ? pick(List.of("Called the guest and apologised; offered a complimentary hair spa.",
                    "Manager spoke to the guest, re-did the service free of charge.",
                    "Shared feedback with the stylist; guest rebooked for next week."))
                    : rs == RecoveryStatusHolder.ACKNOWLEDGED ? "Manager has reached out to the guest." : null;
            w.add("review_recoveries", UUID.randomUUID(), reviewId, tenantId, br.id, bookingId, rating, rs.name(),
                    notes, submittedAt, resolvedAt != null ? resolvedAt : submittedAt, resolvedAt);
        }
    }

    private enum ReviewStatusHolder { PENDING, SUBMITTED, EXPIRED }

    private enum RecoveryStatusHolder { OPEN, ACKNOWLEDGED, RESOLVED }

    // ---- campaigns ----------------------------------------------------------------------------

    private void runCampaign(CampaignDef def, LocalDate d) {
        Br onlyBranch = def.branchCode() != null ? branch(def.branchCode()) : null;
        LocalDate from = null;
        LocalDate to = null;
        CampaignMembershipFilter membershipFilter = null;
        Integer expiringWithin = null;
        double response;
        List<Cust> recipients = new ArrayList<>();
        switch (def.kind()) {
            case FESTIVE -> {
                from = d.minusDays(120);
                response = 0.07;
                for (Cust c : allCustomers) {
                    if (c.whatsappOptIn && c.lastVisit != null && !c.lastVisit.isBefore(at(from))) recipients.add(c);
                }
            }
            case WINBACK, BRANCH_WINBACK -> {
                from = d.minusDays(150);
                to = d.minusDays(60);
                response = def.kind() == CampaignKind.BRANCH_WINBACK ? 0.19 : 0.15;
                for (Cust c : allCustomers) {
                    if (!c.whatsappOptIn || c.lastVisit == null) continue;
                    if (onlyBranch != null && c.br != onlyBranch) continue;
                    if (!c.lastVisit.isBefore(at(from)) && c.lastVisit.isBefore(at(to.plusDays(1)))) recipients.add(c);
                }
            }
            default -> {
                membershipFilter = CampaignMembershipFilter.EXPIRING_SOON;
                expiringWithin = 45;
                response = 0.3;
                for (Cust c : allCustomers) {
                    if (c.whatsappOptIn && c.member != null && !c.member.endsOn.isAfter(d.plusDays(45))) recipients.add(c);
                }
            }
        }
        UUID campaignId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant createdAt = d.atTime(10, 15).atZone(ZONE).toInstant();
        Instant sentAt = d.atTime(11, 0).atZone(ZONE).toInstant();
        int failed = 0;
        for (Cust c : recipients) {
            boolean fail = rnd.nextDouble() < 0.035;
            if (fail) failed++;
            w.add("message_delivery_logs", UUID.randomUUID(), tenantId, campaignId, runId, c.id, null,
                    MessageChannel.WHATSAPP, "91" + c.phone, fail ? MessageDeliveryStatus.FAILED : MessageDeliveryStatus.SENT,
                    fail ? null : "simulated-" + UUID.randomUUID(), fail ? "Number is not on WhatsApp" : null,
                    sentAt.plusSeconds(rnd.nextInt(900)));
            if (fail || rnd.nextDouble() >= response) continue;
            LocalDate visit = d.plusDays(1 + rnd.nextInt(12));
            switch (def.kind()) {
                case WINBACK, BRANCH_WINBACK -> c.comeback = true;
                case MEMBERSHIP_RENEWAL -> c.renewMembership = true;
                default -> { }
            }
            if (c.churned || c.nextDue == null || c.nextDue.isAfter(visit)) {
                c.br.due.remove(c);
                c.churned = false;
                c.nextDue = visit;
                c.br.due.add(c);
            }
        }
        String name = rebrand(def.name());
        w.add("marketing_campaigns", campaignId, tenantId, name, MessageChannel.WHATSAPP, CampaignStatus.COMPLETED,
                rebrand(def.message()), from, to, true, false, onlyBranch != null ? onlyBranch.id : null,
                membershipFilter, expiringWithin, recipients.size(), recipients.size() - failed, failed, ownerUserId,
                sentAt, createdAt, sentAt.plusSeconds(900));
        w.add("campaign_runs", runId, tenantId, campaignId, CampaignRunStatus.COMPLETED, recipients.size(),
                recipients.size() - failed, failed, sentAt, sentAt.plusSeconds(900));
    }

    // ---- attendance & leave -------------------------------------------------------------------

    private void planLeaves(St st) {
        for (LocalDate m = start.withDayOfMonth(1); !m.isAfter(today); m = m.plusMonths(1)) {
            int events = rnd.nextDouble() < 0.55 ? 1 : rnd.nextDouble() < 0.5 ? 0 : 2;
            for (int i = 0; i < events; i++) {
                LocalDate leaveStart = m.plusDays(rnd.nextInt(m.lengthOfMonth()));
                if (leaveStart.isBefore(start) || !leaveStart.isBefore(today) || leaveStart.isBefore(st.joined)) continue;
                boolean halfDay = rnd.nextDouble() < 0.25;
                int days = halfDay ? 1 : 1 + (rnd.nextDouble() < 0.3 ? 1 : 0);
                LocalDate leaveEnd = leaveStart.plusDays(days - 1);
                boolean rejected = rnd.nextDouble() < 0.04;
                if (!halfDay && !rejected) {
                    for (LocalDate x = leaveStart; !x.isAfter(leaveEnd); x = x.plusDays(1)) st.leaveDays.add(x);
                }
                Instant requested = leaveStart.minusDays(2 + rnd.nextInt(6)).atTime(19, 0).atZone(ZONE).toInstant();
                w.add("leave_records", UUID.randomUUID(), tenantId, st.br.id, st.id, leaveStart, leaveEnd,
                        halfDay ? LeaveType.HALF_DAY : LeaveType.FULL_DAY,
                        rejected ? LeaveStatus.REJECTED : LeaveStatus.APPROVED,
                        pick(List.of("Family function", "Not feeling well", "Personal work", "Hometown visit", "Medical appointment")),
                        st.br.managerUserId, st.br.managerUserId, requested, requested.plus(Duration.ofHours(3)));
            }
        }
    }

    private void pendingLeaves() {
        for (int i = 0; i < 6; i++) {
            St st = pick(allStaff);
            LocalDate leaveStart = today.plusDays(2 + rnd.nextInt(10));
            Instant requested = now.minus(Duration.ofHours(2 + rnd.nextInt(40)));
            w.add("leave_records", UUID.randomUUID(), tenantId, st.br.id, st.id, leaveStart, leaveStart,
                    LeaveType.FULL_DAY, LeaveStatus.PENDING, pick(List.of("Sister's wedding", "Exam", "Personal work")),
                    st.br.managerUserId, null, requested, requested);
        }
    }

    private void attendance(St st, LocalDate d) {
        if (d.isBefore(st.joined) || d.getDayOfWeek() == st.def.weeklyOff() || st.leaveDays.contains(d)) {
            return;
        }
        boolean lateStory = st.def.lateProne() && st.br.def().story() == Story.RETENTION_DIP && inWindow(d, 60, 0);
        double lateMean = st.def.lateProne() ? (lateStory ? 22 : 12) : 0;
        int entryOffset = (int) Math.round(-10 + rnd.nextGaussian() * 8 + (rnd.nextDouble() < 0.35 ? lateMean : 0));
        Instant entry = d.atTime(10, 0).atZone(ZONE).toInstant().plus(Duration.ofMinutes(entryOffset));
        Instant exit = d.atTime(21, 0).atZone(ZONE).toInstant().plus(Duration.ofMinutes(Math.round(8 + rnd.nextGaussian() * 12)));
        if (entry.isAfter(now)) {
            return;
        }
        if (exit.isAfter(now)) {
            exit = null;
        }
        boolean verified = rnd.nextDouble() < 0.88;
        boolean outside = verified && rnd.nextDouble() < 0.025;
        double lat = st.br.def().lat() + (outside ? 0.004 : (rnd.nextDouble() - 0.5) * 0.0006);
        double lng = st.br.def().lng() + (outside ? 0.003 : (rnd.nextDouble() - 0.5) * 0.0006);
        UUID recordId = UUID.randomUUID();
        AttendanceMethod method = verified ? AttendanceMethod.VERIFIED : AttendanceMethod.MANUAL;
        w.add("attendance_records", recordId, tenantId, st.br.id, st.id, d, entry, exit, method,
                exit != null ? method : null,
                verified ? null : pick(List.of("Phone battery dead", "App not opening on staff phone", "GPS not available")),
                verified ? null : st.br.managerUserId,
                verified ? lat : null, verified ? lng : null, verified ? 6 + rnd.nextDouble() * 18 : null,
                exit != null && verified ? st.br.def().lat() : null, exit != null && verified ? st.br.def().lng() : null,
                exit != null && verified ? 6 + rnd.nextDouble() * 18 : null,
                verified ? (outside ? GeoStatus.OUT_OF_GEOFENCE : GeoStatus.IN_GEOFENCE) : null,
                exit != null && verified ? GeoStatus.IN_GEOFENCE : null,
                verified ? st.photoKey : null, exit != null && verified ? st.photoKey : null,
                verified, exit != null && verified, entry, exit != null ? exit : entry);
        long lateBy = Duration.between(d.atTime(10, 15).atZone(ZONE).toInstant(), entry).toMinutes();
        if (lateBy > 0) {
            st.lateCount.merge(YearMonth.from(d), 1, Integer::sum);
            if (lateBy > 15 && rnd.nextDouble() < 0.35) {
                w.add("attendance_incidents", UUID.randomUUID(), tenantId, st.id, st.br.id, recordId, d,
                        IncidentType.NOTE, "Late by " + (lateBy + 15) + " min — reminded about shift start",
                        null, st.br.managerUserId, entry.plus(Duration.ofMinutes(30)));
            }
        }
    }

    private void monthlyPenalties(YearMonth month) {
        for (St st : allStaff) {
            int late = st.lateCount.getOrDefault(month, 0);
            if (late >= 4) {
                LocalDate day = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();
                w.add("attendance_incidents", UUID.randomUUID(), tenantId, st.id, st.br.id, null, day,
                        IncidentType.PENALTY, "Late arrival " + late + " times in " + month.getMonth()
                                .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH),
                        money(50L * late), st.br.managerUserId, day.atTime(20, 0).atZone(ZONE).toInstant());
            }
        }
    }

    // ---- inventory & expenditure --------------------------------------------------------------

    private void seedOpeningStock() {
        for (Br br : branches) {
            for (Prod p : products) {
                double qty = openingQty(p);
                br.balance[p.idx] = qty;
                movement(br, p, MovementType.RESTOCK, qty, start, "Opening stock");
            }
        }
    }

    private double openingQty(Prod p) {
        int reorder = p.def.reorderLevel();
        return p.def.unit() == InventoryUnit.PCS || p.def.unit() == InventoryUnit.BOTTLE
                ? Math.max(4, reorder * 3) : reorder * 3.0;
    }

    private void consume(Br br, Svc svc, LocalDate d) {
        for (Map.Entry<String, Double> e : svc.usage.entrySet()) {
            Prod p = productBySku.get(e.getKey());
            if (p != null) {
                br.weekUsage[p.idx] += e.getValue();
                addCost(br, d, e.getValue() * p.def.unitCost());
            }
        }
    }

    private void closeInventoryWeek(Br br, LocalDate d) {
        boolean costSpike = br.def().story() == Story.COST_SPIKE && inWindow(d, 32, 0);
        for (Prod p : products) {
            double usage = br.weekUsage[p.idx];
            br.weekUsage[p.idx] = 0;
            boolean discrete = p.def.unit() == InventoryUnit.PCS || p.def.unit() == InventoryUnit.BOTTLE;
            if (p.def.category() == ProductCategory.RETAIL) {
                int sold = (int) Math.round((2 + rnd.nextInt(6)) * br.def().volumeFactor());
                sold = (int) Math.min(sold, Math.floor(br.balance[p.idx]));
                if (sold > 0) {
                    movement(br, p, MovementType.RETAIL_SALE, sold, d, "Counter sale");
                    br.balance[p.idx] -= sold;
                }
            } else if (usage > 0) {
                double qty = discrete ? Math.ceil(usage - 0.15) : Math.round(usage);
                if (qty > 0) {
                    if (qty > br.balance[p.idx]) restock(br, p, d, qty);
                    movement(br, p, MovementType.USAGE, qty, d, "Weekly service usage");
                    br.balance[p.idx] -= qty;
                }
                if (WASTAGE_PRONE.contains(p.def.sku())) {
                    double rate = costSpike && COST_SPIKE_SKUS.contains(p.def.sku()) ? 0.5 : 0.02;
                    double waste = usage * rate;
                    waste = discrete ? (rnd.nextDouble() < waste ? 1 : Math.floor(waste)) : Math.round(waste);
                    if (waste > br.balance[p.idx]) restock(br, p, d, waste);
                    if (waste > 0) {
                        movement(br, p, MovementType.WASTAGE, waste, d,
                                costSpike && COST_SPIKE_SKUS.contains(p.def.sku())
                                        ? "Over-mixed / expired batch" : "Spillage");
                        br.balance[p.idx] -= waste;
                        addCost(br, d, waste * p.def.unitCost());
                    }
                }
            }
            boolean holdRestock = ChronoUnit.DAYS.between(d, today) < 21
                    && ((p.def.sku().equals("KERK") && (br.def().story() == Story.COST_SPIKE || br.def().story() == Story.FLAGSHIP))
                    || (p.def.sku().equals("RSRM") && br.def().story() == Story.GROWTH));
            if (!holdRestock && br.balance[p.idx] < p.def.reorderLevel() * 1.3) {
                restock(br, p, d.plusDays(1).isAfter(today) ? d : d.plusDays(1), 0);
            }
        }
        if (d.getDayOfMonth() <= 7 && d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            Prod p = pick(products.stream().filter(x -> x.def.category() == ProductCategory.CONSUMABLE).toList());
            double adj = p.def.unit() == InventoryUnit.PCS ? -1 : -Math.round(p.def.reorderLevel() * 0.03);
            if (br.balance[p.idx] + adj > 0) {
                movement(br, p, MovementType.ADJUSTMENT, adj, d, "Monthly stock count adjustment");
                br.balance[p.idx] += adj;
            }
        }
    }

    private void restock(Br br, Prod p, LocalDate d, double minimumNeeded) {
        double target = Math.max(openingQty(p), minimumNeeded + p.def.reorderLevel());
        double qty = target - br.balance[p.idx];
        if (p.def.unit() == InventoryUnit.PCS || p.def.unit() == InventoryUnit.BOTTLE) qty = Math.ceil(qty);
        else qty = Math.ceil(qty / 500.0) * 500;
        if (qty <= 0) return;
        movement(br, p, MovementType.RESTOCK, qty, d, "Purchase from " + DemoBrandCatalog.VENDORS.get(p.def.vendor()));
        br.balance[p.idx] += qty;
    }

    private void movement(Br br, Prod p, MovementType type, double qty, LocalDate d, String note) {
        BigDecimal quantity = BigDecimal.valueOf(qty).setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitCost = BigDecimal.valueOf(p.def.unitCost()).setScale(2, RoundingMode.HALF_UP);
        Instant at = d.atTime(type == MovementType.RESTOCK ? 11 : 21, rnd.nextInt(50)).atZone(ZONE).toInstant();
        if (at.isAfter(now)) at = now.minusSeconds(60);
        w.add("inventory_movements", UUID.randomUUID(), tenantId, br.id, p.id, type, quantity, unitCost,
                quantity.abs().multiply(unitCost).setScale(2, RoundingMode.HALF_UP), d, note, br.managerUserId, at);
    }

    private void addCost(Br br, LocalDate d, double cost) {
        br.productCost.merge(YearMonth.from(d), cost, Double::sum);
    }

    private void monthlyExpenditure(Br br, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        Instant adminAt = monthStart.plusDays(1).atTime(11, 0).atZone(ZONE).toInstant();
        if (adminAt.isAfter(now)) adminAt = now.minusSeconds(3600);
        long stylistPay = br.staff.stream().filter(s -> !s.joined.isAfter(month.atEndOfMonth()))
                .mapToLong(s -> s.def.salary()).sum();
        long incentives = br.staff.stream().mapToLong(s -> s.sales.getOrDefault(month, BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(s.def.incentivePercent())).divide(HUNDRED, 0, RoundingMode.HALF_UP).longValue()).sum();
        long staffCount = br.staff.stream().filter(s -> !s.joined.isAfter(month.atEndOfMonth())).count();
        // Front desk, helpers and housekeeping are on payroll but don't take services.
        int support = br.def().story() == Story.FLAGSHIP ? 5 : 4;
        long payroll = stylistPay + incentives + support * 18_000L;
        expense(br, ExpenditureCategory.RENT, monthStart, br.def().rent(), "Shop rent", false, adminAt);
        expense(br, ExpenditureCategory.EMPLOYEE_SALARY, monthStart, payroll,
                "Payroll — " + staffCount + " stylists + " + support + " support staff (incl. ₹" + incentives + " incentives)",
                false, adminAt);
        expense(br, ExpenditureCategory.EMPLOYEE_ACCOMMODATION_RENT, monthStart, br.def().accommodation(),
                "Staff accommodation", false, adminAt);
        expense(br, ExpenditureCategory.MISCELLANEOUS, monthStart,
                Math.round(br.def().utilities() * (0.9 + rnd.nextDouble() * 0.2)), "Electricity, water & internet", false, adminAt);
        expense(br, ExpenditureCategory.MISCELLANEOUS, monthStart, 32_000 + rnd.nextInt(12_000),
                "Housekeeping & security contract", false, adminAt);
        expense(br, ExpenditureCategory.MISCELLANEOUS, monthStart, 22_000 + rnd.nextInt(15_000),
                "Marketing, WhatsApp credits & software", false, adminAt);
        expense(br, ExpenditureCategory.MISCELLANEOUS, monthStart, 18_000 + rnd.nextInt(8_000),
                "Card & UPI payment charges", false, adminAt);
        double productCost = br.productCost.getOrDefault(month, 0.0);
        if (productCost > 0) {
            expense(br, ExpenditureCategory.PRODUCT_COST, monthStart, Math.round(productCost),
                    "Product consumption (usage + wastage)", false, adminAt);
        }
        int lines = 6 + rnd.nextInt(4);
        for (int i = 0; i < lines; i++) {
            LocalDate day = monthStart.plusDays(rnd.nextInt(month.lengthOfMonth()));
            if (day.isAfter(today)) continue;
            String[] item = pick(List.of(
                    new String[]{"Tea & snacks for staff", "600", "1800"},
                    new String[]{"Laundry — towels & capes", "1200", "3500"},
                    new String[]{"Housekeeping supplies", "800", "2500"},
                    new String[]{"Plumber — wash basin repair", "500", "2500"},
                    new String[]{"Courier & printing", "200", "900"},
                    new String[]{"Drinking water cans", "400", "900"},
                    new String[]{"AC servicing", "1500", "4000"}));
            long amount = Long.parseLong(item[1]) + rnd.nextInt(Integer.parseInt(item[2]) - Integer.parseInt(item[1]));
            expense(br, ExpenditureCategory.MISCELLANEOUS, monthStart, amount, item[0], true,
                    day.atTime(18, rnd.nextInt(59)).atZone(ZONE).toInstant());
        }
    }

    private void expense(Br br, ExpenditureCategory category, LocalDate month, long amount, String description,
                         boolean managerRecorded, Instant at) {
        if (at.isAfter(now)) at = now.minusSeconds(60);
        w.add("branch_expenditures", UUID.randomUUID(), tenantId, br.id, category, month, money(amount), description,
                true, managerRecorded, at, at);
    }

    // ---- leads, appointments, live floor ------------------------------------------------------

    private void enquiries(LocalDate d) {
        if (rnd.nextDouble() > 0.45) return;
        Br br = pick(branches);
        boolean female = rnd.nextDouble() < 0.7;
        String name = pick(female ? DemoBrandCatalog.FEMALE_NAMES : DemoBrandCatalog.MALE_NAMES) + " " + pick(DemoBrandCatalog.SURNAMES);
        Instant at = d.atTime(9 + rnd.nextInt(12), rnd.nextInt(60)).atZone(ZONE).toInstant();
        if (at.isAfter(now)) return;
        String email = name.toLowerCase().replace(' ', '.').replaceAll("[^a-z.]", "") + rnd.nextInt(100) + "@example.com";
        w.add("marketing_enquiries", UUID.randomUUID(), tenantId, name, newPhone(), email, br.def().locality(),
                pick(List.of("Interested in bridal package for December", "Do you have a membership plan?",
                        "Want to book keratin this weekend", "Corporate group booking for 8 people",
                        "Price for full body wax?", "Is there parking available?")), at);
    }

    private void upcomingAppointments() {
        for (Br br : branches) {
            for (int dayAhead = 0; dayAhead <= 3; dayAhead++) {
                LocalDate d = today.plusDays(dayAhead);
                int count = 6 + rnd.nextInt(5);
                for (int i = 0; i < count; i++) {
                    Instant startAt = arrival(d);
                    if (!startAt.isAfter(now.plus(Duration.ofMinutes(30)))) continue;
                    Cust cust = br.customers.isEmpty() ? newCustomer(br, now) : pick(br.customers);
                    Svc svc = pickService(cust, d, null);
                    St st = staffFor(br, svc, d);
                    UUID bookingId = UUID.randomUUID();
                    boolean online = rnd.nextDouble() < 0.6;
                    Instant created = now.minus(Duration.ofHours(2 + rnd.nextInt(60)));
                    w.add("bookings", bookingId, tenantId, br.id, cust.id, br.managerUserId, BookingStatus.CONFIRMED,
                            null, null, DiscountScope.BILL, null, null, null, null, BigDecimal.ZERO.setScale(2),
                            BigDecimal.ZERO.setScale(2), online ? BookingSource.ONLINE : BookingSource.WALK_IN, startAt,
                            startAt.plus(Duration.ofMinutes(svc.def.minutes())), st.id, online ? passToken() : null,
                            null, null, null, created, created, null);
                    w.add("booking_line_items", UUID.randomUUID(), bookingId, br.branchServiceIds.get(svc), svc.id, st.id,
                            svc.def.name(), br.prices.get(svc), 1, GST_RATE, svc.def.minutes(), null, null, null, null);
                }
            }
        }
    }

    private void inProgressVisits() {
        LocalTime t = now.atZone(ZONE).toLocalTime();
        if (t.isBefore(LocalTime.of(10, 30)) || t.isAfter(LocalTime.of(20, 30))) return;
        for (Br br : branches) {
            for (int i = 0; i < 2; i++) {
                Cust cust = br.customers.isEmpty() ? newCustomer(br, now) : pick(br.customers);
                Svc svc = pickService(cust, today, null);
                St st = staffFor(br, svc, today);
                UUID bookingId = UUID.randomUUID();
                Instant arrived = now.minus(Duration.ofMinutes(15 + rnd.nextInt(25)));
                Instant startedAt = arrived.plus(Duration.ofMinutes(5));
                w.add("bookings", bookingId, tenantId, br.id, cust.id, br.managerUserId, BookingStatus.IN_PROGRESS,
                        null, null, DiscountScope.BILL, null, null, null, null, BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2), BookingSource.WALK_IN, null, null, null, null, startedAt,
                        startedAt.plus(Duration.ofMinutes(svc.def.minutes())), null, arrived, arrived, null);
                w.add("booking_line_items", UUID.randomUUID(), bookingId, br.branchServiceIds.get(svc), svc.id, st.id,
                        svc.def.name(), br.prices.get(svc), 1, GST_RATE, svc.def.minutes(), startedAt, null, null, null);
            }
        }
    }

    // ---- final state rows ---------------------------------------------------------------------

    private void writeFinalState() {
        for (Cust c : allCustomers) {
            Instant updated = c.lastVisit != null ? c.lastVisit : c.createdAt;
            w.add("customers", c.id, tenantId, c.br.id, c.name, c.phone, c.passId, CustomerIdentityStatus.PHONE_VERIFIED,
                    c.passToken, c.society, c.flat, null, c.email, c.whatsappOptIn, c.smsOptIn, c.visits, c.spend,
                    c.lastVisit, c.createdAt, updated);
            List<Member> members = new ArrayList<>(c.pastMembers);
            if (c.member != null) members.add(c.member);
            for (Member m : members) {
                MembershipStatus status = m.endsOn.isBefore(today) ? MembershipStatus.EXPIRED : MembershipStatus.ACTIVE;
                w.add("membership_subscriptions", m.id, tenantId, c.id, membershipPlanIds.get(m.planIdx), m.br.id,
                        m.cardNumber, m.startsOn, m.endsOn, status, m.paid, m.mode, null, m.soldByUser, m.soldByStaff,
                        m.createdAt, m.createdAt);
            }
        }
        for (Cust c : allCustomers) {
            for (Pkg p : c.packages) writePackage(c, p);
        }
        for (Map.Entry<Cust, List<Pkg>> e : closedPackages.entrySet()) {
            for (Pkg p : e.getValue()) writePackage(e.getKey(), p);
        }

        Instant promoCreated = start.minusDays(10).atStartOfDay(ZONE).toInstant();
        Instant farFuture = today.plusYears(1).atStartOfDay(ZONE).toInstant();
        w.add("coupons", couponWelcomeId, tenantId, "First visit welcome", "WELCOME10", "10% off a guest's first visit",
                DiscountType.PERCENT, BigDecimal.TEN.setScale(2), promoCreated, farFuture, ServiceScopeType.ALL, null, null,
                PromoStatus.ACTIVE, null, welcomeRedemptions, ownerUserId, promoCreated, now);
        w.add("coupons", couponComebackId, tenantId, "We miss you", "COMEBACK15", "15% off for returning guests",
                DiscountType.PERCENT, BigDecimal.valueOf(15).setScale(2), promoCreated, farFuture, ServiceScopeType.ALL,
                null, null, PromoStatus.ACTIVE, null, comebackRedemptions, ownerUserId, promoCreated, now);
        Instant festiveStart = start.plusDays(15).atStartOfDay(ZONE).toInstant();
        Instant monsoonStart = start.plusDays(268).atStartOfDay(ZONE).toInstant();
        UUID skinCategory = serviceByName.get("Fruit Facial").categoryId;
        UUID treatmentCategory = serviceByName.get("Keratin Treatment").categoryId;
        w.add("offers", offerFestiveId, tenantId, "Festive Facial Fiesta", "15% off all facials during the festive weeks",
                DiscountType.PERCENT, BigDecimal.valueOf(15).setScale(2), festiveStart, festiveStart.plus(Duration.ofDays(31)),
                ServiceScopeType.CATEGORY, skinCategory.toString(), null, PromoStatus.EXPIRED, null, festiveRedemptions,
                ownerUserId, festiveStart.minus(Duration.ofDays(3)), festiveStart.plus(Duration.ofDays(31)));
        w.add("offers", offerMonsoonId, tenantId, "Monsoon Hair Rescue", "20% off keratin, smoothening and hair spas",
                DiscountType.PERCENT, BigDecimal.valueOf(20).setScale(2), monsoonStart, monsoonStart.plus(Duration.ofDays(38)),
                ServiceScopeType.CATEGORY, treatmentCategory.toString(), null, PromoStatus.EXPIRED, null, monsoonRedemptions,
                ownerUserId, monsoonStart.minus(Duration.ofDays(3)), monsoonStart.plus(Duration.ofDays(38)));
        w.add("offers", offerWeekdayId, tenantId, "Weekday Happy Hours", "10% off on Tuesdays and Wednesdays",
                DiscountType.PERCENT, BigDecimal.TEN.setScale(2), promoCreated, farFuture, ServiceScopeType.ALL, null, null,
                PromoStatus.ACTIVE, null, weekdayRedemptions, ownerUserId, promoCreated, now);

        for (Br br : branches) {
            for (Map.Entry<String, Long> e : br.invoiceSeq.entrySet()) {
                w.add("invoice_sequences", UUID.randomUUID(), br.id, e.getKey(), e.getValue());
            }
            for (Prod p : products) {
                w.add("branch_inventory", UUID.randomUUID(), tenantId, br.id, p.id,
                        BigDecimal.valueOf(Math.max(0, br.balance[p.idx])).setScale(2, RoundingMode.HALF_UP), now);
            }
        }
    }

    private final Map<Cust, List<Pkg>> closedPackages = new HashMap<>();

    private void writePackage(Cust c, Pkg p) {
        PackageDef def = DemoBrandCatalog.PACKAGES.get(p.planIdx);
        boolean usedUp = p.remaining.values().stream().allMatch(r -> r[0] <= 0);
        PackageSubscriptionStatus status = usedUp ? PackageSubscriptionStatus.COMPLETED
                : p.expiresOn.isBefore(today) ? PackageSubscriptionStatus.EXPIRED : PackageSubscriptionStatus.ACTIVE;
        w.add("customer_package_subscriptions", p.id, tenantId, c.id, p.br.id, packagePlanIds.get(p.planIdx), def.name(),
                PackagePlanType.SERVICE_BUNDLE, null, null, PackageRedemptionMode.MULTI_VISIT,
                money(def.packagePrice()), p.invoiceId, p.bookingId, p.purchasedOn, p.expiresOn, status, p.soldByUser,
                p.soldByStaff, p.createdAt);
        for (Map.Entry<Svc, int[]> e : p.remaining.entrySet()) {
            w.add("customer_package_entitlements", p.entitlementIds.get(e.getKey()), p.id, e.getKey().id,
                    e.getKey().def.name(), e.getValue()[1], Math.max(0, e.getValue()[0]));
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private Svc pickService(Cust c, LocalDate d, Svc exclude) {
        double total = 0;
        double[] weights = new double[services.size()];
        int month = d.getMonthValue();
        for (int i = 0; i < services.size(); i++) {
            Svc s = services.get(i);
            if (s == exclude) continue;
            String g = s.def.gender();
            if (g.equals("F") && !c.female || g.equals("M") && c.female) continue;
            double wgt = s.def.weight();
            String cat = s.def.category();
            if (cat.equals("Bridal & Makeup") && (month == 11 || month == 12 || month == 1 || month == 2 || month == 5)) wgt *= 3;
            if (cat.equals("Hair Treatments") && (month == 7 || month == 8)) wgt *= 1.25;
            if (cat.equals("Spa & Massage") && month == 2) wgt *= 1.4;
            if (c.segment == Segment.LOYAL) wgt *= Math.pow(s.def.price() / 900.0, 0.15);
            // Indiranagar's recent growth comes partly from premium colour, treatment and spa work.
            if (c.br.def().story() == Story.GROWTH && inWindow(d, 30, 0)) wgt *= Math.pow(s.def.price() / 900.0, 0.08);
            if (c.br.def().story() == Story.COST_SPIKE && (cat.equals("Hair Colour") || cat.equals("Hair Treatments"))) wgt *= 1.25;
            if (c.br.def().businessType() == BranchBusinessType.SALON && cat.equals("Spa & Massage")) wgt *= 0.25;
            weights[i] = wgt;
            total += wgt;
        }
        double roll = rnd.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0 && weights[i] > 0) return services.get(i);
        }
        return services.get(0);
    }

    private St staffFor(Br br, Svc svc, LocalDate d) {
        String skill = svc.def.skill();
        List<St> pool = new ArrayList<>();
        for (St st : br.staff) {
            if (st.skills.contains(skill) && !d.isBefore(st.joined) && d.getDayOfWeek() != st.def.weeklyOff()
                    && !st.leaveDays.contains(d)) pool.add(st);
        }
        if (pool.isEmpty()) {
            for (St st : br.staff) if (st.skills.contains(skill)) pool.add(st);
        }
        if (pool.isEmpty()) pool.addAll(br.staff);
        double total = pool.stream().mapToDouble(s -> s.def.demandWeight()).sum();
        double roll = rnd.nextDouble() * total;
        for (St st : pool) {
            roll -= st.def.demandWeight();
            if (roll <= 0) return st;
        }
        return pool.get(pool.size() - 1);
    }

    private String nextInvoiceNumber(Br br, LocalDate d) {
        String fy = fiscalYear(d);
        long seq = br.invoiceSeq.merge(fy, 1L, Long::sum);
        return br.def().code() + "-" + fy + "-" + String.format("%05d", seq);
    }

    static String fiscalYear(LocalDate date) {
        int year = date.getYear();
        return date.getMonthValue() >= 4
                ? year + "-" + String.valueOf(year + 1).substring(2)
                : (year - 1) + "-" + String.valueOf(year).substring(2);
    }

    private Instant arrival(LocalDate d) {
        double total = Arrays.stream(HOUR_WEIGHTS).sum();
        double roll = rnd.nextDouble() * total;
        int hour = 10;
        for (int i = 0; i < HOUR_WEIGHTS.length; i++) {
            roll -= HOUR_WEIGHTS[i];
            if (roll <= 0) {
                hour = 10 + i;
                break;
            }
        }
        return d.atTime(hour, rnd.nextInt(60)).atZone(ZONE).toInstant();
    }

    private PaymentMode paymentMode() {
        double roll = rnd.nextDouble();
        return roll < 0.58 ? PaymentMode.UPI : roll < 0.8 ? PaymentMode.CARD : roll < 0.96 ? PaymentMode.CASH : PaymentMode.SPLIT;
    }

    private static double monthFactor(LocalDate d) {
        return switch (d.getMonthValue()) {
            case 10 -> 1.12;
            case 11 -> 1.18;
            case 12 -> 1.12;
            case 1 -> 0.9;
            case 2 -> 0.95;
            case 5 -> 1.06;
            case 6 -> 0.96;
            case 7 -> 0.86;
            case 8 -> 0.9;
            default -> 1.0;
        };
    }

    private static double weekdayFactor(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case MONDAY -> 0.72;
            case TUESDAY -> 0.82;
            case WEDNESDAY -> 0.88;
            case THURSDAY -> 0.95;
            case FRIDAY -> 1.08;
            case SATURDAY -> 1.38;
            case SUNDAY -> 1.3;
        };
    }

    private double storyVolume(Br br, LocalDate d) {
        return switch (br.def().story()) {
            case GROWTH -> inWindow(d, 30, 0) ? 1.04 : 1.0;
            case RETENTION_DIP -> inWindow(d, 45, 0) ? 0.84 : inWindow(d, 120, 0) ? 0.93 : 1.0;
            default -> 1.0;
        };
    }

    /** True when d is between {@code fromDaysAgo} and {@code toDaysAgo} days before today. */
    private boolean inWindow(LocalDate d, int fromDaysAgo, int toDaysAgo) {
        long ago = ChronoUnit.DAYS.between(d, today);
        return ago <= fromDaysAgo && ago >= toDaysAgo;
    }

    private String newPhone() {
        while (true) {
            int lead = 6 + rnd.nextInt(4);
            String phone = lead + String.format("%09d", rnd.nextInt(1_000_000_000));
            if (!reservedPhones.contains(phone) && usedPhones.add(phone)) return phone;
        }
    }

    private String passToken() {
        while (true) {
            String token = Long.toHexString(rnd.nextLong()) + Long.toHexString(rnd.nextLong());
            if (usedPassTokens.add(token)) return token;
        }
    }

    private Br branch(String code) {
        return branches.stream().filter(b -> b.def().code().equals(code)).findFirst().orElseThrow();
    }

    private Svc packageItemService(String item) {
        return serviceByName.get(item.substring(0, item.lastIndexOf(" x")));
    }

    private static int packageItemQty(String item) {
        return Integer.parseInt(item.substring(item.lastIndexOf(" x") + 2));
    }

    private String rebrand(String text) {
        return text.replace("Aura", brandShort);
    }

    private Instant at(LocalDate d) {
        return d.atStartOfDay(ZONE).toInstant();
    }

    private int sub(int overall) {
        return clamp(overall + (rnd.nextDouble() < 0.6 ? 0 : rnd.nextBoolean() ? 1 : -1), 1, 5);
    }

    private <T> T pick(List<T> list) {
        return list.get(rnd.nextInt(list.size()));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static String mapsSearchUrl(String query) {
        return "https://www.google.com/maps/search/?api=1&query="
                + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
    }

    static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal percent) {
        return base.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
