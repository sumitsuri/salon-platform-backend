# WhatsApp templates — Meta-compliant (Antrahq WABA)

Use these when creating templates in **WhatsApp Manager** or **MSG91 → WhatsApp → Templates**.

Rejected templates cannot be edited. Create a **new template** with a **new name** (do not reuse `account_creation_confirmation_antrahq_3`).

---

## How to check account status

| What you need | Where to look | Healthy signal |
|---------------|---------------|----------------|
| Phone number can send messages | [WhatsApp Manager](https://business.facebook.com/wa/manage) → **Account tools → Phone numbers** → **Status** column | **Connected** |
| WABA not restricted | Same page → top banners / quality rating | No “restricted” or “disabled” banner |
| Business verification | Meta Business Suite → **Settings → Business info** | Verified (optional but helps limits) |
| Template-only issues | Business Support Home → **Activity issues → Rejected message templates** | Account still **Connected**; only specific templates blocked |

**Business Support Home** (your screenshot) shows **template/asset violations**, not full account health. Always confirm **Phone numbers → Connected** in WhatsApp Manager.

If status is **Limited** or **Disabled**, fix policy issues before submitting more templates.

---

## 1. Account registration confirmation (Utility)

Use after a salon owner **completes signup** on Antrahq. Triggered by user action; no offers, pricing, trials, or subscription language.

### Fields to enter in WhatsApp Manager

| Field | Value |
|-------|--------|
| **Template name** | `antrahq_account_registered_v4` |
| **Category** | **Utility** |
| **Language** | English |

### Body (copy exactly)

```
Hello {{1}},

Your Antrahq account registration is complete.

Registered email: {{2}}
Salon or business name: {{3}}

You can sign in with the password you created during signup. If you did not register, please ignore this message.

Thank you.
```

### Sample values (required for review)

| Variable | Example |
|----------|---------|
| `{{1}}` | Priya |
| `{{2}}` | priya@blisssalon.in |
| `{{3}}` | Bliss Salon & Spa |

### Buttons

None (optional). Avoid URL buttons on first submission if you have prior Commerce Policy rejections.

### Do not include

- Free trial, plan, subscription, upgrade, or pricing text
- “Book now”, discounts, or marketing CTAs
- Links to signup/pricing pages in body or header
- Variables at the start or end of the message (body ends with “Thank you.”)

---

## 2. Signup OTP (Authentication) — preferred for new accounts

If the message sends a **one-time code** during signup, do **not** use Utility. Use Meta’s preset:

1. WhatsApp Manager → **Message templates → Create template**
2. Category: **Authentication**
3. Choose **One-time password** (copy-code button)
4. Name: `antrahq_signup_otp_v1`
5. Body uses Meta’s fixed OTP format; parameter = the code only

Authentication templates are reviewed under identity-verification rules, not Commerce Policy for digital subscriptions.

---

## 3. Appointment confirmation (Utility)

For the other rejected template (`appointment_confir...`). User booked an appointment; message confirms details only.

| Field | Value |
|-------|--------|
| **Template name** | `salon_appointment_confirmed_v2` |
| **Category** | **Utility** |
| **Language** | English |

### Body

```
Hello {{1}},

Your appointment at {{2}} is confirmed.

Date: {{3}}
Time: {{4}}
Service: {{5}}

Please arrive a few minutes early. To change your appointment, contact the salon.

Thank you.
```

### Sample values

| Variable | Example |
|----------|---------|
| `{{1}}` | Amit |
| `{{2}}` | Bliss Salon — Koramangala |
| `{{3}}` | 20 Aug 2026 |
| `{{4}}` | 4:30 PM |
| `{{5}}` | Haircut & beard trim |

### Backend wiring

| Env var | Default | When it fires |
|---------|---------|---------------|
| `MSG91_APPOINTMENT_CONFIRMED_TEMPLATE` | `salon_appointment_confirmed_v2` | `POST /api/v1/public/book/.../appointments` after OTP verification |
| `PUBLIC_FRONTEND_BASE_URL` | `http://localhost:3000` | Manage/book links in API responses (prod: `https://book.antrahq.com`) |

Customer must have **WhatsApp opt-in** (`whatsappOptIn=true`) — set automatically on online booking signup.

---

## Why `account_creation_confirmation_antrahq_3` may have failed

Meta flagged **Commerce Policy**. Common causes for SaaS account messages:

1. Wording about **plans, trials, subscriptions, or digital accounts**
2. **Marketing** content in a Utility template (promos, “get started today”)
3. **Generic** body with vague placeholders
4. Business portfolio category mismatch with message content

The replacement templates above are **transactional**, **user-initiated**, and **non-promotional**.

---

## Submission checklist

- [ ] Phone number status = **Connected** in WhatsApp Manager
- [ ] New template name (not the rejected one)
- [ ] Category matches content (Utility vs Authentication)
- [ ] Sample values filled for every `{{n}}`
- [ ] Body does not start or end with a variable
- [ ] No promotional or subscription language
- [ ] Only message users who **opted in** to WhatsApp from your product

After approval, add template names to MSG91 / env if wired in the app (`MSG91_*_TEMPLATE` vars in [MSG91_SETUP.md](./MSG91_SETUP.md)).
