# Salon Platform / Antrahq — Product & Engineering Context

This document is the **single source of context** for AI assistants and engineers extending the salon platform. It describes what each app surface does, how we build and design, and how changes reach production.

---

## 1. Product overview

**Antrahq** (repo name: *salon-platform*) is a **multi-tenant SaaS** for Indian salon chains: walk-in billing, GST invoices, memberships/packages, branch operations, brand admin analytics, and platform-level tenant/sales tooling.

- **Production API:** `https://api.antrahq.com`
- **Production frontend:** static app on CloudFront/S3 (built with `NEXT_PUBLIC_API_URL=https://api.antrahq.com`)
- **Pilot tenants / branches (demo):** e.g. Mantri Lithos, Mantri Webcity under demo brand tenants

Each **tenant** (brand) has **branches**, **staff**, **catalog**, **customers**, and **bookings**. Data is isolated by `tenantId`; managers are scoped to a single `branchId`.

---

## 2. Repository layout

Local monorepo folder (not one git root):

| Path | Git remote | Deploy trigger |
|------|------------|----------------|
| `salon-platform/backend/` | `github.com/sumitsuri/salon-platform-backend` | Push to `main` → ECR + SSM deploy |
| `salon-platform/frontend/` | `github.com/sumitsuri/salon-platform-frontend` | Push to `main` → S3 + CloudFront |
| `salon-platform/marketing/` | separate marketing repo | own workflow |
| `salon-platform/salon-platform-infra/` | Terraform / nginx / deploy scripts | manual / infra pipeline |

**Always commit and push from the correct sub-repo** (`backend/` or `frontend/`), not the parent folder.

---

## 3. Roles and app routing

After login, home route comes from `getHomeForRole()` in `frontend/src/lib/auth-store.ts`:

| Role | Home | Primary app |
|------|------|-------------|
| `PLATFORM_SUPER_ADMIN` | `/platform/overview` | Platform |
| `SALES_EXECUTIVE` | `/platform/sales` | Platform → Sales |
| `BRAND_ADMIN` | `/admin` | Admin (CEO / brand) |
| `BRANCH_MANAGER`, `SALON_MANAGER` | `/manager` | Manager (branch floor) |

**Admin layout** allows `BRAND_ADMIN` and `PLATFORM_SUPER_ADMIN`. Everyone else is redirected to `/manager`.

---

## 4. App surfaces and features

### 4.1 Manager app (`/manager/*`)

**Audience:** Branch managers and salon managers on **tablet/mobile** (bottom tab bar, touch-first).

**Scope:** Single branch (`user.branchId`). APIs must respect branch access; UI assumes one branch context.

| Area | Routes | Capabilities |
|------|--------|--------------|
| **Home** | `/manager` | **Earn-now block first** (Walk-in / Membership / Package promos), then **Today at a Glance**: date selector, **compact monthly target chip** (red &lt;90%, amber 90–99%, green ≥100% of goal; MTD pace + daily avg vs target pace), 2×2 KPI grid, link to Insights |
| **Floor / schedule** | `/manager/schedule` | Floor schedule view |
| **Walk-in** | `/manager/walk-in` | New walk-in, open visits, billing: multi-qty lines, per-unit stylists, services/packages, collapsed membership + **visit scratch card** (when branch enabled), stacked manual + promo/scratch discounts, **full-screen payment success** (amount paid, review QR, Done → home) |
| **Memberships** | `/manager/memberships` | Sell membership (`?sell=1`), **`soldByStaffId` required** |
| **Packages** | `/manager/packages` | Sell service packages on floor; staff seller for package incentive |
| **Customers** | `/manager/customers` | Branch-scoped guest CRM |
| **Bookings** | `/manager/bookings` | Booking list (more menu) |
| **Attendance / employees** | `/manager/attendance` | Team attendance |
| **Stock hub** | `/manager/stock` | Links to inventory + expenditure |
| **Inventory** | `/manager/inventory` | Stock movements |
| **Daily expenditure** | `/manager/expenditure` | **Manager-only daily costs** (misc); not rent/payroll |
| **Services / sales** | `/manager/services` | Service contribution / sales view |
| **Insights** | `/manager/insights` | Recommendations / tips |

**Manager home — business rules (important):**

- **Layout order:** `ManagerHomeFloorActions` (Don’t Let Earnings Walk Away + three promos) **above** `ManagerHomeGlanceSection`; section titles share `manager-home-glance-title` styling.
- **Glance date range:** Default **today**, compare to **previous day**. Selector allows **today**, **this month**, **last month**, or **custom** dates only within **last calendar month → today**.
- **Branch monthly target (manager-visible):** `GET /api/v1/branches/performance/targets` scoped to manager’s branch (same pattern as dashboard). MTD sales through selected date vs **monthlySalesTarget**; API returns **expectedSalesSoFar**, **dailyAverageActual**, **dailyAverageExpected**, **catchUpDailyAverage**, **onTrack**. UI: `ManagerHomeTargetChip` → Insights.
- **Staff promo incentives (constants in `manager-home-sell-incentives.ts`):** Membership **₹50 flat** to credited staff; package **3%** of sale, **cap ₹500**.
- **Expenditure:** Backend sets `managerRecorded=true` only for manager-created **MISCELLANEOUS** lines. List API for managers filters to those rows. Rent, salary, product-cost sync, etc. are **admin-only** (`admin/finance`).

**Mobile nav:** Today | Floor | Stock | Employees | More (FAB walk-in on some screens).

---

### 4.2 Admin app (`/admin/*`)

**Audience:** Brand CEO (`BRAND_ADMIN`) — desktop-first dashboard patterns, multi-branch filters.

| Section | Routes | Capabilities |
|---------|--------|--------------|
| **Overview** | `/admin` | KPI strip, branch performance, employee check-in/sales, targets, P&L/inventory teasers, scope date + branch filters |
| **Intelligence** | `/admin/market-pulse`, `/admin/local-spotlight`, `/admin/insights`, `/admin/guest-voice` | Market and guest intelligence |
| **Operations** | `/admin/bookings`, `/admin/customers`, `/admin/services`, `/admin/inventory`, `/admin/employees` | Full brand operations |
| **Growth** | `/admin/leads`, `/admin/campaigns`, `/admin/whatsapp-templates`, `/admin/promotions`, `/admin/scratch-cards` | Leads, campaigns, promos, **visit scratch campaigns** (prizes, active window) |
| **Business** | `/admin/finance`, `/admin/branches` | **All expenditure categories**, payroll sync, P&L, branch targets, org users; per branch: **monthly sales target**, **Enable scratch-card rewards at walk-in** (`scratchCardEnabled`, default **off**) |

**Expenditure (admin):** Full ledger — rent, employee salary, product cost, accommodation, miscellaneous; payroll sync endpoint; used in finance P&L.

**Team requests — leave approvals + salary advances (`/admin/requests`, Ops hub tile, banner on `/admin/employees`):**
- Employees apply from the employee app (`/employee/advance`, Time → Leave); managers/CEO decide. Leave: managers (branch-scoped) or brand admin approve/reject with an optional note; employees can cancel their own *pending* leave (`CANCELLED`); overlapping leave is refused.
- Advances are **owner-only** (`assertBrandAdmin`). Revolving budget: `available = pool − SCHEDULED installments`, so repayments free the budget again. Fairness knobs (`salary_advance_policies`): per-employee share of pool %, salary multiple, max installments, cooldown days, enable/pause. Approval takes a `PESSIMISTIC_WRITE` lock on the policy row so two approvals cannot overspend. Approved amount may be lower than asked; installments split equally (remainder on last); first deduction defaults to next month (IST). Repeat requesters (≥3 in 180 days) are flagged. Owners can also **record an advance directly** (`POST /api/v1/salary-advances/grant`) when money was paid outside the employee app — same budget/per-employee checks and installment schedule, no employee cooldown.
- Deductions tab: "mark month deducted" settles due installments; individual installments can be waived; an advance completes when nothing is left to recover. Advances are **not** auto-applied to payroll/P&L — "after advance" = salary minus deductions only.
- Schema: new tables come from `ddl-auto: update`; `SalaryAdvanceSchemaPatch` adds `leave_records.decision_note/decided_at` and recreates `leave_records_status_check` to include `CANCELLED` (Hibernate never alters existing CHECK constraints).
- Copy: English base + Hindi for employee-facing strings; other locales fall back to English.
- Local employee-app testing: `/employee/*` redirects off plain `localhost`. Run `bash scripts/dev-server.sh restart` with session-only `APP_CORS_ALLOWED_ORIGINS=<defaults>,http://employee.localhost:3000 NEXT_PUBLIC_API_URL=http://127.1:8080 NEXT_PUBLIC_EMPLOYEE_HOST=employee.localhost NEXT_PUBLIC_EMPLOYEE_APP_URL=http://employee.localhost:3000`, then browse `http://employee.localhost:3000/employee/`. Restart without those vars afterwards.

---

### 4.3 Platform app (`/platform/*`)

**Audience:** Antrahq internal — `PLATFORM_SUPER_ADMIN` or `SALES_EXECUTIVE`.

| Role | Features |
|------|----------|
| **Platform super admin** | `/platform/overview`, `/platform` tenant list, **`/platform/usage`** (customer usage), `/platform/sales/*` pipeline, incoming leads, sales team |
| **Sales executive** | `/platform/sales` pipeline, `/platform/sales/growth`, lead detail; restricted from tenant admin routes |

Backend: `/api/v1/platform/*` for tenants, users, cross-tenant operations.

#### 4.3.1 Customer usage (platform analytics)

**Product name in UI:** “Customer usage” — adoption and feature-level activity across **all brands** (tenants), with drill-down to branches and users.

| Surface | Route | Purpose |
|---------|-------|---------|
| Portfolio | `/platform/usage` | KPI cards with change vs previous period, **account health** table (score, status, trend sparkline, change, adoption, last active) with health-segment filter, **feature adoption across brands** (brand reach, last used, trend), platform daily chart, **brand × app heatmap** |
| Brand drill-down | `/platform/usage/detail?tenantId={uuid}` | Tabs: **Overview** (health score + breakdown, KPIs, auto-generated findings, daily events by app), **Features** (events, user reach, trend, first/last used), **Branches** (scorecard incl. silent branches + **feature × branch matrix**), **Users** (events, features, last seen, status + **feature × user matrix**), **Activity** (recent events feed); plus branch × app heatmap and user activity calendar; filters: date, branch, user, role, search |

**Audience:** `PLATFORM_SUPER_ADMIN` only (`SecurityUtils.assertPlatformAdmin()` on APIs).

**Production URLs:** Platform UI `https://platform.antrahq.com/platform/usage/` · API `https://api.antrahq.com`.

---

##### Data model and ingestion

| Store | Role |
|-------|------|
| **`product_events`** | Raw telemetry (source of truth for live analytics). Rows include `tenant_id`, `user_id`, `branch_id`, `role`, `portal`, `event_name`, optional `feature_key`, `route`, `occurred_at`, `session_id`. |
| **`usage_daily_*` rollups** | `usage_daily_tenant`, `usage_daily_tenant_feature`, `usage_daily_user` — populated by `ProductUsageRollupService` (hourly / on portfolio refresh). Used when raw reads fail or for legacy summary paths. |
| **`user_usage_snapshot`** | Login counts, last seen, `days_active_28d` (updated from rollups). |

**How events are produced (frontend):**

- `ProductTelemetryTracker` on employee / manager / admin shells records sign-in, session, screen views, feature use.
- Stable **feature keys** are derived from routes only (brand-agnostic): `frontend/src/lib/product-analytics/route-feature-map.ts` → e.g. `/admin/guest-voice` → `admin.guest_voice`.
- Ingest: `ProductTelemetryIngestService` → `POST /api/v1/telemetry/events` (see `IngestTelemetryEventRequest`).

**Feature dimension in SQL:** `ProductUsageSqlSupport.featureDimensionSql()` probes `information_schema` for `product_events.feature_key`. If the column is missing (older prod DB until patch runs), SQL uses `event_name` only; otherwise `COALESCE(NULLIF(TRIM(feature_key), ''), event_name)`. Call `usageSql.ensureTelemetrySchema()` at the start of portfolio/tenant analytics (via `ProductTelemetrySchemaPatch.ensureProductEventsColumnsPublic()`).

**Do not** reference a non-existent `route` column in analytics GROUP BY (removed from prod-safe paths).

---

##### Backend APIs and services

| Endpoint | Response | Service |
|----------|----------|---------|
| `GET /api/v1/platform/usage/portfolio?from&to&includeDemo&tenantId&branchId` | `PlatformUsagePortfolioResponse` | `PlatformUsageAnalyticsService.portfolio()` |
| `GET /api/v1/platform/usage/tenants/{tenantId}/analytics?from&to&branchId&userId&role&search` | `PlatformUsageAnalyticsResponse` | `tenantAnalytics()` — resilient sections (`safeDailyTenant`, `safeFeatureUsage`, …) |
| `GET /api/v1/platform/usage/heatmap?from&to&includeDemo&tenantId&branchId` | `PlatformUsageHeatmapResponse` | `heatmap()` — portal totals; tenant×portal grid (portfolio); branch×portal + user×day when `tenantId` set |
| `GET /api/v1/platform/usage/insights/portfolio?from&to&includeDemo` | `PlatformUsageInsights.Portfolio` | `PlatformUsageInsightsService.portfolio()` — per-brand totals vs previous period, health, daily trend, platform feature reach |
| `GET /api/v1/platform/usage/insights/tenants/{tenantId}?from&to&branchId` | `PlatformUsageInsights.Tenant` | `PlatformUsageInsightsService.tenant()` — health, daily-by-app, feature/branch/user rows with last-used and trends, feature × branch and feature × user matrices, recent activity |
| `GET /api/v1/platform/usage/summary?from&to&includeDemo` | Legacy rollup-oriented summary | `PlatformUsageService` |
| `GET /api/v1/platform/usage/telemetry-status` | Pipeline health | `ProductTelemetryStatusService` |

**Insights layer (adoption analytics):** `PlatformUsageInsightsService` is deliberately separate from `PlatformUsageAnalyticsService` so the existing screens are untouched. It reads `product_events` directly with explicit SQL strings, compares the selected range with the immediately preceding range of equal length, and collapses daily series into at most 60 buckets for sparklines (`bucketize`). **Health score (0–100)** = 35% adoption (active ÷ provisioned users, capped at 100) + 25% feature breadth (distinct features ÷ core set of up to 8) + 20% stickiness (WAU ÷ MAU) + 20% momentum (events vs previous period, 50 = flat). Status: `DORMANT` (no activity in last 14 days; score capped at 25), `NEW` (no previous-period baseline), `AT_RISK` (score < 45, or events fell ≥ 50% from a previous period with ≥ 20 events; score capped at 44), `WATCH` (45–69), `HEALTHY` (≥ 70). The score components are returned so the UI can explain it. Rules are unit-tested in `PlatformUsageInsightsServiceTest`.

**Core class:** `PlatformUsageAnalyticsService` — filters via inner `EventFilter` / `FilterSql` (`AND tenant_id = ?`, branch, user, role, search, demo exclusion).

**Date ranges:**

- Frontend sends ISO instants from **local calendar bounds** (`toIsoDateTimeRange` in `frontend/src/lib/date-range.ts`) so “Today” / “Last 60 days” match operator timezone (e.g. IST).
- Backend `normalizeRange()` maps request instants to **UTC date** bounds (`InstantRange.fromDates`) for SQL `occurred_at >= ? AND occurred_at < ?`.

**Portfolio logic (high level):** Count raw `product_events` in range; if table ready, build from live events + optional rollup refresh; if aggregates return 0 but raw count &gt; 0, rebuild via resilient path; if table unavailable and no raw rows, fall back to `portfolioFromRollups()`.

---

##### Frontend modules

| File | Responsibility |
|------|----------------|
| `frontend/src/app/platform/usage/page.tsx` | Portfolio entry |
| `frontend/src/app/platform/usage/detail/page.tsx` | Brand drill-down (`tenantId` query) |
| `PlatformUsagePortfolioView.tsx` / `PlatformUsageTenantView.tsx` | Charts (MetricChart), tables, filters |
| `PlatformUsageFilterBar.tsx` | Date, branch, user, role, search |
| `insights/PortfolioInsights.tsx` / `insights/TenantInsights.tsx` | Adoption insights UI (health, change, trends, matrices); replace the former brand/feature/branch/user tables |
| `insights/usage-viz.tsx` / `insights/usage-format.ts` | Shared visuals (health badge/ring, score breakdown, change chip, relative time, stacked daily bars, matrix grid) and status metadata |
| `feature-labels.ts` + `lib/product-analytics/feature-key-labels.ts` | **Display names** for keys (e.g. `admin.guest_voice` → “Guest voice”) — keys stay dotted in DB only |
| `UsageHeatmap.tsx` / `PlatformUsageHeatmapSection.tsx` | Heatmap grids and drill-down (portfolio: click brand cell → detail; tenant: branch cell sets `branchId` in URL) |
| `frontend/src/lib/api.ts` | `getPlatformUsagePortfolio`, `getPlatformUsageAnalytics`, `getPlatformUsageHeatmap` |

**UI rules for feature names:** Tables and charts show **catalog labels**, not raw `admin.home` strings. Optional tooltip (`title`) holds the stable key for support. Subtitle on tenant feature rows may show “Admin app” / “Manager app” via `featurePortalLabel()`.

---

##### Known pitfalls (read before changing usage SQL)

1. **Java text-block concatenation** — Patterns like `""" SELECT """ + expr` or `""" ... """ + sql.whereClause + """ GROUP BY ... """` can compile to invalid SQL (`SELECTCOALESCE`, `?GROUP BY`). **Always** use explicit strings: `"SELECT " + dim + " ..." + sql.whereClause + " GROUP BY ..."`. This caused prod symptoms: header showed correct **totalEvents** / WAU while daily chart, features, and user rows were **empty** (failed queries caught in `safe*` methods).
2. **Partial failure** — Summary queries (`COUNT(*)`, `COUNT(DISTINCT user_id)`) can succeed while breakdown queries fail; UI looks “half broken”. Use verification scripts after changes.
3. **Deploy timing** — Backend EC2 restart may return **502** for ~30–60s; heatmap may 500 until new container is live.
4. **Demo tenants** — Portfolio `includeDemo` checkbox; demo tenant IDs excluded from platform filters when unchecked (`EventFilter.platform`).

---

##### Verification before prod (required for usage work)

Scripts live in monorepo `salon-platform/scripts/` (run from dev machine; not deployed):

```bash
# 1) SQL patterns against local Docker Postgres (feature_key present/absent)
bash salon-platform/scripts/verify-usage-sql-local.sh

# 2) API contract — local or prod
API_BASE=http://localhost:8089 MYSTIC_TENANT_ID=<tenant-with-events> \
  node salon-platform/scripts/verify-tenant-usage.mjs

# Prod (default API https://api.antrahq.com, Mystic tenant id in script)
node salon-platform/scripts/verify-tenant-usage.mjs
```

The script also checks the insights endpoints for internal consistency (trend buckets sum to event counts, per-app split equals the daily total, segments equal brand count, dormant brands score ≤ 25).

**Testing the platform app locally:** `/platform/*` on any host other than the platform host redirects to production. For a local run, start the stack with session-only env vars (do not commit them): `APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://platform.localhost:3000 NEXT_PUBLIC_PLATFORM_HOST=platform.localhost NEXT_PUBLIC_PLATFORM_APP_URL=http://platform.localhost:3000 NEXT_PUBLIC_API_URL=http://127.1:8080 bash scripts/dev-server.sh restart`, then open `http://platform.localhost:3000/`. (`127.1` is used because the client drops a `localhost` API URL on non-`localhost` hosts and assumes a same-origin proxy.) The local database has almost no telemetry; generate synthetic events tagged `properties->>'synthetic' = 'true'` and delete them afterwards.

**Pass criteria when `totalEvents > 0`:** `sum(daily.eventCount) > 0`, `featureUsage.length > 0`, `activeUsersInRange > 0` (and ideally user rows with `eventsInRange > 0` when event `user_id`s match `users` table).

**Typical deploy order for usage features:** backend first (`salon-platform-backend` `main`) → wait for GitHub Actions + SSM → run prod verify script → frontend if UI/API types changed → hard refresh platform (CloudFront).

---

##### Key commits (reference)

| Area | Notes |
|------|--------|
| SQL concatenation fix | `PlatformUsageAnalyticsService` + `ProductUsageSqlSupport` — explicit SQL strings |
| Heatmap API | `PlatformUsageHeatmapResponse`, `GET .../usage/heatmap` |
| Labels + heatmap UI | `feature-key-labels.ts`, `PlatformUsageHeatmapSection` |

---

### 4.4 Customer / public routes (same frontend build)

| Route | Purpose |
|-------|---------|
| `/login`, `/forgot-password`, `/reset-password` | Auth |
| `/book/...` | Online booking per tenant/branch slug |
| `/pass` | Customer pass / visit pass flows |
| `/review` | Post-visit review |
| `/scratch` | Guest scratch-card reveal (token in query); public API |

These share the static export; not part of manager/admin shells.

---

### 4.5 Backend API (cross-cutting)

Spring Boot 3.5, Java 21, PostgreSQL, Redis. Key modules (see `backend/README.md`):

- Auth JWT, multi-tenant security (`SecurityUtils`, branch access)
- Bookings, billing, GST invoices (PDF)
- Memberships, packages, promotions
- Analytics: dashboard, staff sales, **staff promo sales**, service contribution, attendance, recommendations
- Branch target performance (`BranchPerformanceService`; **managers** may read targets for their branch only)
- **Scratch footfall:** `/api/v1/scratch-cards/*` (issue, redeem, by-booking), `/api/v1/scratch-campaigns/*` (admin), public scratch endpoints; **one card per bill** (`booking_id` uniqueness)
- Expenditures (with manager vs admin semantics)
- Inventory
- Platform tenant administration
- Sales module (leads) for platform

**Schema evolution:** Many `*SchemaPatch` `ApplicationRunner` classes apply idempotent SQL (e.g. `ExpenditureSchemaPatch`, `MembershipSoldByStaffSchemaPatch`, `ScratchCampaignSchemaPatch`, `branches.scratch_card_enabled` default false).

**Walk-in billing:** Manual manager discount may **stack** with scratch/coupon promo (`BookingService` / `GstCalculationService`).

---

## 5. Development guidelines

Patterns consistently used in this codebase — **follow these for new work**.

### 5.1 General engineering

- **Minimize diff scope** — fix the requested behavior; do not refactor unrelated code.
- **Match existing conventions** — naming, folder layout, TanStack Query keys, `api.ts` client methods, DTO shapes.
- **No over-abstraction** — prefer inline logic over one-off helpers; reuse existing lib/components.
- **Comments** — only for non-obvious business rules (e.g. manager expenditure vs admin).
- **Tests** — add only when they cover real behavior; run `mvn test` (backend) and `npm run build -- --webpack` (frontend) before prod deploy.
- **Commits** — only when explicitly requested; never force-push `main`; never commit secrets (`.env.local`, credentials).

### 5.2 Frontend

- **Next.js App Router**, static export for production (`output: export` pattern; build via `npm run build -- --webpack`).
- **TypeScript** strict; types for API in `frontend/src/lib/api.ts`.
- **Data fetching:** TanStack Query (`useQuery` / `useMutation`); invalidate related query keys on mutation success.
- **Client state:** Zustand (`auth-store`, `theme-store`).
- **i18n:** `next-intl`; primary copy in `frontend/messages/en-IN.json` (other locales exist; update en-IN for new strings).
- **Styling:** Tailwind + CSS variables (`--brand`, `--surface`, `--text-primary`, etc.); shared UI in `frontend/src/components/ui.tsx` and `enterprise-ui.tsx`.
- **Manager promos / home:** `manager-home-cta-themes.css`, `manager-home-glance.css` imported from `globals.css`; compact promo components under `components/manager/`.
- **Branch scope:** Manager pages use `user.branchId`; pass `branchIds: [branchId]` to analytics APIs.
- **Admin scope:** `useAdminBranchSelection()` + `ScopeFilterBar` for date range and branch multi-select.

### 5.3 Backend

- **Layers:** controller → service → repository; DTOs in `dto/`; enums in `domain/enums/`.
- **Authorization:** `SecurityUtils.assertBrandAdminOrAbove()`, `isManagerRole()`, `assertBranchAccess(branchId)` — enforce server-side, not only UI. Branch target performance uses manager branch scoping (same idea as `AnalyticsService.resolveBranchIds`).
- **Manager expenditure:** restrict category on create; filter list by `managerRecorded`.
- **Analytics:** `GET /api/v1/analytics/dashboard`, `.../staff-promo-sales`, etc. — date range via `startDate` / `endDate`.
- **Platform customer usage:** `PlatformUsageAnalyticsService`, `ProductUsageSqlSupport`, `ProductUsageRollupService`, `ProductTelemetryIngestService` — see **§4.3.1**; never glue SQL with adjacent text blocks without explicit spaces before `GROUP BY` / function names.
- **Scratch gating:** `Branch.scratchCardEnabled` — when false, **no new card issue** and empty active-campaign list for managers; redeem allowed for a card already tied to the current bill.
- **Local profile:** `mvn spring-boot:run -Dspring-boot.run.profiles=local` (requires Docker Postgres/Redis from `docker compose up -d` at monorepo root).

### 5.4 Feature flags / ops

- **Scratch cards:** Shipped; **disabled by default per branch** until **Admin → Branches → Enable scratch-card rewards at walk-in**. Admin configures campaigns under **Scratch cards**.
- **Ad-hoc SQL/CSV in `backend/scripts/`** — operational audits, not part of app deploy.

---

## 6. UX and design principles

### 6.1 Manager experience (primary)

- **Mobile-first, no horizontal scroll for primary KPIs** — use **2×2 grids** on small screens; full row of four only on larger breakpoints.
- **Thumb-friendly** — min tap targets (~44px / `min-h-11`), bottom navigation, FAB for walk-in where appropriate.
- **Information density without clutter** — compact promo cards: icon | title + subtitle + feature tags | kicker + action pill; avoid tall glass cards that push content below the fold.
- **Clear money story** — show incentive amounts (₹50 membership, up to ₹500 package) on CTAs; staff must be **credited** on sell flows.
- **Target motivation** — traffic-light target chip with subtle motion (pulse / nudge / glow); respect `prefers-reduced-motion`.
- **Payment closure** — after pay, full-screen success (no walk-in chrome behind); one primary **Done** back to manager home.
- **Separation of concerns** — managers log **daily branch expenses**; CEOs see **full P&L expenditure** in admin finance.
- **Defaults that match floor rhythm** — home KPIs default to **today vs yesterday**; optional month ranges via constrained date picker.

### 6.2 Admin experience

- **Overview-first** — command bar, KPI strip, branch comparison, employee tables, action rails for recommendations.
- **Filters visible** — date preset + branch scope on dashboard widgets; consistent `ProductDateRange` presets (`today`, `last_30_days`, etc. in `lib/date-range.ts`).
- **Desktop density** — tables, teasers linking to deep modules (finance, services, bookings).

### 6.3 Visual system

- **Antrahq / tenant theming** — `theme-store` + CSS variables; optional accent from tenant `primaryColor`.
- **Semantic color** — emerald revenue, violet/purple walk-ins, amber tickets, rose/green deltas for trends.
- **Motion** — subtle pulse on incentive CTAs; respect `prefers-reduced-motion` in promo CSS.
- **Accessibility** — meaningful `aria-labelledby` on sections; don’t rely on color alone for trends (icons + % text).

### 6.4 Copy and locale

- Indian currency formatting via shared `formatCurrency` / locale kit.
- Short mobile labels (e.g. “vs prev. day”) with full text on larger screens where implemented.

---

## 7. Deployment: local → production

### 7.1 Pre-deploy checklist

**Backend** (from `salon-platform/backend/`):

```bash
mvn test
# optional: mvn -q spring-boot:run -Dspring-boot.run.profiles=local
# if touching platform usage analytics:
bash ../scripts/verify-usage-sql-local.sh   # from repo root scripts path on dev machine
node ../scripts/verify-tenant-usage.mjs     # with API_BASE pointing at local or staging API
```

**Frontend** (from `salon-platform/frontend/`):

```bash
npm run build -- --webpack
```

Ensure:

- Production build includes intended routes (e.g. `/scratch`, `/manager/walk-in`).
- `.env.local` is **not** relied on in CI (production build uses env in GitHub Actions).
- Staged files match intent (no accidental scripts or secrets).

### 7.2 Git push (triggers prod)

**Backend:**

```bash
cd salon-platform/backend
git add <files>
git commit -m "..."
git push origin main
```

**Frontend:**

```bash
cd salon-platform/frontend
git add <files>
git commit -m "..."
git push origin main
```

Each push to **`main`** runs GitHub Actions:

| Workflow | What it does |
|----------|----------------|
| `backend/.github/workflows/deploy.yml` | Build ARM Docker image → push ECR → SSM run `/opt/salon-platform/scripts/deploy.sh backend` on prod app host |
| `frontend/.github/workflows/deploy.yml` | `npm ci` + build with `NEXT_PUBLIC_API_URL=https://api.antrahq.com` → sync `out/` to S3 `__app/` → CloudFront invalidation `/*`; verifies bundles contain `api.antrahq.com` and not `localhost:8080` |

Monitor:

```bash
gh run list --repo sumitsuri/salon-platform-backend --limit 3
gh run list --repo sumitsuri/salon-platform-frontend --limit 3
```

### 7.3 Local verification (restart dev servers)

From monorepo root, typical flow:

```bash
# Infrastructure
docker compose up -d

# Kill old dev ports
lsof -ti:8080 | xargs kill -9 2>/dev/null
lsof -ti:3000 | xargs kill -9 2>/dev/null

# Backend
cd backend && mvn -q spring-boot:run -Dspring-boot.run.profiles=local

# Frontend (separate terminal)
cd frontend && npm run dev
```

- API: http://localhost:8080  
- App: http://localhost:3000  
- Manager home: http://localhost:3000/manager  

Frontend `.env.local`:

```
NEXT_PUBLIC_API_URL=http://localhost:8080
```

### 7.4 Post-deploy smoke (manager + admin)

1. Manager home — **three earn promos on top**, then glance: date selector, **target chip** (shows MTD vs branch target when set), 4 KPIs.  
2. Walk-in — pay flow → **full-screen payment complete** (amount, review QR, Done).  
3. Walk-in scratch — **hidden** until branch scratch enabled in admin; then billing modal, claim-on-bill (no auto-redeem on reveal).  
4. Sell membership — requires staff seller; API accepts `soldByStaffId`.  
5. Manager expenditure — only manager-logged daily lines.  
6. Admin → branch edit — **scratch toggle**; Admin → **Scratch cards** campaigns.  
7. Admin finance — full expenditure including rent/salary.  
8. Hard refresh or incognito if CloudFront cache lingers (~1–2 minutes after invalidation).

### 7.5 When backend vs frontend must ship together

- **API contract changes** (new fields, manager-only filters): deploy **backend first**, then frontend.  
- **UI-only** (layout, copy, CSS): frontend-only deploy is safe.  
- **Schema patches**: backend deploy applies SQL on startup — no manual migration step in normal flow.

---

## 8. Key files quick reference

| Topic | Location |
|-------|----------|
| API client & types | `frontend/src/lib/api.ts` |
| Auth & role routing | `frontend/src/lib/auth-store.ts` |
| Date ranges (admin) | `frontend/src/lib/date-range.ts` |
| Manager home date rules | `frontend/src/lib/manager-home-date-range.ts` |
| Manager incentives | `frontend/src/lib/manager-home-sell-incentives.ts` |
| Manager home UI | `frontend/src/components/manager/ManagerHomeGlanceSection.tsx`, `ManagerHomeFloorActions.tsx`, `ManagerHomeTargetChip.tsx` |
| Walk-in payment success | `frontend/src/app/manager/walk-in/WalkInPaymentCompleteModal.tsx` |
| Walk-in scratch billing | `frontend/src/app/manager/walk-in/WalkInScratchPanel.tsx`, `BillingScratchModal.tsx`, `frontend/src/components/scratch/ScratchCardFlow.tsx` |
| Branch target API | `backend/.../BranchPerformanceService.java`, `BranchController` `/performance/targets` |
| Scratch footfall API | `backend/.../ScratchFootfallService.java`, `ScratchCardController`, `ScratchCampaignController` |
| Branch scratch flag | `backend/.../Branch.java` `scratchCardEnabled`, `BranchSchemaPatch` |
| Expenditure service | `backend/.../ExpenditureService.java` |
| Staff promo analytics | `backend/.../StaffPromoSalesAnalyticsService.java` |
| **Platform customer usage** | **§4.3.1** — `PlatformUsageAnalyticsService.java`, `PlatformUsageController.java`, `ProductUsageSqlSupport.java`, `ProductUsageRollupService.java`, `ProductTelemetryIngestService.java`, `route-feature-map.ts`, `frontend/src/modules/platform/usage/*`, `scripts/verify-tenant-usage.mjs`, `scripts/verify-usage-sql-local.sh` |
| Deploy workflows | `frontend/.github/workflows/deploy.yml`, `backend/.github/workflows/deploy.yml` |

---

## 9. Demo credentials (local / seeded)

See `backend/README.md` and root `README.md`:

| Role | Email | Password |
|------|-------|----------|
| Platform Admin | platform@salonplatform.local | admin123 |
| Brand CEO | ceo@demo-brand.local | ceo123 |
| Lithos Manager | manager.lithos@demo-brand.local | manager123 |
| Webcity Manager | manager.webcity@demo-brand.local | manager123 |

---

## 10. How to use this doc with Claude

When asking for enhancements:

1. State **which app** (manager / admin / platform / public).  
2. Reference **branch vs brand scope** and **mobile vs desktop**.  
3. Call out if change needs **backend + frontend** or UI-only.  
4. For manager money features, check **expenditure** and **incentive** rules above.  
5. For **platform customer usage** (`/platform/usage`), read **§4.3.1** and run verify scripts before pushing backend analytics changes.  
6. After implementation, run **build/test** and follow **§7** for prod push.

Keep this file updated when adding major modules or changing deploy topology.
