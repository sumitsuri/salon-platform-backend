# Google Places — local development

Production uses an API key that is **restricted to EC2 egress IPs**. Calls from your laptop fail with **403** / `API_KEY_IP_ADDRESS_BLOCKED`.

## Root cause

The backend calls Google **server-side** (`places.googleapis.com`). Google sees your home/office public IP, not the allowed production server IP.

## Fix options (pick one)

### A. Unrestricted dev key (recommended for daily local work)

1. In [Google Cloud Console → Credentials](https://console.cloud.google.com/apis/credentials), create a **second** API key (Places API (New) enabled).
2. Restrict it to **Places API** only; use **None** for application restrictions, or IP restrict to your dev IP only.
3. Store in SSM:

   ```bash
   aws ssm put-parameter --name /salon-platform-prod/google/places_api_key_local \
     --type SecureString --value 'YOUR_DEV_KEY' --overwrite --region ap-south-1
   ```

4. Run `bash scripts/sync-local-google-places-key.sh` and restart the backend.

`application.yml` prefers `GOOGLE_PLACES_API_KEY_LOCAL` over `GOOGLE_PLACES_API_KEY`.

### B. Allowlist your IP on the production key

1. Console → Credentials → your production key → **IP addresses**.
2. Add your current IPv4 (e.g. from `curl -4 ifconfig.me`) as `/32`.
3. Keep using the synced prod key; no proxy needed.

### C. Prod proxy (same prod key, no GCP IP changes)

After the internal proxy is **deployed to production**:

1. Generate a secret: `openssl rand -hex 32`
2. Store in SSM (prod + local sync):

   ```bash
   PLACES_INTERNAL_PROXY_SECRET='...' bash salon-platform-infra/scripts/setup-google-places-ssm.sh
   ```

3. Redeploy backend so prod has `PLACES_INTERNAL_PROXY_SECRET` in runtime env.
4. `bash scripts/sync-local-google-places-key.sh` sets `APP_GOOGLE_PLACES_INTERNAL_PROXY_ENABLED=true`.
5. Restart local backend.

Local calls go to `https://api.antrahq.com/api/v1/internal/places/*`; production calls Google from an allowed IP.
