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

---

### 4.3 Platform app (`/platform/*`)

**Audience:** Antrahq internal — `PLATFORM_SUPER_ADMIN` or `SALES_EXECUTIVE`.

| Role | Features |
|------|----------|
| **Platform super admin** | `/platform/overview`, `/platform` tenant list, `/platform/sales/*` pipeline, incoming leads, sales team |
| **Sales executive** | `/platform/sales` pipeline, `/platform/sales/growth`, lead detail; restricted from tenant admin routes |

Backend: `/api/v1/platform/*` for tenants, users, cross-tenant operations.

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
5. After implementation, run **build/test** and follow **§7** for prod push.

Keep this file updated when adding major modules or changing deploy topology.
