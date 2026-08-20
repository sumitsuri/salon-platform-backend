# MSG91 setup (WhatsApp + SMS)

Transactional **email** (password reset) uses AWS SES — see [SES_SETUP.md](./SES_SETUP.md). This guide covers MSG91 only.

## Overview

| Feature | Channel | When it fires |
|---------|---------|---------------|
| Bill receipt with PDF | WhatsApp | After walk-in payment (`completePayment`) |
| Appointment confirmation | WhatsApp | After online booking (`createAppointment`) |
| Marketing campaigns | WhatsApp or SMS | CEO sends from `/admin/campaigns` |

When `MSG91_AUTH_KEY` is empty, messaging is **disabled** — payments and campaigns still work; delivery logs show `SKIPPED`.

## 1. MSG91 account

1. Sign up at [msg91.com](https://msg91.com) and complete KYC.
2. Create an **auth key** (API section).
3. Connect **WhatsApp Business** (Meta verification required).
4. Register **DLT** for SMS (~₹5,000 one-time in India).

## 2. WhatsApp templates

Create and get Meta approval for two templates in the MSG91 dashboard:

### `bill_receipt` (utility)

- **Header:** Document (dynamic URL — PDF link)
- **Body:** `Hi {{1}}, thank you for visiting {{2}}! Your invoice {{3}} for Rs.{{4}} is attached.`
- **Variables:** `{{1}}` customer name, `{{2}}` tenant brand name, `{{3}}` invoice #, `{{4}}` amount

### `salon_appointment_confirmed_v2` (utility)

Online booking confirmation — see [WHATSAPP_META_TEMPLATES.md](./WHATSAPP_META_TEMPLATES.md) §3 for Meta-approved body copy.

- **Body:** `Hello {{1}}, Your appointment at {{2}} is confirmed. Date: {{3}} Time: {{4}} Service: {{5}} …`
- **Variables:** customer name, branch label (brand · branch), date, time, service name

### `salon_promo` (marketing)

- **Body:** `Hi {{1}}, {{2}} Book your next visit at {{3}} today!`
- **Variables:** `{{1}}` customer name, `{{2}}` offer text, `{{3}}` tenant brand name

One approved template set works for **all salon chains** on the platform; brand name comes from each tenant record, not hardcoded copy.

Template names must match env vars (defaults: `bill_receipt`, `salon_promo`).

## 3. SMS flow

1. Create a DLT-approved promotional template in MSG91.
2. Build a **Flow** with variables `VAR1` (customer name) and `VAR2` (promo text).
3. Note the **flow_id** and **sender id**.

## 4. SSM parameters (production)

Store secrets in AWS SSM Parameter Store under your prefix (e.g. `/salon-platform-prod/`):

| Parameter | Example |
|-----------|---------|
| `msg91/auth_key` | `your-auth-key` |
| `msg91/whatsapp_number` | `919876543210` |
| `msg91/bill_receipt_template` | `bill_receipt` |
| `msg91/appointment_confirmed_template` | `salon_appointment_confirmed_v2` |
| `msg91/promo_template` | `salon_promo` |
| `msg91/promo_sms_flow_id` | `flow-id-from-dashboard` |
| `msg91/sms_sender` | `SALONX` |

```bash
aws ssm put-parameter --name "/salon-platform-prod/msg91/auth_key" \
  --type SecureString --value "YOUR_KEY" --region ap-south-1
# ... repeat for other params
```

Re-run bootstrap on EC2 (or redeploy backend) to pick up new values.

## 5. Local development

```bash
export MSG91_AUTH_KEY=your-key
export MSG91_WHATSAPP_NUMBER=919876543210
export MSG91_PROMO_SMS_FLOW_ID=your-flow-id
export MSG91_SMS_SENDER=SALONX
export MSG91_APPOINTMENT_CONFIRMED_TEMPLATE=salon_appointment_confirmed_v2
export PUBLIC_FRONTEND_BASE_URL=http://localhost:3000
export PUBLIC_URL=http://localhost:8080
```

## 6. Bill PDF public URL

WhatsApp document headers need a **publicly reachable HTTPS URL**. Today the app uses a signed link:

```
{PUBLIC_URL}/api/v1/public/invoices/{id}/pdf?token=...
```

- Token expires in 30 minutes.
- On HTTP + IP (current prod), delivery may work for testing but Meta often requires HTTPS.
- Plan a custom domain + TLS (or S3 presigned URLs) before high-volume production use.

## 7. API endpoints

| Method | Path | Role |
|--------|------|------|
| `GET` | `/api/v1/customers` | Filtered customer list (campaign cohorts) |
| `GET/POST` | `/api/v1/campaigns` | List / create campaigns |
| `POST` | `/api/v1/campaigns/preview` | Count matching customers |
| `POST` | `/api/v1/campaigns/{id}/send` | Dispatch campaign |
| `GET` | `/api/v1/public/invoices/{id}/pdf?token=` | Public PDF for WhatsApp |

## 8. Estimated monthly cost

~800 bill WhatsApps + 2,000 marketing + 2,000 SMS ≈ **₹2,200–6,000/month** at current scale (marketing WhatsApp dominates).
