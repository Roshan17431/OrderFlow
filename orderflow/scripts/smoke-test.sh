#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-240}"
GATEWAY_URL="http://localhost:${API_GATEWAY_PORT:-8080}"
AUTH_URL="http://localhost:${AUTH_SERVICE_PORT:-8081}"
PRODUCT_URL="http://localhost:${PRODUCT_SERVICE_PORT:-8082}"
CART_URL="http://localhost:${CART_SERVICE_PORT:-8083}"
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

json_path() {
  local file="$1"
  local path="$2"
  if command -v jq >/dev/null 2>&1; then
    jq -r --arg path "${path}" \
      'getpath($path | split(".") | map(if test("^[0-9]+$") then tonumber else . end)) // empty' \
      "${file}"
  elif command -v python3 >/dev/null 2>&1; then
    python3 - "${file}" "${path}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    value = json.load(source)
for segment in sys.argv[2].split("."):
    value = value[int(segment)] if segment.isdigit() else value[segment]
if value is not None:
    print(str(value).lower() if isinstance(value, bool) else value)
PY
  else
    fail "nested JSON checks require jq or python3"
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
  local jar="${2:-${COOKIE_JAR}}"
  awk -v expected="${name}" \
    'NF >= 7 && $6 == expected {value=$7} END {print value}' "${jar}"
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
wait_for_url "product service" "${PRODUCT_URL}/actuator/health/readiness"
wait_for_url "cart service" "${CART_URL}/actuator/health/readiness"
wait_for_url "API gateway" "${GATEWAY_URL}/actuator/health/readiness"
wait_for_eureka_app "AUTH-SERVICE"
wait_for_eureka_app "PRODUCT-SERVICE"
wait_for_eureka_app "CART-SERVICE"
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

# Phase 3: promote the already registered local smoke user without creating a
# public role-assignment endpoint, then issue a fresh JWT containing VENDOR.
VENDOR_USER_ID="$(json_path "${TEMP_DIR}/register.json" "user.id")"
[[ "${VENDOR_USER_ID}" =~ ^[0-9a-fA-F-]{36}$ ]] ||
  fail "registered vendor user ID was invalid"
docker compose exec -T postgres sh -c \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "$1"' \
  sh \
  "INSERT INTO user_roles (user_id, role) VALUES ('${VENDOR_USER_ID}'::uuid, 'VENDOR') ON CONFLICT DO NOTHING" \
  >/dev/null

# Logout invalidates the prior browser session, so establish a fresh
# double-submit CSRF cookie/header pair before the next login.
VENDOR_CSRF_STATUS="$(request GET "/api/auth/csrf" \
  "${TEMP_DIR}/vendor-csrf.json" "${TEMP_DIR}/vendor-csrf.headers" \
  -c "${COOKIE_JAR}" -b "${COOKIE_JAR}")"
assert_status "${VENDOR_CSRF_STATUS}" "200" "vendor login CSRF token"
CSRF_TOKEN="$(json_value "${TEMP_DIR}/vendor-csrf.json" "token")"

VENDOR_LOGIN_BODY="$(printf \
  '{"email":"%s","password":"Smoke password 123!"}' "${TEST_EMAIL}")"
VENDOR_LOGIN_STATUS="$(request POST "/api/auth/login" \
  "${TEMP_DIR}/vendor-login.json" "${TEMP_DIR}/vendor-login.headers" \
  -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
  --data "${VENDOR_LOGIN_BODY}")"
assert_status "${VENDOR_LOGIN_STATUS}" "200" "vendor login after local role promotion"
VENDOR_TOKEN="$(json_value "${TEMP_DIR}/vendor-login.json" "accessToken")"
[[ "${VENDOR_TOKEN}" == *.*.* ]] || fail "vendor access token was invalid"

CATEGORIES_STATUS="$(request GET "/api/categories" \
  "${TEMP_DIR}/categories.json" "${TEMP_DIR}/categories.headers" \
  -H "X-Correlation-ID: product-correlation-smoke")"
assert_status "${CATEGORIES_STATUS}" "200" "seeded category listing"
CATEGORY_ID="$(json_path "${TEMP_DIR}/categories.json" "items.0.id")"
[[ "${CATEGORY_ID}" =~ ^[0-9a-fA-F-]{36}$ ]] ||
  fail "seeded category ID was invalid"
[[ "$(header_value "${TEMP_DIR}/categories.headers" "X-Correlation-ID")" == \
    "product-correlation-smoke" ]] ||
  fail "product-service correlation ID was not preserved"

PRODUCT_SKU="SMOKE-${UNIQUE_SUFFIX}"
PRODUCT_SLUG="smoke-product-${UNIQUE_SUFFIX}"
CREATE_PRODUCT_BODY="$(printf \
  '{"sku":"%s","name":"Smoke Keyboard","description":"Phase 3 smoke product","priceAmount":49.99,"currency":"USD","categoryId":"%s","slug":"%s"}' \
  "${PRODUCT_SKU}" "${CATEGORY_ID}" "${PRODUCT_SLUG}")"
CREATE_PRODUCT_STATUS="$(request POST "/api/vendor/products" \
  "${TEMP_DIR}/product-create.json" "${TEMP_DIR}/product-create.headers" \
  -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "Authorization: Bearer ${VENDOR_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
  --data "${CREATE_PRODUCT_BODY}")"
assert_status "${CREATE_PRODUCT_STATUS}" "201" "vendor product creation"
PRODUCT_ID="$(json_value "${TEMP_DIR}/product-create.json" "id")"
[[ "$(json_value "${TEMP_DIR}/product-create.json" "status")" == "DRAFT" ]] ||
  fail "new product was not DRAFT"

DRAFT_PUBLIC_STATUS="$(request GET "/api/products/${PRODUCT_ID}" \
  "${TEMP_DIR}/draft-public.json" "${TEMP_DIR}/draft-public.headers")"
assert_status "${DRAFT_PUBLIC_STATUS}" "404" "private draft product"
[[ "$(json_value "${TEMP_DIR}/draft-public.json" "code")" == "PRODUCT_NOT_FOUND" ]] ||
  fail "draft privacy did not use PRODUCT_NOT_FOUND"

ACTIVATE_STATUS="$(request PATCH "/api/vendor/products/${PRODUCT_ID}/activate" \
  "${TEMP_DIR}/product-active.json" "${TEMP_DIR}/product-active.headers" \
  -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "Authorization: Bearer ${VENDOR_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
  --data '{"version":0}')"
assert_status "${ACTIVATE_STATUS}" "200" "product activation"
ACTIVE_VERSION="$(json_path "${TEMP_DIR}/product-active.json" "version")"

for attempt in 1 2; do
  PUBLIC_STATUS="$(request GET "/api/products/${PRODUCT_ID}" \
    "${TEMP_DIR}/product-public-${attempt}.json" \
    "${TEMP_DIR}/product-public-${attempt}.headers")"
  assert_status "${PUBLIC_STATUS}" "200" "public active product retrieval ${attempt}"
  [[ "$(json_value "${TEMP_DIR}/product-public-${attempt}.json" "currency")" == "USD" ]] ||
    fail "public product currency was not USD"
done

# A separate customer owns the cart.
CUSTOMER_COOKIE_JAR="${TEMP_DIR}/customer-cookies.txt"
CUSTOMER_CSRF_STATUS="$(request GET "/api/auth/csrf" \
  "${TEMP_DIR}/customer-csrf.json" "${TEMP_DIR}/customer-csrf.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}")"
assert_status "${CUSTOMER_CSRF_STATUS}" "200" "customer CSRF token"
CUSTOMER_CSRF="$(json_value "${TEMP_DIR}/customer-csrf.json" "token")"
CUSTOMER_EMAIL="cart-${UNIQUE_SUFFIX}@example.com"
CUSTOMER_REGISTER_BODY="$(printf \
  '{"email":"%s","password":"Smoke password 123!","displayName":"Cart Customer"}' \
  "${CUSTOMER_EMAIL}")"
CUSTOMER_REGISTER_STATUS="$(request POST "/api/auth/register" \
  "${TEMP_DIR}/customer-register.json" "${TEMP_DIR}/customer-register.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  --data "${CUSTOMER_REGISTER_BODY}")"
assert_status "${CUSTOMER_REGISTER_STATUS}" "201" "separate customer registration"
CUSTOMER_TOKEN="$(json_value "${TEMP_DIR}/customer-register.json" "accessToken")"

ADD_CART_STATUS="$(request POST "/api/cart/items" \
  "${TEMP_DIR}/cart-add.json" "${TEMP_DIR}/cart-add.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  -H "X-Correlation-ID: cart-product-correlation-smoke" \
  --data "{\"productId\":\"${PRODUCT_ID}\",\"quantity\":1}")"
assert_status "${ADD_CART_STATUS}" "200" "cart item addition"
[[ "$(header_value "${TEMP_DIR}/cart-add.headers" "X-Correlation-ID")" == \
    "cart-product-correlation-smoke" ]] ||
  fail "gateway/cart correlation ID was not preserved"
[[ "$(json_path "${TEMP_DIR}/cart-add.json" "items.0.productId")" == "${PRODUCT_ID}" ]] ||
  fail "cart product snapshot was missing"
[[ "$(json_path "${TEMP_DIR}/cart-add.json" "items.0.quantity")" == "1" ]] ||
  fail "cart quantity was incorrect"
[[ "$(json_value "${TEMP_DIR}/cart-add.json" "currency")" == "USD" ]] ||
  fail "cart currency was not USD"
[[ "$(json_path "${TEMP_DIR}/cart-add.json" "subtotal")" == "49.99" ]] ||
  fail "cart subtotal was incorrect"

CART_GET_STATUS="$(request GET "/api/cart" \
  "${TEMP_DIR}/cart-get.json" "${TEMP_DIR}/cart-get.headers" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}")"
assert_status "${CART_GET_STATUS}" "200" "stored cart read"

UPDATE_PRODUCT_BODY="$(printf \
  '{"name":"Smoke Keyboard","description":"Updated Phase 3 smoke product","priceAmount":59.99,"currency":"USD","categoryId":"%s","version":%s}' \
  "${CATEGORY_ID}" "${ACTIVE_VERSION}")"
UPDATE_PRODUCT_STATUS="$(request PUT "/api/vendor/products/${PRODUCT_ID}" \
  "${TEMP_DIR}/product-update.json" "${TEMP_DIR}/product-update.headers" \
  -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "Authorization: Bearer ${VENDOR_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
  --data "${UPDATE_PRODUCT_BODY}")"
assert_status "${UPDATE_PRODUCT_STATUS}" "200" "vendor price update"

VALIDATE_STATUS="$(request POST "/api/cart/validate" \
  "${TEMP_DIR}/cart-validate.json" "${TEMP_DIR}/cart-validate.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}")"
assert_status "${VALIDATE_STATUS}" "200" "cart validation"
[[ "$(json_path "${TEMP_DIR}/cart-validate.json" "issues.0.code")" == "PRICE_CHANGED" ]] ||
  fail "cart validation did not report PRICE_CHANGED"

QUANTITY_STATUS="$(request PATCH "/api/cart/items/${PRODUCT_ID}" \
  "${TEMP_DIR}/cart-quantity.json" "${TEMP_DIR}/cart-quantity.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  --data '{"quantity":2}')"
assert_status "${QUANTITY_STATUS}" "200" "cart quantity update"
[[ "$(json_path "${TEMP_DIR}/cart-quantity.json" "items.0.quantity")" == "2" ]] ||
  fail "cart quantity update was not stored"

REMOVE_STATUS="$(request DELETE "/api/cart/items/${PRODUCT_ID}" \
  "${TEMP_DIR}/cart-remove.json" "${TEMP_DIR}/cart-remove.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}")"
assert_status "${REMOVE_STATUS}" "200" "cart item removal"
[[ "$(json_path "${TEMP_DIR}/cart-remove.json" "distinctItemCount")" == "0" ]] ||
  fail "cart was not empty after final removal"

CUSTOMER_VENDOR_STATUS="$(request POST "/api/vendor/products" \
  "${TEMP_DIR}/customer-vendor.json" "${TEMP_DIR}/customer-vendor.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  --data "${CREATE_PRODUCT_BODY}")"
assert_status "${CUSTOMER_VENDOR_STATUS}" "403" "customer vendor denial"
[[ "$(json_value "${TEMP_DIR}/customer-vendor.json" "code")" == \
    "INSUFFICIENT_PERMISSIONS" ]] ||
  fail "customer vendor denial was not standardized"

ANONYMOUS_CART_STATUS="$(request GET "/api/cart" \
  "${TEMP_DIR}/anonymous-cart.json" "${TEMP_DIR}/anonymous-cart.headers" \
  -H "X-Correlation-ID: anonymous-cart-smoke")"
assert_status "${ANONYMOUS_CART_STATUS}" "401" "anonymous cart denial"
[[ "$(json_value "${TEMP_DIR}/anonymous-cart.json" "code")" == \
    "AUTHENTICATION_REQUIRED" ]] ||
  fail "anonymous cart denial was not standardized"

INVALID_QUANTITY_STATUS="$(request POST "/api/cart/items" \
  "${TEMP_DIR}/invalid-quantity.json" "${TEMP_DIR}/invalid-quantity.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  --data "{\"productId\":\"${PRODUCT_ID}\",\"quantity\":0}")"
assert_status "${INVALID_QUANTITY_STATUS}" "400" "invalid cart quantity"
[[ "$(json_value "${TEMP_DIR}/invalid-quantity.json" "code")" == "VALIDATION_ERROR" ]] ||
  fail "cart validation error was not standardized"

echo "Smoke tests passed:"
echo "  Phase 1 health, Eureka routing, metadata, and correlation checks passed."
echo "  Registration, JWT claims, refresh-cookie issuance, and current-user checks passed."
echo "  Customer access and admin denial passed."
echo "  Refresh rotation, reuse rejection, logout, and access revocation passed."
echo "  Product lifecycle, public cache reads, ownership, and category checks passed."
echo "  Cart snapshots, TTL-backed mutations, price validation, and security checks passed."
