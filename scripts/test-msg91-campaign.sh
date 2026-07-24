#!/bin/bash
# Verify MSG91 setup and run a WhatsApp + SMS campaign test against local backend.
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080}"
CEO_EMAIL="${CEO_EMAIL:-ceo@demo-brand.local}"
CEO_PASSWORD="${CEO_PASSWORD:-ceo123}"

: "${MSG91_AUTH_KEY:?Set MSG91_AUTH_KEY}"
: "${MSG91_WHATSAPP_NUMBER:?Set MSG91_WHATSAPP_NUMBER}"
: "${TEST_PHONE_1:?Set TEST_PHONE_1 (10-digit Indian mobile)}"
: "${TEST_PHONE_2:?Set TEST_PHONE_2 (10-digit Indian mobile)}"

echo "==> Checking MSG91 templates..."
curl -sS -H "authkey: $MSG91_AUTH_KEY" \
  "https://api.msg91.com/api/v5/whatsapp/get-template-client/${MSG91_WHATSAPP_NUMBER}?template_name=salon_promo" | head -c 500
echo ""

login_resp=$(curl -sS -X POST "$API_BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$CEO_EMAIL\",\"password\":\"$CEO_PASSWORD\"}")
TOKEN=$(echo "$login_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])" 2>/dev/null || true)
if [ -z "$TOKEN" ]; then
  echo "Login failed: $login_resp"
  exit 1
fi
AUTH="Authorization: Bearer $TOKEN"

echo "==> Creating test customers..."
for phone in "$TEST_PHONE_1" "$TEST_PHONE_2"; do
  curl -sS -X POST "$API_BASE/api/v1/customers" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"name\":\"Test User $phone\",\"phone\":\"$phone\",\"society\":\"Mantri Lithos\",\"flatUnit\":\"T-A-101\"}" \
    || echo "(customer $phone may already exist)"
done

echo "==> WhatsApp campaign..."
wa_create=$(curl -sS -X POST "$API_BASE/api/v1/campaigns" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"name\":\"WA Test $(date +%H%M)\",\"channel\":\"WHATSAPP\",\"messageText\":\"20% off on hair spa this weekend!\",\"filterPhone\":\"$TEST_PHONE_1\"}")
WA_ID=$(echo "$wa_create" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
curl -sS -X POST "$API_BASE/api/v1/campaigns/$WA_ID/send" -H "$AUTH"
echo "WhatsApp campaign sent: $WA_ID"

echo "==> SMS campaign..."
sms_create=$(curl -sS -X POST "$API_BASE/api/v1/campaigns" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"name\":\"SMS Test $(date +%H%M)\",\"channel\":\"SMS\",\"messageText\":\"20% off on hair spa this weekend!\",\"filterPhone\":\"$TEST_PHONE_2\"}")
SMS_ID=$(echo "$sms_create" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
curl -sS -X POST "$API_BASE/api/v1/campaigns/$SMS_ID/send" -H "$AUTH"
echo "SMS campaign sent: $SMS_ID"

echo "==> Done. Check phones $TEST_PHONE_1 (WA) and $TEST_PHONE_2 (SMS)."
