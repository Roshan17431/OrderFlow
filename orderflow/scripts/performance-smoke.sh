#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

gateway_url="${GATEWAY_URL:-http://localhost:${API_GATEWAY_PORT:-8080}}"
auth_url="${AUTH_URL:-http://localhost:${AUTH_SERVICE_PORT:-8081}}"
concurrency="${PERF_CONCURRENCY:-20}"
requests_per_endpoint="${PERF_REQUESTS_PER_ENDPOINT:-25}"
temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/orderflow-performance.XXXXXX")"
trap 'rm -rf "${temp_dir}"' EXIT

cookie_jar="${temp_dir}/cookies.txt"
curl --fail --silent -c "${cookie_jar}" -b "${cookie_jar}" \
  "${auth_url}/api/auth/csrf" >"${temp_dir}/csrf.json"
csrf_token="$(jq -r '.token' "${temp_dir}/csrf.json")"
email="performance-$(date +%s)-$$@example.com"
register_body="$(printf \
  '{"email":"%s","password":"Performance password 123!","displayName":"Performance Smoke"}' \
  "${email}")"
curl --fail --silent -c "${cookie_jar}" -b "${cookie_jar}" \
  -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: ${csrf_token}" \
  --data "${register_body}" \
  "${auth_url}/api/auth/register" >"${temp_dir}/register.json"
access_token="$(jq -r '.accessToken' "${temp_dir}/register.json")"
[[ "${access_token}" == *.*.* ]] || {
  echo 'Performance smoke: could not obtain an access token'
  exit 1
}

export ORDERFLOW_PERF_TOKEN="${access_token}"
export ORDERFLOW_PERF_GATEWAY="${gateway_url}"

request_once() {
  local path="$1"
  curl --silent --output /dev/null \
    --max-time 10 \
    --write-out '%{http_code} %{time_total}\n' \
    -H "Authorization: Bearer ${ORDERFLOW_PERF_TOKEN}" \
    "${ORDERFLOW_PERF_GATEWAY}${path}"
}
export -f request_once

{
  for path in /api/products /api/cart /api/orders /api/notifications; do
    for _ in $(seq 1 "${requests_per_endpoint}"); do
      echo "${path}"
    done
  done
} | xargs -n 1 -P "${concurrency}" bash -c 'request_once "$1"' _ \
  >"${temp_dir}/results.txt"

total="$(wc -l <"${temp_dir}/results.txt" | tr -d ' ')"
success="$(awk '$1 >= 200 && $1 < 400 {count++} END {print count+0}' "${temp_dir}/results.txt")"
failure=$((total - success))
sort -n -k2 "${temp_dir}/results.txt" >"${temp_dir}/sorted.txt"

percentile() {
  local percent="$1"
  local row=$(( (total * percent + 99) / 100 ))
  awk -v row="${row}" 'NR == row {printf "%.3f", $2 * 1000}' "${temp_dir}/sorted.txt"
}

printf 'OrderFlow performance smoke: total=%d success=%d failure=%d p50=%sms p95=%sms p99=%sms concurrency=%s\n' \
  "${total}" "${success}" "${failure}" \
  "$(percentile 50)" "$(percentile 95)" "$(percentile 99)" "${concurrency}"

[[ "${failure}" == "0" ]] || exit 1
