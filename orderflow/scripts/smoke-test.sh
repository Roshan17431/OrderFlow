#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-240}"
GATEWAY_URL="http://localhost:${API_GATEWAY_PORT:-8080}"
AUTH_URL="http://localhost:${AUTH_SERVICE_PORT:-8081}"
EUREKA_URL="http://localhost:${EUREKA_PORT:-8761}"
KNOWN_ID="orderflow-smoke-test-123"
TEMP_DIR="$(mktemp -d)"
COOKIE_JAR="${TEMP_DIR}/cookies.txt"
trap 'rm -rf "${TEMP_DIR}"' EXIT

cd "${REPOSITORY_ROOT}"

fail() {
  echo "Smoke test failed: $*" >&2
  exit 1
}

wait_for_url() {
  local name="$1"
  local url="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  until curl --fail --silent --show-error "${url}" >/dev/null; do
    if (( SECONDS >= deadline )); then
      docker compose ps >&2 || true
      fail "timed out waiting for ${name}: ${url}"
    fi
    sleep 2
  done
  echo "Healthy: ${name}"
}

wait_for_eureka_app() {
  local application_name="$1"
  local output_file="${TEMP_DIR}/eureka-${application_name}.json"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  until curl --fail --silent --show-error \
      -H "Accept: application/json" \
      "${EUREKA_URL}/eureka/apps/${application_name}" >"${output_file}" 2>/dev/null \
      && grep -q '"status":"UP"' "${output_file}"; do
    if (( SECONDS >= deadline )); then
      docker compose ps >&2 || true
      fail "timed out waiting for ${application_name} to register UP in Eureka"
    fi
    sleep 2
  done
  echo "Registered UP: ${application_name}"
}

json_value() {
  local file="$1"
  local key="$2"
  if command -v jq >/dev/null 2>&1; then
    jq -r --arg key "${key}" '.[$key] // empty' "${file}"
  else
    sed -n "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p" "${file}" |
      head -n 1
  fi
}

header_value() {
  local file="$1"
  local name="$2"
  awk -F ': *' -v expected="${name}" \
    'tolower($1) == tolower(expected) {sub("\r$", "", $2); print $2}' "${file}" |
    tail -n 1
}

cookie_value() {
  local name="$1"
  awk -v expected="${name}" \
    'NF >= 7 && $6 == expected {value=$7} END {print value}' "${COOKIE_JAR}"
}

request() {
  local method="$1"
  local path="$2"
  local output="$3"
  local headers="$4"
  shift 4
  curl --silent --show-error \
    -X "${method}" \
    -D "${headers}" \
    -o "${output}" \
    -w "%{http_code}" \
    "$@" \
    "${GATEWAY_URL}${path}"
}

assert_status() {
  local actual="$1"
  local expected="$2"
  local operation="$3"
  [[ "${actual}" == "${expected}" ]] ||
    fail "${operation} returned HTTP ${actual}; expected ${expected}"
}

wait_for_url "service registry" "${EUREKA_URL}/actuator/health/readiness"
wait_for_url "auth service" "${AUTH_URL}/actuator/health/readiness"
wait_for_url "API gateway" "${GATEWAY_URL}/actuator/health/readiness"
wait_for_eureka_app "AUTH-SERVICE"
wait_for_eureka_app "API-GATEWAY"

# Preserve Phase 1 platform checks.
PING_STATUS="$(request GET "/api/platform/ping" \
  "${TEMP_DIR}/ping.json" "${TEMP_DIR}/ping.headers" \
  -H "Accept: application/json" -H "X-Correlation-ID: ${KNOWN_ID}")"
assert_status "${PING_STATUS}" "200" "platform ping"
[[ "$(header_value "${TEMP_DIR}/ping.headers" "X-Correlation-ID")" == "${KNOWN_ID}" ]] ||
  fail "supplied correlation ID was not preserved"
[[ "$(json_value "${TEMP_DIR}/ping.json" "correlationId")" == "${KNOWN_ID}" ]] ||
  fail "correlation ID was not propagated into the ping response"

INFO_STATUS="$(request GET "/api/platform/info" \
  "${TEMP_DIR}/info.json" "${TEMP_DIR}/info.headers" \
  -H "Accept: application/json")"
assert_status "${INFO_STATUS}" "200" "platform info"
[[ "$(json_value "${TEMP_DIR}/info.json" "schemaVersion")" == "1" ]] ||
  fail "database-backed platform metadata was not returned"

# Obtain a browser-style CSRF cookie and token.
CSRF_STATUS="$(request GET "/api/auth/csrf" \
  "${TEMP_DIR}/csrf.json" "${TEMP_DIR}/csrf.headers" \
  -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" -H "Accept: application/json")"
assert_status "${CSRF_STATUS}" "200" "CSRF token request"
CSRF_TOKEN="$(json_value "${TEMP_DIR}/csrf.json" "token")"
[[ -n "${CSRF_TOKEN}" ]] || fail "CSRF token was empty"

UNIQUE_SUFFIX="$(date +%s)-$$"
TEST_EMAIL="smoke-${UNIQUE_SUFFIX}@example.com"
REGISTER_BODY="$(printf \
  '{"email":"%s","password":"Smoke password 123!","displayName":"Smoke User"}' \
  "${TEST_EMAIL}")"
REGISTER_STATUS="$(request POST "/api/auth/register" \
  "${TEMP_DIR}/register.json" "${TEMP_DIR}/register.headers" \
  -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
  -H "X-Correlation-ID: ${KNOWN_ID}" \
  --data "${REGISTER_BODY}")"
assert_status "${REGISTER_STATUS}" "201" "registration"
ACCESS_TOKEN="$(json_value "${TEMP_DIR}/register.json" "accessToken")"
OLD_REFRESH_TOKEN="$(cookie_value "ORDERFLOW_REFRESH")"
[[ "${ACCESS_TOKEN}" == *.*.* ]] || fail "access token is not a three-part JWT"
[[ -n "${OLD_REFRESH_TOKEN}" ]] || fail "refresh cookie was not captured"
[[ "$(header_value "${TEMP_DIR}/register.headers" "X-Correlation-ID")" == "${KNOWN_ID}" ]] ||
  fail "registration correlation ID was not preserved"

# Decode only the JWT payload structure, never print it.
JWT_PAYLOAD="${ACCESS_TOKEN#*.}"
JWT_PAYLOAD="${JWT_PAYLOAD%%.*}"
JWT_PAYLOAD_PADDED="${JWT_PAYLOAD//-/+}"
JWT_PAYLOAD_PADDED="${JWT_PAYLOAD_PADDED//_/\/}"
while (( ${#JWT_PAYLOAD_PADDED} % 4 != 0 )); do
  JWT_PAYLOAD_PADDED="${JWT_PAYLOAD_PADDED}="
done
if printf '%s' "${JWT_PAYLOAD_PADDED}" | base64 --decode \
    >"${TEMP_DIR}/jwt-payload.json" 2>/dev/null \
    || printf '%s' "${JWT_PAYLOAD_PADDED}" | base64 -D \
      >"${TEMP_DIR}/jwt-payload.json" 2>/dev/null; then
  grep -q '"roles"' "${TEMP_DIR}/jwt-payload.json" ||
    fail "JWT payload did not contain roles"
  grep -q '"session_id"' "${TEMP_DIR}/jwt-payload.json" ||
    fail "JWT payload did not contain session_id"
  grep -q '"token_version"' "${TEMP_DIR}/jwt-payload.json" ||
    fail "JWT payload did not contain token_version"
else
  fail "JWT payload could not be decoded with standard base64"
fi

ME_STATUS="$(request GET "/api/auth/me" \
  "${TEMP_DIR}/me.json" "${TEMP_DIR}/me.headers" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")"
assert_status "${ME_STATUS}" "200" "current-user endpoint"
[[ "$(json_value "${TEMP_DIR}/me.json" "email")" == "${TEST_EMAIL}" ]] ||
  fail "current-user endpoint returned the wrong email"

SECURE_PING_STATUS="$(request GET "/api/platform/secure-ping" \
  "${TEMP_DIR}/secure-ping.json" "${TEMP_DIR}/secure-ping.headers" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")"
assert_status "${SECURE_PING_STATUS}" "200" "secure platform ping"

CUSTOMER_STATUS="$(request GET "/api/security/customer" \
  "${TEMP_DIR}/customer.json" "${TEMP_DIR}/customer.headers" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")"
assert_status "${CUSTOMER_STATUS}" "200" "customer authorization"
ADMIN_STATUS="$(request GET "/api/security/admin" \
  "${TEMP_DIR}/admin.json" "${TEMP_DIR}/admin.headers" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "X-Correlation-ID: admin-denied-smoke")"
assert_status "${ADMIN_STATUS}" "403" "admin authorization"
[[ "$(json_value "${TEMP_DIR}/admin.json" "code")" == "INSUFFICIENT_PERMISSIONS" ]] ||
  fail "admin denial did not use the standard security error"

REFRESH_STATUS="$(request POST "/api/auth/refresh" \
  "${TEMP_DIR}/refresh.json" "${TEMP_DIR}/refresh.headers" \
  -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "X-XSRF-TOKEN: ${CSRF_TOKEN}")"
assert_status "${REFRESH_STATUS}" "200" "refresh"
REFRESHED_ACCESS_TOKEN="$(json_value "${TEMP_DIR}/refresh.json" "accessToken")"
NEW_REFRESH_TOKEN="$(cookie_value "ORDERFLOW_REFRESH")"
[[ -n "${REFRESHED_ACCESS_TOKEN}" && "${REFRESHED_ACCESS_TOKEN}" != "${ACCESS_TOKEN}" ]] ||
  fail "access token did not change during refresh"
[[ -n "${NEW_REFRESH_TOKEN}" && "${NEW_REFRESH_TOKEN}" != "${OLD_REFRESH_TOKEN}" ]] ||
  fail "refresh cookie did not rotate"

REUSE_STATUS="$(request POST "/api/auth/refresh" \
  "${TEMP_DIR}/reuse.json" "${TEMP_DIR}/reuse.headers" \
  -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
  -H "Cookie: XSRF-TOKEN=${CSRF_TOKEN}; ORDERFLOW_REFRESH=${OLD_REFRESH_TOKEN}")"
assert_status "${REUSE_STATUS}" "401" "old refresh-token reuse"

LOGOUT_STATUS="$(request POST "/api/auth/logout" \
  "${TEMP_DIR}/logout.json" "${TEMP_DIR}/logout.headers" \
  -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "Authorization: Bearer ${REFRESHED_ACCESS_TOKEN}" \
  -H "X-XSRF-TOKEN: ${CSRF_TOKEN}")"
assert_status "${LOGOUT_STATUS}" "204" "logout"

REVOKED_STATUS="$(request GET "/api/auth/me" \
  "${TEMP_DIR}/revoked.json" "${TEMP_DIR}/revoked.headers" \
  -H "Authorization: Bearer ${REFRESHED_ACCESS_TOKEN}" \
  -H "X-Correlation-ID: revoked-smoke")"
assert_status "${REVOKED_STATUS}" "401" "revoked access-token check"
[[ "$(header_value "${TEMP_DIR}/revoked.headers" "X-Correlation-ID")" == "revoked-smoke" ]] ||
  fail "authentication-failure correlation ID was not preserved"

echo "Smoke tests passed:"
echo "  Phase 1 health, Eureka routing, metadata, and correlation checks passed."
echo "  Registration, JWT claims, refresh-cookie issuance, and current-user checks passed."
echo "  Customer access and admin denial passed."
echo "  Refresh rotation, reuse rejection, logout, and access revocation passed."
