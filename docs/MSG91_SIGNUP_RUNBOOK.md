# MSG91 quick-start runbook (steps 1 & 2)

Complete this in the MSG91 dashboard, then share credentials in chat so we can run the campaign test locally.

## Step 1 — Account (~15 min + KYC wait)

1. Go to [msg91.com/signup](https://msg91.com/signup) and create an account.
2. Complete **KYC** (PAN + business details). Approval is usually same-day to 1–2 days.
3. Add wallet balance (₹500 is enough for testing).
4. Copy your **Auth Key**: Dashboard → API → Auth Key.

### WhatsApp Business (required for bill receipts + WA campaigns)

1. Dashboard → **WhatsApp** → Connect / Onboard.
2. Link your **Meta Business Manager** account and complete Meta verification if prompted.
3. Note your **integrated WhatsApp number** (format: `919876543210`).

### SMS (required for SMS campaigns)

1. Register **DLT** entity + headers on MSG91 (India regulatory requirement).
2. Create an approved **promotional SMS template** with variables for name + offer text.
3. Create a **Flow** in MSG91 Flow section mapping `VAR1` = customer name, `VAR2` = promo text.
4. Note **flow_id** and **sender id** (6-char alphanumeric).

> SMS DLT can take 1–3 days. WhatsApp templates usually approve in hours. You can test WhatsApp first while SMS is pending.

---

## Step 2 — Create WhatsApp templates

Create both in MSG91 → WhatsApp → Templates → Create Template.

### Template A: `salon_promo` (Marketing)

| Field | Value |
|-------|-------|
| Category | Marketing |
| Language | English |
| Header | None |
| Body | `Hi {{1}}, {{2}} — Book your next visit at Demo Salon Brand today!` |
| Footer | `Reply STOP to opt out` |
| Sample {{1}} | `Rahul` |
| Sample {{2}} | `20% off on hair spa this weekend!` |

### Template B: `bill_receipt` (Utility)

| Field | Value |
|-------|-------|
| Category | Utility |
| Language | English |
| Header | **Document** (dynamic) |
| Body | `Hi {{1}}, thank you for your visit! Invoice {{2}} for ₹{{3}} is attached.` |
| Sample {{1}} | `Rahul` |
| Sample {{2}} | `LIT-2026-00001` |
| Sample {{3}} | `1500` |

Submit both for Meta approval. Status must show **Approved** before API sends work.

---

## What to paste back in chat

```
MSG91_AUTH_KEY=...
MSG91_WHATSAPP_NUMBER=91XXXXXXXXXX
MSG91_PROMO_SMS_FLOW_ID=...        # when SMS flow is ready
MSG91_SMS_SENDER=...               # 6-char sender id
TEST_PHONE_1=XXXXXXXXXX            # receives WhatsApp test
TEST_PHONE_2=XXXXXXXXXX            # receives SMS test
```

Also confirm template names if different from `salon_promo` / `bill_receipt`.

---

## What we run after (step 3 — no deploy)

```bash
# Terminal 1 — backend with MSG91 creds
export MSG91_AUTH_KEY=...
export MSG91_WHATSAPP_NUMBER=...
export MSG91_PROMO_SMS_FLOW_ID=...
export MSG91_SMS_SENDER=...
export PUBLIC_URL=http://localhost:8080
cd backend && ./mvnw spring-boot:run

# Terminal 2 — campaign test
export TEST_PHONE_1=...
export TEST_PHONE_2=...
chmod +x scripts/test-msg91-campaign.sh
./scripts/test-msg91-campaign.sh
```

This creates 2 test customers, sends one WhatsApp campaign to `TEST_PHONE_1` and one SMS campaign to `TEST_PHONE_2`.
