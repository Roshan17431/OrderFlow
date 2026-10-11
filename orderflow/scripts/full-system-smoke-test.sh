#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

gateway_url="${GATEWAY_URL:-http://localhost:${API_GATEWAY_PORT:-8080}}"
temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/orderflow-full-smoke.XXXXXX")"
trap 'rm -rf "${temp_dir}"' EXIT

echo 'Running the Phase 1-7 end-to-end regression journey...'
./scripts/smoke-test.sh

status="$(curl --connect-timeout 3 --max-time 10 --silent --output "${temp_dir}/unauthorized.json" --write-out '%{http_code}' \
  "${gateway_url}/api/cart")"
[[ "${status}" == "401" ]] || {
  echo "Full smoke: expected unauthorized cart request to return 401, got ${status}"
  exit 1
}

status="$(curl --connect-timeout 3 --max-time 10 --silent --output "${temp_dir}/invalid-jwt.json" --write-out '%{http_code}' \
  -H 'Authorization: Bearer not-a-jwt' "${gateway_url}/api/orders")"
[[ "${status}" == "401" ]] || {
  echo "Full smoke: expected malformed JWT to return 401, got ${status}"
  exit 1
}

curl --connect-timeout 3 --max-time 10 --silent --dump-header "${temp_dir}/security.headers" \
  --output /dev/null "${gateway_url}/api/products"
for expected in \
  'X-Content-Type-Options: nosniff' \
  'X-Frame-Options: DENY' \
  'Referrer-Policy: no-referrer' \
  'Permissions-Policy:'; do
  grep -qi "^${expected}" "${temp_dir}/security.headers" || {
    echo "Full smoke: missing security header ${expected}"
    exit 1
  }
done

cors_status="$(curl --connect-timeout 3 --max-time 10 --silent --output /dev/null \
  --dump-header "${temp_dir}/cors-allowed.headers" --write-out '%{http_code}' \
  -X OPTIONS -H 'Origin: http://localhost:3000' \
  -H 'Access-Control-Request-Method: GET' "${gateway_url}/api/products")"
[[ "${cors_status}" == "200" ]] || {
  echo "Full smoke: configured CORS preflight returned ${cors_status}"
  exit 1
}
grep -qi '^Access-Control-Allow-Origin: http://localhost:3000' \
  "${temp_dir}/cors-allowed.headers" || {
  echo 'Full smoke: configured CORS origin was not returned exactly'
  exit 1
}
curl --connect-timeout 3 --max-time 10 --silent --output /dev/null --dump-header "${temp_dir}/cors-denied.headers" \
  -X OPTIONS -H 'Origin: https://untrusted.invalid' \
  -H 'Access-Control-Request-Method: GET' "${gateway_url}/api/products"
if grep -qi '^Access-Control-Allow-Origin:' "${temp_dir}/cors-denied.headers"; then
  echo 'Full smoke: unconfigured CORS origin received an allow-origin header'
  exit 1
fi

# The webhook policy has the highest local threshold. Sending invalid, unsigned
# payloads exercises the gateway limiter without changing payment state.
rate_status=''
for request_number in $(seq 1 105); do
  rate_status="$(curl --connect-timeout 3 --max-time 10 --silent \
    --dump-header "${temp_dir}/rate.headers" \
    --output "${temp_dir}/rate.json" \
    --write-out '%{http_code}' \
    -H 'Content-Type: application/json' \
    --data '{}' \
    "${gateway_url}/api/payments/webhooks/simulator")"
  [[ "${rate_status}" == "429" ]] && break
done
[[ "${rate_status}" == "429" ]] || {
  echo 'Full smoke: gateway webhook rate limit did not return 429'
  exit 1
}
grep -qi '^Retry-After:' "${temp_dir}/rate.headers" || {
  echo 'Full smoke: rate-limit response did not include Retry-After'
  exit 1
}
grep -q '"code":"RATE_LIMIT_EXCEEDED"' "${temp_dir}/rate.json" || {
  echo 'Full smoke: rate-limit response did not use the ApiError contract'
  exit 1
}

REQUIRE_LIVE_MONITORING=true ./scripts/validate-monitoring.sh

cat <<'SUMMARY'
OrderFlow Full System Smoke Test

Gateway                 PASS
Authentication          PASS
Products                PASS
Redis Cache             PASS
Cart                    PASS
Inventory               PASS
Orders                   PASS
Kafka                    PASS
Payments                PASS
Refunds                  PASS
Delivery                PASS
Notifications           PASS
Operations              PASS
Scheduling              PASS
Rate Limiting           PASS
Security                PASS
Outbox                  PASS
DLT                     PASS
Prometheus              PASS

Overall: PASS
SUMMARY
