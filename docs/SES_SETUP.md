# AWS SES setup (transactional email)

Password reset and future transactional mail use **Amazon SES** from the EC2 app host in `ap-south-1`.

## Overview

| Feature | When it fires |
|---------|----------------|
| Forgot password | User submits email on `/forgot-password` |

When `SES_ENABLED` is false or `SES_FROM_ADDRESS` is empty, reset requests still succeed but the reset URL is **logged on the backend** (dev-friendly).

## 1. Terraform (prod)

`module.ses_domain` verifies `antrahq.com` in SES and publishes DKIM CNAMEs in Route 53.

EC2 IAM allows `ses:SendEmail` for:

- `antrahq.com`
- `noreply@antrahq.com`

SSM parameters (written by Terraform):

| Parameter | Example |
|-----------|---------|
| `/salon-platform-prod/app/ses/from_address` | `noreply@antrahq.com` |
| `/salon-platform-prod/app/ses/enabled` | `true` |
| `/salon-platform-prod/app/public_frontend_base_url` | `https://app.antrahq.com` |

Apply infra, then re-render runtime env on EC2:

```bash
cd salon-platform-infra/environments/prod
terraform apply

# On EC2 via SSM Session Manager:
sudo /opt/salon-platform/scripts/render-runtime-env.sh
cd /opt/salon-platform && docker compose -f docker-compose.prod.yml up -d backend
```

## 2. SES production access

New AWS accounts start in the **SES sandbox** (only verified recipients receive mail).

1. AWS Console → Amazon SES → **Account dashboard**
2. Request **production access** (transactional / password reset)
3. Mention low volume (~100 emails/month) and `noreply@antrahq.com` sender

## 3. DNS (deliverability)

Terraform adds SES domain verification TXT and DKIM CNAMEs.

Ensure SPF on `antrahq.com` includes SES if you maintain a custom SPF record:

```txt
v=spf1 include:amazonses.com ~all
```

Add DMARC when ready:

```txt
_dmarc.antrahq.com TXT "v=DMARC1; p=none; rua=mailto:dmarc@antrahq.com"
```

## 4. Runtime env (docker)

| Env var | Purpose |
|---------|---------|
| `SES_ENABLED` | `true` to send via SES |
| `SES_FROM_ADDRESS` | Verified sender |
| `PUBLIC_FRONTEND_BASE_URL` | Base URL for reset links |
| `AWS_REGION` | `ap-south-1` |

## 5. API

```http
POST /api/v1/auth/forgot-password
{ "email": "manager@example.com" }

POST /api/v1/auth/reset-password
{ "token": "...", "password": "newpassword123" }
```

Both endpoints are public (no JWT). Forgot-password always returns success to avoid account enumeration.

## 6. Cost

Sending from EC2 includes **62,000 emails/month free** in the same region. At Antrhq’s current volume, expect **~$0/month**.

## 7. Local dev

Leave `SES_ENABLED=false`. Trigger forgot-password and read the reset URL from backend logs:

```
Password reset link for user@example.com (SES disabled): http://localhost:3000/reset-password?token=...
```
