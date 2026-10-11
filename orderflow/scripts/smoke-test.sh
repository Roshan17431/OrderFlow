#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-240}"
GATEWAY_URL="http://localhost:${API_GATEWAY_PORT:-8080}"
AUTH_URL="http://localhost:${AUTH_SERVICE_PORT:-8081}"
PRODUCT_URL="http://localhost:${PRODUCT_SERVICE_PORT:-8082}"
CART_URL="http://localhost:${CART_SERVICE_PORT:-8083}"
INVENTORY_URL="http://localhost:${INVENTORY_SERVICE_PORT:-8084}"
ORDER_URL="http://localhost:${ORDER_SERVICE_PORT:-8085}"
PAYMENT_URL="http://localhost:${PAYMENT_SERVICE_PORT:-8086}"
DELIVERY_URL="http://localhost:${DELIVERY_SERVICE_PORT:-8087}"
NOTIFICATION_URL="http://localhost:${NOTIFICATION_SERVICE_PORT:-8088}"
OPERATIONS_URL="http://localhost:${OPERATIONS_SERVICE_PORT:-8089}"
EUREKA_URL="http://localhost:${EUREKA_PORT:-8761}"
KNOWN_ID="orderflow-smoke-test-123"
TEMP_DIR="$(mktemp -d)"
COOKIE_JAR="${TEMP_DIR}/cookies.txt"
KAFKA_PAUSED=false
cleanup() {
  if [[ "${KAFKA_PAUSED}" == "true" ]]; then docker compose unpause kafka >/dev/null || true; fi
  rm -rf "${TEMP_DIR}"
}
trap cleanup EXIT

cd "${REPOSITORY_ROOT}"

fail() {
  echo "Smoke test failed: $*" >&2
  exit 1
}

wait_for_url() {
  local name="$1"
  local url="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  until curl --connect-timeout 3 --max-time 10 --fail --silent --show-error "${url}" >/dev/null; do
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
  until curl --connect-timeout 3 --max-time 10 --fail --silent --show-error \
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
  curl --connect-timeout 3 --max-time "${SMOKE_HTTP_TIMEOUT_SECONDS:-30}" --silent --show-error \
    -X "${method}" \
    -D "${headers}" \
    -o "${output}" \
    -w "%{http_code}" \
    "$@" \
    "${GATEWAY_URL}${path}"
}

simulator_signature() {
  local timestamp="$1"
  local body="$2"
  local secret="${APP_PAYMENT_SIMULATOR_WEBHOOK_SECRET:-orderflow-local-simulator-secret-not-for-production}"
  printf '%s.%s' "${timestamp}" "${body}" |
    openssl dgst -sha256 -hmac "${secret}" |
    awk '{print $NF}'
}

signed_webhook() {
  local event_id="$1"
  local body="$2"
  local output="$3"
  local headers="$4"
  local timestamp
  local signature
  timestamp="$(date +%s)"
  signature="$(simulator_signature "${timestamp}" "${body}")"
  request POST "/api/payments/webhooks/simulator" "${output}" "${headers}" \
    -H "Content-Type: application/json" \
    -H "X-Payment-Timestamp: ${timestamp}" \
    -H "X-Payment-Event-ID: ${event_id}" \
    -H "X-Payment-Signature: ${signature}" \
    --data-binary "${body}"
}

assert_status() {
  local actual="$1"
  local expected="$2"
  local operation="$3"
  [[ "${actual}" == "${expected}" ]] ||
    fail "${operation} returned HTTP ${actual}; expected ${expected}"
}

wait_for_order_status() {
  local token="$1"
  local order_id="$2"
  local expected="$3"
  local label="$4"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local status
  while true; do
    status="$(request GET "/api/orders/${order_id}" \
      "${TEMP_DIR}/${label}.json" "${TEMP_DIR}/${label}.headers" \
      -H "Authorization: Bearer ${token}")"
    if [[ "${status}" == "200" ]] &&
        [[ "$(json_value "${TEMP_DIR}/${label}.json" "status")" == "${expected}" ]]; then
      return
    fi
    if (( SECONDS >= deadline )); then
      fail "timed out waiting for order ${order_id} to reach ${expected}"
    fi
    sleep 1
  done
}

wait_for_payment_status() {
  local token="$1"
  local order_id="$2"
  local expected="$3"
  local label="$4"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local status
  while true; do
    status="$(request GET "/api/payments/orders/${order_id}" \
      "${TEMP_DIR}/${label}.json" "${TEMP_DIR}/${label}.headers" \
      -H "Authorization: Bearer ${token}")"
    if [[ "${status}" == "200" ]] &&
        [[ "$(json_value "${TEMP_DIR}/${label}.json" "status")" == "${expected}" ]]; then
      return
    fi
    if (( SECONDS >= deadline )); then
      fail "timed out waiting for order ${order_id} payment to reach ${expected}"
    fi
    sleep 1
  done
}

wait_for_fulfillment_status() {
  local token="$1" order_id="$2" expected="$3" label="$4"
  local deadline=$((SECONDS + TIMEOUT_SECONDS)) status
  while true; do
    status="$(request GET "/api/orders/${order_id}" \
      "${TEMP_DIR}/${label}.json" "${TEMP_DIR}/${label}.headers" \
      -H "Authorization: Bearer ${token}")"
    if [[ "${status}" == "200" ]] &&
        [[ "$(json_value "${TEMP_DIR}/${label}.json" "fulfillmentStatus")" == "${expected}" ]]; then
      return
    fi
    (( SECONDS < deadline )) || fail "timed out waiting for fulfillment ${expected}"
    sleep 1
  done
}

wait_for_delivery_status() {
  local token="$1" order_id="$2" expected="$3" label="$4"
  local deadline=$((SECONDS + TIMEOUT_SECONDS)) status
  while true; do
    status="$(request GET "/api/deliveries/orders/${order_id}" \
      "${TEMP_DIR}/${label}.json" "${TEMP_DIR}/${label}.headers" \
      -H "Authorization: Bearer ${token}")"
    if [[ "${status}" == "200" ]] &&
        [[ "$(json_value "${TEMP_DIR}/${label}.json" "status")" == "${expected}" ]]; then
      return
    fi
    (( SECONDS < deadline )) || fail "timed out waiting for delivery ${expected}"
    sleep 1
  done
}

wait_for_agent_availability() {
  local token="$1" expected="$2" label="$3"
  local deadline=$((SECONDS + TIMEOUT_SECONDS)) status
  while true; do
    status="$(request GET "/api/delivery/agents/me" \
      "${TEMP_DIR}/${label}.json" "${TEMP_DIR}/${label}.headers" \
      -H "Authorization: Bearer ${token}")"
    if [[ "${status}" == "200" ]] &&
        [[ "$(json_value "${TEMP_DIR}/${label}.json" "availability")" == "${expected}" ]]; then
      return
    fi
    (( SECONDS < deadline )) || fail "timed out waiting for agent ${expected}"
    sleep 1
  done
}

wait_for_inventory_quantities() {
  local token="$1"
  local product_id="$2"
  local on_hand="$3"
  local reserved="$4"
  local available="$5"
  local label="$6"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local status
  while true; do
    status="$(request GET "/api/inventory/products/${product_id}" \
      "${TEMP_DIR}/${label}.json" "${TEMP_DIR}/${label}.headers" \
      -H "Authorization: Bearer ${token}")"
    if [[ "${status}" == "200" ]] &&
        [[ "$(json_value "${TEMP_DIR}/${label}.json" "onHandQuantity")" == "${on_hand}" ]] &&
        [[ "$(json_value "${TEMP_DIR}/${label}.json" "reservedQuantity")" == "${reserved}" ]] &&
        [[ "$(json_value "${TEMP_DIR}/${label}.json" "availableQuantity")" == "${available}" ]]; then
      return
    fi
    if (( SECONDS >= deadline )); then
      fail "timed out waiting for expected inventory quantities"
    fi
    sleep 1
  done
}

kafka_topic_end_offset() {
  local topic="$1"
  docker compose exec -T kafka \
    /opt/kafka/bin/kafka-get-offsets.sh \
    --bootstrap-server localhost:29092 \
    --topic "${topic}" 2>/dev/null |
    awk -F: '{total += $3} END {print total + 0}'
}

wait_for_url "service registry" "${EUREKA_URL}/actuator/health/readiness"
wait_for_url "auth service" "${AUTH_URL}/actuator/health/readiness"
wait_for_url "product service" "${PRODUCT_URL}/actuator/health/readiness"
wait_for_url "cart service" "${CART_URL}/actuator/health/readiness"
wait_for_url "inventory service" "${INVENTORY_URL}/actuator/health/readiness"
wait_for_url "order service" "${ORDER_URL}/actuator/health/readiness"
wait_for_url "payment service" "${PAYMENT_URL}/actuator/health/readiness"
wait_for_url "delivery service" "${DELIVERY_URL}/actuator/health/readiness"
wait_for_url "notification service" "${NOTIFICATION_URL}/actuator/health/readiness"
wait_for_url "operations service" "${OPERATIONS_URL}/actuator/health/readiness"
wait_for_url "API gateway" "${GATEWAY_URL}/actuator/health/readiness"
wait_for_eureka_app "AUTH-SERVICE"
wait_for_eureka_app "PRODUCT-SERVICE"
wait_for_eureka_app "CART-SERVICE"
wait_for_eureka_app "INVENTORY-SERVICE"
wait_for_eureka_app "ORDER-SERVICE"
wait_for_eureka_app "PAYMENT-SERVICE"
wait_for_eureka_app "DELIVERY-SERVICE"
wait_for_eureka_app "NOTIFICATION-SERVICE"
wait_for_eureka_app "OPERATIONS-SERVICE"
wait_for_eureka_app "API-GATEWAY"

ORDER_DLT_OFFSET_BEFORE="$(kafka_topic_end_offset "orderflow.order-events.v1.DLT")"
INVENTORY_DLT_OFFSET_BEFORE="$(kafka_topic_end_offset "orderflow.inventory-events.v1.DLT")"
PAYMENT_DLT_OFFSET_BEFORE="$(kafka_topic_end_offset "orderflow.payment-events.v1.DLT")"
DELIVERY_DLT_OFFSET_BEFORE="$(kafka_topic_end_offset "orderflow.delivery-events.v1.DLT")"
NOTIFICATION_DLT_OFFSET_BEFORE="$(kafka_topic_end_offset "orderflow.notification-events.v1.DLT")"

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
SHIPPING_ADDRESS_JSON=',"shippingAddress":{"recipientName":"Smoke Recipient","addressLine1":"10 Example Street","city":"Kochi","stateOrRegion":"Kerala","postalCode":"682001","countryCode":"IN"}'
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
CUSTOMER_ID="$(json_path "${TEMP_DIR}/customer-register.json" "user.id")"

PREFERENCE_STATUS="$(request PUT "/api/notifications/preferences" \
  "${TEMP_DIR}/notification-preference.json" \
  "${TEMP_DIR}/notification-preference.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  --data "{\"emailEnabled\":true,\"emailAddress\":\"${CUSTOMER_EMAIL}\",\"smsEnabled\":false}")"
assert_status "${PREFERENCE_STATUS}" "200" "notification preference update"

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

# Phase 4: durable order creation and the real Kafka inventory workflow.
ADJUST_STATUS="$(request POST "/api/inventory/products/${PRODUCT_ID}/adjustments" \
  "${TEMP_DIR}/inventory-adjust.json" "${TEMP_DIR}/inventory-adjust.headers" \
  -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "Authorization: Bearer ${VENDOR_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
  -H "X-Correlation-ID: phase4-stock-smoke" \
  --data '{"quantityDelta":10,"reason":"SMOKE_TEST_STOCK","reference":"phase4"}')"
assert_status "${ADJUST_STATUS}" "200" "initial stock adjustment"
[[ "$(json_value "${TEMP_DIR}/inventory-adjust.json" "onHandQuantity")" == "10" ]] ||
  fail "on-hand stock was not 10"
[[ "$(json_value "${TEMP_DIR}/inventory-adjust.json" "reservedQuantity")" == "0" ]] ||
  fail "initial reserved stock was not zero"
[[ "$(json_value "${TEMP_DIR}/inventory-adjust.json" "availableQuantity")" == "10" ]] ||
  fail "initial available stock was not 10"
[[ "$(header_value "${TEMP_DIR}/inventory-adjust.headers" "X-Correlation-ID")" == \
    "phase4-stock-smoke" ]] ||
  fail "inventory correlation ID was not preserved"

CUSTOMER_ADJUST_STATUS="$(request POST \
  "/api/inventory/products/${PRODUCT_ID}/adjustments" \
  "${TEMP_DIR}/customer-inventory-adjust.json" \
  "${TEMP_DIR}/customer-inventory-adjust.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  --data '{"quantityDelta":1,"reason":"DENIED"}')"
assert_status "${CUSTOMER_ADJUST_STATUS}" "403" "customer inventory adjustment denial"
[[ "$(json_value "${TEMP_DIR}/customer-inventory-adjust.json" "code")" == \
    "INSUFFICIENT_PERMISSIONS" ]] ||
  fail "customer inventory denial was not standardized"

PHASE4_CART_STATUS="$(request POST "/api/cart/items" \
  "${TEMP_DIR}/phase4-cart.json" "${TEMP_DIR}/phase4-cart.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  --data "{\"productId\":\"${PRODUCT_ID}\",\"quantity\":3}")"
assert_status "${PHASE4_CART_STATUS}" "200" "Phase 4 cart creation"
PHASE4_CART_VERSION="$(json_value "${TEMP_DIR}/phase4-cart.json" "version")"
[[ "${PHASE4_CART_VERSION}" =~ ^[0-9]+$ ]] ||
  fail "Phase 4 cart version was invalid"

CREATE_ORDER_KEY="create-${UNIQUE_SUFFIX}"
CREATE_ORDER_STATUS="$(request POST "/api/orders" \
  "${TEMP_DIR}/order-create.json" "${TEMP_DIR}/order-create.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  -H "Idempotency-Key: ${CREATE_ORDER_KEY}" \
  -H "X-Correlation-ID: phase4-order-smoke" \
  --data "{\"expectedCartVersion\":${PHASE4_CART_VERSION}${SHIPPING_ADDRESS_JSON}}")"
assert_status "${CREATE_ORDER_STATUS}" "202" "order creation"
ORDER_ID="$(json_value "${TEMP_DIR}/order-create.json" "id")"
[[ "${ORDER_ID}" =~ ^[0-9a-fA-F-]{36}$ ]] || fail "created order ID was invalid"
[[ "$(header_value "${TEMP_DIR}/order-create.headers" "X-Correlation-ID")" == \
    "phase4-order-smoke" ]] ||
  fail "order correlation ID was not preserved"
wait_for_order_status "${CUSTOMER_TOKEN}" "${ORDER_ID}" \
  "PAYMENT_PENDING" "order-payment-pending"
wait_for_inventory_quantities "${VENDOR_TOKEN}" "${PRODUCT_ID}" \
  "10" "3" "7" "inventory-reserved"

CREATE_ORDER_REPEAT_STATUS="$(request POST "/api/orders" \
  "${TEMP_DIR}/order-create-repeat.json" "${TEMP_DIR}/order-create-repeat.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  -H "Idempotency-Key: ${CREATE_ORDER_KEY}" \
  --data "{\"expectedCartVersion\":${PHASE4_CART_VERSION}${SHIPPING_ADDRESS_JSON}}")"
assert_status "${CREATE_ORDER_REPEAT_STATUS}" "202" "idempotent order replay"
[[ "$(json_value "${TEMP_DIR}/order-create-repeat.json" "id")" == "${ORDER_ID}" ]] ||
  fail "idempotent order replay returned a different order"

DIFFERENT_CART_VERSION=$((PHASE4_CART_VERSION + 1))
CREATE_ORDER_CONFLICT_STATUS="$(request POST "/api/orders" \
  "${TEMP_DIR}/order-create-conflict.json" "${TEMP_DIR}/order-create-conflict.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  -H "Idempotency-Key: ${CREATE_ORDER_KEY}" \
  --data "{\"expectedCartVersion\":${DIFFERENT_CART_VERSION}${SHIPPING_ADDRESS_JSON}}")"
assert_status "${CREATE_ORDER_CONFLICT_STATUS}" "409" "idempotency-key conflict"
[[ "$(json_value "${TEMP_DIR}/order-create-conflict.json" "code")" == \
    "ORDER_IDEMPOTENCY_CONFLICT" ]] ||
  fail "idempotency conflict was not standardized"

# A second customer asks for eight units while only seven are available.
CUSTOMER_TWO_COOKIE_JAR="${TEMP_DIR}/customer-two-cookies.txt"
CUSTOMER_TWO_CSRF_STATUS="$(request GET "/api/auth/csrf" \
  "${TEMP_DIR}/customer-two-csrf.json" "${TEMP_DIR}/customer-two-csrf.headers" \
  -c "${CUSTOMER_TWO_COOKIE_JAR}" -b "${CUSTOMER_TWO_COOKIE_JAR}")"
assert_status "${CUSTOMER_TWO_CSRF_STATUS}" "200" "second customer CSRF token"
CUSTOMER_TWO_CSRF="$(json_value "${TEMP_DIR}/customer-two-csrf.json" "token")"
CUSTOMER_TWO_EMAIL="order-two-${UNIQUE_SUFFIX}@example.com"
CUSTOMER_TWO_REGISTER_BODY="$(printf \
  '{"email":"%s","password":"Smoke password 123!","displayName":"Second Customer"}' \
  "${CUSTOMER_TWO_EMAIL}")"
CUSTOMER_TWO_REGISTER_STATUS="$(request POST "/api/auth/register" \
  "${TEMP_DIR}/customer-two-register.json" \
  "${TEMP_DIR}/customer-two-register.headers" \
  -c "${CUSTOMER_TWO_COOKIE_JAR}" -b "${CUSTOMER_TWO_COOKIE_JAR}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_TWO_CSRF}" \
  --data "${CUSTOMER_TWO_REGISTER_BODY}")"
assert_status "${CUSTOMER_TWO_REGISTER_STATUS}" "201" "second customer registration"
CUSTOMER_TWO_TOKEN="$(json_value "${TEMP_DIR}/customer-two-register.json" "accessToken")"
CUSTOMER_TWO_ID="$(json_path "${TEMP_DIR}/customer-two-register.json" "user.id")"

CUSTOMER_TWO_CART_STATUS="$(request POST "/api/cart/items" \
  "${TEMP_DIR}/customer-two-cart.json" "${TEMP_DIR}/customer-two-cart.headers" \
  -c "${CUSTOMER_TWO_COOKIE_JAR}" -b "${CUSTOMER_TWO_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TWO_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_TWO_CSRF}" \
  --data "{\"productId\":\"${PRODUCT_ID}\",\"quantity\":8}")"
assert_status "${CUSTOMER_TWO_CART_STATUS}" "200" "second customer cart"
CUSTOMER_TWO_CART_VERSION="$(json_value "${TEMP_DIR}/customer-two-cart.json" "version")"

CUSTOMER_TWO_ORDER_STATUS="$(request POST "/api/orders" \
  "${TEMP_DIR}/customer-two-order.json" "${TEMP_DIR}/customer-two-order.headers" \
  -c "${CUSTOMER_TWO_COOKIE_JAR}" -b "${CUSTOMER_TWO_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TWO_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_TWO_CSRF}" \
  -H "Idempotency-Key: create-two-${UNIQUE_SUFFIX}" \
  --data "{\"expectedCartVersion\":${CUSTOMER_TWO_CART_VERSION}${SHIPPING_ADDRESS_JSON}}")"
assert_status "${CUSTOMER_TWO_ORDER_STATUS}" "202" "insufficient-stock order creation"
CUSTOMER_TWO_ORDER_ID="$(json_value "${TEMP_DIR}/customer-two-order.json" "id")"
wait_for_order_status "${CUSTOMER_TWO_TOKEN}" "${CUSTOMER_TWO_ORDER_ID}" \
  "INVENTORY_REJECTED" "order-inventory-rejected"
wait_for_inventory_quantities "${VENDOR_TOKEN}" "${PRODUCT_ID}" \
  "10" "3" "7" "inventory-after-rejection"

CANCEL_KEY="cancel-${UNIQUE_SUFFIX}"
CANCEL_STATUS="$(request POST "/api/orders/${ORDER_ID}/cancel" \
  "${TEMP_DIR}/order-cancel.json" "${TEMP_DIR}/order-cancel.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  -H "Idempotency-Key: ${CANCEL_KEY}" \
  -H "X-Correlation-ID: phase4-cancel-smoke" \
  --data '{"reason":"CUSTOMER_REQUEST"}')"
assert_status "${CANCEL_STATUS}" "202" "order cancellation"
wait_for_order_status "${CUSTOMER_TOKEN}" "${ORDER_ID}" \
  "CANCELLED" "order-cancelled"
wait_for_inventory_quantities "${VENDOR_TOKEN}" "${PRODUCT_ID}" \
  "10" "0" "10" "inventory-released"

CANCEL_REPEAT_STATUS="$(request POST "/api/orders/${ORDER_ID}/cancel" \
  "${TEMP_DIR}/order-cancel-repeat.json" "${TEMP_DIR}/order-cancel-repeat.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  -H "Idempotency-Key: ${CANCEL_KEY}" \
  --data '{"reason":"CUSTOMER_REQUEST"}')"
assert_status "${CANCEL_REPEAT_STATUS}" "202" "idempotent cancellation replay"
[[ "$(json_value "${TEMP_DIR}/order-cancel-repeat.json" "id")" == "${ORDER_ID}" ]] ||
  fail "idempotent cancellation returned a different order"

# Phase 5: a fresh order is confirmed asynchronously and completed by an
# exact-byte signed simulator webhook.
PAYMENT_CART_STATUS="$(request GET "/api/cart" \
  "${TEMP_DIR}/payment-cart.json" "${TEMP_DIR}/payment-cart.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}")"
assert_status "${PAYMENT_CART_STATUS}" "200" "payment cart refresh"
PAYMENT_CART_VERSION="$(json_value "${TEMP_DIR}/payment-cart.json" "version")"
[[ "${PAYMENT_CART_VERSION}" =~ ^[0-9]+$ ]] ||
  fail "payment cart version was invalid"

PAYMENT_ORDER_STATUS="$(request POST "/api/orders" \
  "${TEMP_DIR}/payment-order.json" "${TEMP_DIR}/payment-order.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  -H "Idempotency-Key: payment-order-${UNIQUE_SUFFIX}" \
  --data "{\"expectedCartVersion\":${PAYMENT_CART_VERSION}${SHIPPING_ADDRESS_JSON}}")"
assert_status "${PAYMENT_ORDER_STATUS}" "202" "payment order creation"
PAYMENT_ORDER_ID="$(json_value "${TEMP_DIR}/payment-order.json" "id")"
wait_for_order_status "${CUSTOMER_TOKEN}" "${PAYMENT_ORDER_ID}" \
  "PAYMENT_PENDING" "payment-order-pending"
wait_for_payment_status "${CUSTOMER_TOKEN}" "${PAYMENT_ORDER_ID}" \
  "PENDING" "payment-pending"
PAYMENT_ID="$(json_value "${TEMP_DIR}/payment-pending.json" "id")"

NON_OWNER_PAYMENT_STATUS="$(request GET "/api/payments/orders/${PAYMENT_ORDER_ID}" \
  "${TEMP_DIR}/payment-non-owner.json" "${TEMP_DIR}/payment-non-owner.headers" \
  -H "Authorization: Bearer ${CUSTOMER_TWO_TOKEN}")"
assert_status "${NON_OWNER_PAYMENT_STATUS}" "404" "non-owner payment privacy"

CONFIRM_KEY="confirm-${UNIQUE_SUFFIX}"
CONFIRM_STATUS="$(request POST "/api/payments/orders/${PAYMENT_ORDER_ID}/confirm" \
  "${TEMP_DIR}/payment-confirm.json" "${TEMP_DIR}/payment-confirm.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  -H "Idempotency-Key: ${CONFIRM_KEY}" \
  --data '{"paymentMethodToken":"tok_success"}')"
assert_status "${CONFIRM_STATUS}" "202" "payment confirmation"
[[ "$(json_value "${TEMP_DIR}/payment-confirm.json" "status")" == "PROCESSING" ]] ||
  fail "successful simulator token did not enter PROCESSING"

CONFIRM_REPEAT_STATUS="$(request POST "/api/payments/orders/${PAYMENT_ORDER_ID}/confirm" \
  "${TEMP_DIR}/payment-confirm-repeat.json" \
  "${TEMP_DIR}/payment-confirm-repeat.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  -H "Idempotency-Key: ${CONFIRM_KEY}" \
  --data '{"paymentMethodToken":"tok_success"}')"
assert_status "${CONFIRM_REPEAT_STATUS}" "202" "payment confirmation replay"

INVALID_WEBHOOK_STATUS="$(request POST "/api/payments/webhooks/simulator" \
  "${TEMP_DIR}/invalid-webhook.json" "${TEMP_DIR}/invalid-webhook.headers" \
  -H "Content-Type: application/json" \
  -H "X-Payment-Timestamp: $(date +%s)" \
  -H "X-Payment-Event-ID: invalid-${UNIQUE_SUFFIX}" \
  -H "X-Payment-Signature: 00" \
  --data '{"type":"payment.succeeded"}')"
assert_status "${INVALID_WEBHOOK_STATUS}" "401" "invalid webhook signature"

PAYMENT_PROVIDER_ID="sim_pay_${PAYMENT_ID}"
PAYMENT_WEBHOOK_BODY="$(printf \
  '{"type":"payment.succeeded","providerPaymentId":"%s"}' \
  "${PAYMENT_PROVIDER_ID}")"
if [[ "${AUDIT_KAFKA_RECOVERY:-false}" == "true" ]]; then
  docker compose pause kafka >/dev/null
  KAFKA_PAUSED=true
  KAFKA_HEALTH_STATUS="$(curl --connect-timeout 3 --max-time 8 --silent --output /dev/null --write-out '%{http_code}' "${INVENTORY_URL}/actuator/health/readiness")"
  assert_status "${KAFKA_HEALTH_STATUS}" "503" "bounded Kafka outage readiness"
fi
PAYMENT_WEBHOOK_STATUS="$(signed_webhook \
  "payment-success-${UNIQUE_SUFFIX}" "${PAYMENT_WEBHOOK_BODY}" \
  "${TEMP_DIR}/payment-webhook.json" "${TEMP_DIR}/payment-webhook.headers")"
assert_status "${PAYMENT_WEBHOOK_STATUS}" "200" "signed payment success webhook"
PAYMENT_WEBHOOK_REPEAT_STATUS="$(signed_webhook \
  "payment-success-${UNIQUE_SUFFIX}" "${PAYMENT_WEBHOOK_BODY}" \
  "${TEMP_DIR}/payment-webhook-repeat.json" \
  "${TEMP_DIR}/payment-webhook-repeat.headers")"
assert_status "${PAYMENT_WEBHOOK_REPEAT_STATUS}" "200" "duplicate payment webhook"
if [[ "${KAFKA_PAUSED}" == "true" ]]; then
  RETRY_DEADLINE=$((SECONDS + 45))
  while true; do
    OUTBOX_RETRIES="$(docker compose exec -T postgres sh -c \
      'psql -U "$POSTGRES_USER" -d "$1" -Atc "$2"' sh "${PAYMENT_POSTGRES_DB:-orderflow_payment}" \
      "SELECT count(*) FROM outbox_event WHERE aggregate_id='${PAYMENT_ORDER_ID}' AND event_type='PAYMENT_SUCCEEDED' AND attempts > 0" | tr -d '[:space:]')"
    [[ "${OUTBOX_RETRIES}" -ge 1 ]] && break
    (( SECONDS < RETRY_DEADLINE )) || fail "Kafka outage did not record a publication retry"
    sleep 1
  done
  docker compose unpause kafka >/dev/null
  KAFKA_PAUSED=false
  echo 'Kafka outage: bounded readiness and persisted publication retry verified; checking recovery.'
fi
wait_for_order_status "${CUSTOMER_TOKEN}" "${PAYMENT_ORDER_ID}" \
  "PAID" "payment-order-paid"
wait_for_fulfillment_status "${CUSTOMER_TOKEN}" "${PAYMENT_ORDER_ID}" \
  "DELIVERY_PENDING" "payment-fulfillment-pending"
wait_for_delivery_status "${CUSTOMER_TOKEN}" "${PAYMENT_ORDER_ID}" \
  "PENDING_ASSIGNMENT" "delivery-pending"
DELIVERY_ID="$(json_value "${TEMP_DIR}/delivery-pending.json" "id")"

# Phase 6: create a local-only delivery agent, assign the oldest pending
# delivery, and drive the complete lifecycle through protected APIs.
AGENT_COOKIE_JAR="${TEMP_DIR}/agent-cookies.txt"
AGENT_CSRF_STATUS="$(request GET "/api/auth/csrf" \
  "${TEMP_DIR}/agent-csrf.json" "${TEMP_DIR}/agent-csrf.headers" \
  -c "${AGENT_COOKIE_JAR}" -b "${AGENT_COOKIE_JAR}")"
assert_status "${AGENT_CSRF_STATUS}" "200" "delivery-agent CSRF token"
AGENT_CSRF="$(json_value "${TEMP_DIR}/agent-csrf.json" "token")"
AGENT_EMAIL="agent-${UNIQUE_SUFFIX}@example.com"
AGENT_REGISTER_STATUS="$(request POST "/api/auth/register" \
  "${TEMP_DIR}/agent-register.json" "${TEMP_DIR}/agent-register.headers" \
  -c "${AGENT_COOKIE_JAR}" -b "${AGENT_COOKIE_JAR}" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: ${AGENT_CSRF}" \
  --data "{\"email\":\"${AGENT_EMAIL}\",\"password\":\"Smoke password 123!\",\"displayName\":\"Delivery Agent\"}")"
assert_status "${AGENT_REGISTER_STATUS}" "201" "delivery-agent registration"
AGENT_USER_ID="$(json_path "${TEMP_DIR}/agent-register.json" "user.id")"
docker compose exec -T postgres sh -c \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "$1"' \
  sh "INSERT INTO user_roles (user_id, role) VALUES ('${AGENT_USER_ID}'::uuid, 'DELIVERY_AGENT') ON CONFLICT DO NOTHING" \
  >/dev/null
AGENT_LOGIN_STATUS="$(request POST "/api/auth/login" \
  "${TEMP_DIR}/agent-login.json" "${TEMP_DIR}/agent-login.headers" \
  -c "${AGENT_COOKIE_JAR}" -b "${AGENT_COOKIE_JAR}" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: ${AGENT_CSRF}" \
  --data "{\"email\":\"${AGENT_EMAIL}\",\"password\":\"Smoke password 123!\"}")"
assert_status "${AGENT_LOGIN_STATUS}" "200" "delivery-agent login"
AGENT_TOKEN="$(json_value "${TEMP_DIR}/agent-login.json" "accessToken")"

CUSTOMER_AGENT_STATUS="$(request GET "/api/delivery/agents/me" \
  "${TEMP_DIR}/customer-agent-denied.json" "${TEMP_DIR}/customer-agent-denied.headers" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}")"
assert_status "${CUSTOMER_AGENT_STATUS}" "403" "customer delivery-agent denial"
CUSTOMER_ADMIN_DELIVERY_STATUS="$(request GET "/api/admin/deliveries/${DELIVERY_ID}/history" \
  "${TEMP_DIR}/customer-admin-delivery.json" "${TEMP_DIR}/customer-admin-delivery.headers" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}")"
assert_status "${CUSTOMER_ADMIN_DELIVERY_STATUS}" "403" "customer admin-delivery denial"
NON_OWNER_DELIVERY_STATUS="$(request GET "/api/deliveries/orders/${PAYMENT_ORDER_ID}" \
  "${TEMP_DIR}/delivery-non-owner.json" "${TEMP_DIR}/delivery-non-owner.headers" \
  -H "Authorization: Bearer ${CUSTOMER_TWO_TOKEN}")"
assert_status "${NON_OWNER_DELIVERY_STATUS}" "404" "non-owner delivery privacy"

AGENT_AVAILABLE_STATUS="$(request PUT "/api/delivery/agents/me/availability" \
  "${TEMP_DIR}/agent-available.json" "${TEMP_DIR}/agent-available.headers" \
  -c "${AGENT_COOKIE_JAR}" -b "${AGENT_COOKIE_JAR}" \
  -H "Authorization: Bearer ${AGENT_TOKEN}" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: ${AGENT_CSRF}" \
  -H "X-Correlation-ID: phase6-delivery-smoke" \
  --data '{"availability":"AVAILABLE"}')"
assert_status "${AGENT_AVAILABLE_STATUS}" "200" "delivery-agent availability"
wait_for_delivery_status "${CUSTOMER_TOKEN}" "${PAYMENT_ORDER_ID}" \
  "ASSIGNED" "delivery-assigned"
wait_for_agent_availability "${AGENT_TOKEN}" "BUSY" "agent-busy"
wait_for_fulfillment_status "${CUSTOMER_TOKEN}" "${PAYMENT_ORDER_ID}" \
  "DELIVERY_ASSIGNED" "fulfillment-assigned"

for transition in pickup out-for-delivery delivered; do
  TRANSITION_STATUS="$(request POST "/api/delivery/agents/me/deliveries/${DELIVERY_ID}/${transition}" \
    "${TEMP_DIR}/delivery-${transition}.json" "${TEMP_DIR}/delivery-${transition}.headers" \
    -c "${AGENT_COOKIE_JAR}" -b "${AGENT_COOKIE_JAR}" \
    -H "Authorization: Bearer ${AGENT_TOKEN}" -H "X-XSRF-TOKEN: ${AGENT_CSRF}")"
  assert_status "${TRANSITION_STATUS}" "200" "delivery ${transition} transition"
  case "${transition}" in
    pickup) EXPECTED_FULFILLMENT="PICKED_UP" ;;
    out-for-delivery) EXPECTED_FULFILLMENT="OUT_FOR_DELIVERY" ;;
    delivered) EXPECTED_FULFILLMENT="DELIVERED" ;;
  esac
  wait_for_fulfillment_status "${CUSTOMER_TOKEN}" "${PAYMENT_ORDER_ID}" \
    "${EXPECTED_FULFILLMENT}" "fulfillment-${transition}"
done
wait_for_inventory_quantities "${VENDOR_TOKEN}" "${PRODUCT_ID}" \
  7 0 7 "inventory-fulfilled"
wait_for_agent_availability "${AGENT_TOKEN}" "AVAILABLE" "agent-released"

# Leave the local smoke agent offline after verifying that delivery completion
# released it. This keeps a subsequent smoke run from auto-assigning the next
# delivery before that run can assert the PENDING_ASSIGNMENT boundary.
AGENT_OFFLINE_STATUS="$(request PUT "/api/delivery/agents/me/availability" \
  "${TEMP_DIR}/agent-offline.json" "${TEMP_DIR}/agent-offline.headers" \
  -c "${AGENT_COOKIE_JAR}" -b "${AGENT_COOKIE_JAR}" \
  -H "Authorization: Bearer ${AGENT_TOKEN}" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: ${AGENT_CSRF}" \
  --data '{"availability":"OFFLINE"}')"
assert_status "${AGENT_OFFLINE_STATUS}" "200" "delivery-agent smoke cleanup"
wait_for_agent_availability "${AGENT_TOKEN}" "OFFLINE" "agent-offline"

OTHER_AGENT_DELIVERY_STATUS="$(request GET "/api/delivery/agents/me/deliveries/00000000-0000-0000-0000-000000000001" \
  "${TEMP_DIR}/agent-other-delivery.json" "${TEMP_DIR}/agent-other-delivery.headers" \
  -H "Authorization: Bearer ${AGENT_TOKEN}")"
assert_status "${OTHER_AGENT_DELIVERY_STATUS}" "404" "agent other-delivery privacy"

# Persistent notifications must contain each expected event exactly once.
NOTIFICATION_DEADLINE=$((SECONDS + TIMEOUT_SECONDS))
while true; do
  NOTIFICATION_LIST_STATUS="$(request GET "/api/notifications?size=100" \
    "${TEMP_DIR}/notifications.json" "${TEMP_DIR}/notifications.headers" \
    -H "Authorization: Bearer ${CUSTOMER_TOKEN}")"
  if [[ "${NOTIFICATION_LIST_STATUS}" == "200" ]] &&
      grep -q '"type":"PAYMENT_SUCCEEDED"' "${TEMP_DIR}/notifications.json" &&
      grep -q '"type":"DELIVERY_ASSIGNED"' "${TEMP_DIR}/notifications.json" &&
      grep -q '"type":"DELIVERY_OUT_FOR_DELIVERY"' "${TEMP_DIR}/notifications.json" &&
      grep -q '"type":"DELIVERY_DELIVERED"' "${TEMP_DIR}/notifications.json"; then
    break
  fi
  (( SECONDS < NOTIFICATION_DEADLINE )) || fail "timed out waiting for Phase 6 notifications"
  sleep 1
done
for notification_type in PAYMENT_SUCCEEDED DELIVERY_ASSIGNED DELIVERY_OUT_FOR_DELIVERY DELIVERY_DELIVERED; do
  [[ "$(grep -o "\"type\":\"${notification_type}\"" "${TEMP_DIR}/notifications.json" | wc -l | tr -d ' ')" == "1" ]] ||
    fail "notification ${notification_type} was missing or duplicated"
done

UNREAD_STATUS="$(request GET "/api/notifications/unread-count" \
  "${TEMP_DIR}/unread-before.json" "${TEMP_DIR}/unread-before.headers" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}")"
assert_status "${UNREAD_STATUS}" "200" "notification unread count"
UNREAD_BEFORE="$(json_value "${TEMP_DIR}/unread-before.json" "count")"
FIRST_NOTIFICATION_ID="$(json_path "${TEMP_DIR}/notifications.json" "content.0.id")"
READ_ONE_STATUS="$(request PATCH "/api/notifications/${FIRST_NOTIFICATION_ID}/read" \
  "${TEMP_DIR}/read-one.json" "${TEMP_DIR}/read-one.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}")"
assert_status "${READ_ONE_STATUS}" "200" "mark notification read"
UNREAD_AFTER_ONE_STATUS="$(request GET "/api/notifications/unread-count" \
  "${TEMP_DIR}/unread-after-one.json" "${TEMP_DIR}/unread-after-one.headers" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}")"
assert_status "${UNREAD_AFTER_ONE_STATUS}" "200" "unread count after mark-read"
[[ "$(json_value "${TEMP_DIR}/unread-after-one.json" "count")" -lt "${UNREAD_BEFORE}" ]] ||
  fail "mark-read did not reduce unread count"
READ_ALL_STATUS="$(request PATCH "/api/notifications/read-all" \
  "${TEMP_DIR}/read-all.json" "${TEMP_DIR}/read-all.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}")"
assert_status "${READ_ALL_STATUS}" "200" "mark all notifications read"
[[ "$(json_value "${TEMP_DIR}/read-all.json" "count")" == "0" ]] ||
  fail "read-all did not clear unread count"

EMAIL_DEADLINE=$((SECONDS + TIMEOUT_SECONDS))
while true; do
  EMAIL_SENT_COUNT="$(docker compose exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$1" -Atc "$2"' sh \
    "${NOTIFICATION_POSTGRES_DB:-orderflow_notification}" \
    "SELECT count(*) FROM notification_delivery d JOIN notification n ON n.id=d.notification_id WHERE n.user_id='${CUSTOMER_ID}'::uuid AND d.channel='EMAIL' AND d.status='SENT'" | tr -d '[:space:]')"
  [[ "${EMAIL_SENT_COUNT}" -ge 1 ]] && break
  (( SECONDS < EMAIL_DEADLINE )) || fail "simulated email delivery did not reach SENT"
  sleep 1
done

CUSTOMER_REFUND_STATUS="$(request POST \
  "/api/admin/payments/${PAYMENT_ID}/refunds" \
  "${TEMP_DIR}/customer-refund.json" "${TEMP_DIR}/customer-refund.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  -H "Idempotency-Key: denied-refund-${UNIQUE_SUFFIX}" \
  --data '{"amount":1.00,"reason":"CUSTOMER_REQUEST"}')"
assert_status "${CUSTOMER_REFUND_STATUS}" "403" "customer refund denial"

# Promote the existing local vendor through the database-only smoke fixture,
# then obtain a fresh JWT containing ADMIN for the protected refund API.
docker compose exec -T postgres sh -c \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "$1"' \
  sh \
  "INSERT INTO user_roles (user_id, role) VALUES ('${VENDOR_USER_ID}'::uuid, 'ADMIN') ON CONFLICT DO NOTHING" \
  >/dev/null
ADMIN_LOGIN_STATUS="$(request POST "/api/auth/login" \
  "${TEMP_DIR}/admin-login.json" "${TEMP_DIR}/admin-login.headers" \
  -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
  --data "${VENDOR_LOGIN_BODY}")"
assert_status "${ADMIN_LOGIN_STATUS}" "200" "admin login after local role promotion"
ADMIN_TOKEN="$(json_value "${TEMP_DIR}/admin-login.json" "accessToken")"

OPERATIONS_ADMIN_STATUS="$(request GET "/api/admin/operations/overview" \
  "${TEMP_DIR}/operations-overview.json" "${TEMP_DIR}/operations-overview.headers" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")"
assert_status "${OPERATIONS_ADMIN_STATUS}" "200" "operations admin overview"
OPERATIONS_CUSTOMER_STATUS="$(request GET "/api/admin/operations/overview" \
  "${TEMP_DIR}/operations-denied.json" "${TEMP_DIR}/operations-denied.headers" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}")"
assert_status "${OPERATIONS_CUSTOMER_STATUS}" "403" "operations customer denial"
curl --connect-timeout 3 --max-time 10 --fail --silent --show-error "${OPERATIONS_URL}/actuator/prometheus" | \
  grep -q "operations_event_processing" || fail "operations Prometheus metrics missing"

REFUND_STATUS="$(request POST "/api/admin/payments/${PAYMENT_ID}/refunds" \
  "${TEMP_DIR}/refund.json" "${TEMP_DIR}/refund.headers" \
  -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
  -H "Idempotency-Key: refund-${UNIQUE_SUFFIX}" \
  --data '{"amount":1.00,"reason":"CUSTOMER_REQUEST"}')"
assert_status "${REFUND_STATUS}" "202" "partial refund request"
REFUND_ID="$(json_value "${TEMP_DIR}/refund.json" "id")"
REFUND_WEBHOOK_BODY="$(printf \
  '{"type":"refund.succeeded","providerRefundId":"sim_ref_%s"}' \
  "${REFUND_ID}")"
REFUND_WEBHOOK_STATUS="$(signed_webhook \
  "refund-success-${UNIQUE_SUFFIX}" "${REFUND_WEBHOOK_BODY}" \
  "${TEMP_DIR}/refund-webhook.json" "${TEMP_DIR}/refund-webhook.headers")"
assert_status "${REFUND_WEBHOOK_STATUS}" "200" "signed refund success webhook"
wait_for_order_status "${CUSTOMER_TOKEN}" "${PAYMENT_ORDER_ID}" \
  "PARTIALLY_REFUNDED" "payment-order-partially-refunded"

# Complete the remaining refund balance after delivery. Fulfilled stock must stay deducted.
REMAINING_REFUND="$(jq -r '.totalAmount' "${TEMP_DIR}/payment-order.json" | awk '{printf "%.2f", $1 - 1.00}')"
FULL_REFUND_STATUS="$(request POST "/api/admin/payments/${PAYMENT_ID}/refunds" \
  "${TEMP_DIR}/full-refund.json" "${TEMP_DIR}/full-refund.headers" \
  -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: ${CSRF_TOKEN}" \
  -H "Idempotency-Key: full-refund-${UNIQUE_SUFFIX}" \
  --data "{\"amount\":${REMAINING_REFUND},\"reason\":\"CUSTOMER_REQUEST\"}")"
assert_status "${FULL_REFUND_STATUS}" "202" "full refund request"
FULL_REFUND_ID="$(json_value "${TEMP_DIR}/full-refund.json" "id")"
FULL_REFUND_BODY="$(printf '{"type":"refund.succeeded","providerRefundId":"sim_ref_%s"}' "${FULL_REFUND_ID}")"
FULL_REFUND_WEBHOOK_STATUS="$(signed_webhook "full-refund-${UNIQUE_SUFFIX}" "${FULL_REFUND_BODY}" \
  "${TEMP_DIR}/full-refund-webhook.json" "${TEMP_DIR}/full-refund-webhook.headers")"
assert_status "${FULL_REFUND_WEBHOOK_STATUS}" "200" "full refund webhook"
wait_for_order_status "${CUSTOMER_TOKEN}" "${PAYMENT_ORDER_ID}" "REFUNDED" "payment-order-fully-refunded"
wait_for_inventory_quantities "${VENDOR_TOKEN}" "${PRODUCT_ID}" 7 0 7 "inventory-after-refund"

# A separate payment demonstrates the stable HTTP 402 decline and automatic
# inventory release while the order remains PAYMENT_FAILED.
DECLINE_CART_STATUS="$(request GET "/api/cart" \
  "${TEMP_DIR}/decline-cart.json" "${TEMP_DIR}/decline-cart.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}")"
assert_status "${DECLINE_CART_STATUS}" "200" "decline cart refresh"
DECLINE_CART_VERSION="$(json_value "${TEMP_DIR}/decline-cart.json" "version")"
[[ "${DECLINE_CART_VERSION}" =~ ^[0-9]+$ ]] ||
  fail "decline cart version was invalid"

DECLINE_ORDER_STATUS="$(request POST "/api/orders" \
  "${TEMP_DIR}/decline-order.json" "${TEMP_DIR}/decline-order.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  -H "Idempotency-Key: decline-order-${UNIQUE_SUFFIX}" \
  --data "{\"expectedCartVersion\":${DECLINE_CART_VERSION}${SHIPPING_ADDRESS_JSON}}")"
assert_status "${DECLINE_ORDER_STATUS}" "202" "decline order creation"
DECLINE_ORDER_ID="$(json_value "${TEMP_DIR}/decline-order.json" "id")"
wait_for_order_status "${CUSTOMER_TOKEN}" "${DECLINE_ORDER_ID}" \
  "PAYMENT_PENDING" "decline-order-pending"
wait_for_payment_status "${CUSTOMER_TOKEN}" "${DECLINE_ORDER_ID}" \
  "PENDING" "decline-payment-pending"
DECLINE_CONFIRM_STATUS="$(request POST \
  "/api/payments/orders/${DECLINE_ORDER_ID}/confirm" \
  "${TEMP_DIR}/decline-confirm.json" "${TEMP_DIR}/decline-confirm.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" \
  -H "Idempotency-Key: decline-confirm-${UNIQUE_SUFFIX}" \
  --data '{"paymentMethodToken":"tok_declined"}')"
assert_status "${DECLINE_CONFIRM_STATUS}" "402" "stable payment decline"
[[ "$(json_value "${TEMP_DIR}/decline-confirm.json" "code")" == "PAYMENT_DECLINED" ]] ||
  fail "payment decline did not use PAYMENT_DECLINED"
wait_for_order_status "${CUSTOMER_TOKEN}" "${DECLINE_ORDER_ID}" \
  "PAYMENT_FAILED" "decline-order-failed"

# Failure before pickup releases committed inventory and requests a compensation refund.
FAILURE_CART_STATUS="$(request GET "/api/cart" \
  "${TEMP_DIR}/failure-cart.json" "${TEMP_DIR}/failure-cart.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}")"
assert_status "${FAILURE_CART_STATUS}" "200" "delivery failure cart refresh"
FAILURE_CART_VERSION="$(json_value "${TEMP_DIR}/failure-cart.json" "version")"
FAILURE_ORDER_STATUS="$(request POST "/api/orders" \
  "${TEMP_DIR}/failure-order.json" "${TEMP_DIR}/failure-order.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" -H "Idempotency-Key: failure-order-${UNIQUE_SUFFIX}" \
  --data "{\"expectedCartVersion\":${FAILURE_CART_VERSION}${SHIPPING_ADDRESS_JSON}}")"
assert_status "${FAILURE_ORDER_STATUS}" "202" "delivery failure order creation"
FAILURE_ORDER_ID="$(json_value "${TEMP_DIR}/failure-order.json" "id")"
wait_for_order_status "${CUSTOMER_TOKEN}" "${FAILURE_ORDER_ID}" "PAYMENT_PENDING" "failure-order-pending"
wait_for_payment_status "${CUSTOMER_TOKEN}" "${FAILURE_ORDER_ID}" "PENDING" "failure-payment-pending"
FAILURE_PAYMENT_ID="$(json_value "${TEMP_DIR}/failure-payment-pending.json" "id")"
FAILURE_CONFIRM_STATUS="$(request POST "/api/payments/orders/${FAILURE_ORDER_ID}/confirm" \
  "${TEMP_DIR}/failure-confirm.json" "${TEMP_DIR}/failure-confirm.headers" \
  -c "${CUSTOMER_COOKIE_JAR}" -b "${CUSTOMER_COOKIE_JAR}" \
  -H "Authorization: Bearer ${CUSTOMER_TOKEN}" -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_CSRF}" -H "Idempotency-Key: failure-confirm-${UNIQUE_SUFFIX}" \
  --data '{"paymentMethodToken":"tok_success"}')"
assert_status "${FAILURE_CONFIRM_STATUS}" "202" "delivery failure payment confirmation"
FAILURE_PAYMENT_BODY="$(printf '{"type":"payment.succeeded","providerPaymentId":"sim_pay_%s"}' "${FAILURE_PAYMENT_ID}")"
FAILURE_WEBHOOK_STATUS="$(signed_webhook "failure-payment-${UNIQUE_SUFFIX}" "${FAILURE_PAYMENT_BODY}" \
  "${TEMP_DIR}/failure-webhook.json" "${TEMP_DIR}/failure-webhook.headers")"
assert_status "${FAILURE_WEBHOOK_STATUS}" "200" "delivery failure payment webhook"
wait_for_order_status "${CUSTOMER_TOKEN}" "${FAILURE_ORDER_ID}" "PAID" "failure-order-paid"
wait_for_delivery_status "${CUSTOMER_TOKEN}" "${FAILURE_ORDER_ID}" "PENDING_ASSIGNMENT" "failure-delivery-pending"
FAILURE_DELIVERY_ID="$(json_value "${TEMP_DIR}/failure-delivery-pending.json" "id")"
FAILURE_AGENT_STATUS="$(request PUT "/api/delivery/agents/me/availability" \
  "${TEMP_DIR}/failure-agent.json" "${TEMP_DIR}/failure-agent.headers" \
  -c "${AGENT_COOKIE_JAR}" -b "${AGENT_COOKIE_JAR}" \
  -H "Authorization: Bearer ${AGENT_TOKEN}" -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${AGENT_CSRF}" --data '{"availability":"AVAILABLE"}')"
assert_status "${FAILURE_AGENT_STATUS}" "200" "failure delivery agent availability"
wait_for_delivery_status "${CUSTOMER_TOKEN}" "${FAILURE_ORDER_ID}" "ASSIGNED" "failure-delivery-assigned"
FAILED_STATUS="$(request POST "/api/delivery/agents/me/deliveries/${FAILURE_DELIVERY_ID}/failed" \
  "${TEMP_DIR}/failed-delivery.json" "${TEMP_DIR}/failed-delivery.headers" \
  -c "${AGENT_COOKIE_JAR}" -b "${AGENT_COOKIE_JAR}" \
  -H "Authorization: Bearer ${AGENT_TOKEN}" -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${AGENT_CSRF}" --data '{"failureCode":"VEHICLE_PROBLEM"}')"
assert_status "${FAILED_STATUS}" "200" "delivery failure before pickup"
wait_for_fulfillment_status "${CUSTOMER_TOKEN}" "${FAILURE_ORDER_ID}" "DELIVERY_FAILED" "failure-fulfillment"
wait_for_inventory_quantities "${VENDOR_TOKEN}" "${PRODUCT_ID}" 7 0 7 "inventory-after-failure"
wait_for_agent_availability "${AGENT_TOKEN}" "AVAILABLE" "agent-after-failure"
# The simulator's refund is completed by its signed webhook, as with normal refunds.
COMPENSATION_DEADLINE=$((SECONDS + TIMEOUT_SECONDS))
while true; do
  COMPENSATION_REFUND_ID="$(docker compose exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$1" -Atc "$2"' sh "${PAYMENT_POSTGRES_DB:-orderflow_payment}" \
    "SELECT id FROM refund WHERE payment_id='${FAILURE_PAYMENT_ID}' AND reason_code='SYSTEM_COMPENSATION' LIMIT 1" | tr -d '[:space:]')"
  [[ -n "${COMPENSATION_REFUND_ID}" ]] && break
  (( SECONDS < COMPENSATION_DEADLINE )) || fail "compensation refund was not created"
  sleep 1
done
COMPENSATION_BODY="$(printf '{"type":"refund.succeeded","providerRefundId":"sim_ref_%s"}' "${COMPENSATION_REFUND_ID}")"
COMPENSATION_STATUS="$(signed_webhook "failure-compensation-${UNIQUE_SUFFIX}" "${COMPENSATION_BODY}" \
  "${TEMP_DIR}/compensation-webhook.json" "${TEMP_DIR}/compensation-webhook.headers")"
assert_status "${COMPENSATION_STATUS}" "200" "compensation refund webhook"
wait_for_order_status "${CUSTOMER_TOKEN}" "${FAILURE_ORDER_ID}" "REFUNDED" "failure-order-refunded"
AGENT_CLEANUP_STATUS="$(request PUT "/api/delivery/agents/me/availability" \
  "${TEMP_DIR}/failure-agent-offline.json" "${TEMP_DIR}/failure-agent-offline.headers" \
  -c "${AGENT_COOKIE_JAR}" -b "${AGENT_COOKIE_JAR}" \
  -H "Authorization: Bearer ${AGENT_TOKEN}" -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${AGENT_CSRF}" --data '{"availability":"OFFLINE"}')"
assert_status "${AGENT_CLEANUP_STATUS}" "200" "delivery failure agent cleanup"

OPERATIONS_DEADLINE=$((SECONDS + TIMEOUT_SECONDS))
while true; do
  OPERATIONS_FINAL_STATUS="$(request GET "/api/admin/operations/overview" \
    "${TEMP_DIR}/operations-final.json" "${TEMP_DIR}/operations-final.headers" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}")"
  if [[ "${OPERATIONS_FINAL_STATUS}" == "200" ]] && jq -e \
    '.ordersCreated >= 1 and .paymentsSucceeded >= 2 and .paymentsFailed >= 1 and .deliveriesCompleted >= 1 and .deliveriesFailed >= 1' \
    "${TEMP_DIR}/operations-final.json" >/dev/null; then break; fi
  (( SECONDS < OPERATIONS_DEADLINE )) || fail "operations read model did not reflect the completed journeys"
  sleep 1
done

# Promote the second local user after its customer order to verify product
# ownership enforcement through product-service rather than shared tables.
docker compose exec -T postgres sh -c \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "$1"' \
  sh \
  "INSERT INTO user_roles (user_id, role) VALUES ('${CUSTOMER_TWO_ID}'::uuid, 'VENDOR') ON CONFLICT DO NOTHING" \
  >/dev/null
CUSTOMER_TWO_LOGIN_BODY="$(printf \
  '{"email":"%s","password":"Smoke password 123!"}' "${CUSTOMER_TWO_EMAIL}")"
CUSTOMER_TWO_LOGIN_STATUS="$(request POST "/api/auth/login" \
  "${TEMP_DIR}/customer-two-login.json" "${TEMP_DIR}/customer-two-login.headers" \
  -c "${CUSTOMER_TWO_COOKIE_JAR}" -b "${CUSTOMER_TWO_COOKIE_JAR}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_TWO_CSRF}" \
  --data "${CUSTOMER_TWO_LOGIN_BODY}")"
assert_status "${CUSTOMER_TWO_LOGIN_STATUS}" "200" "second vendor login"
OTHER_VENDOR_TOKEN="$(json_value "${TEMP_DIR}/customer-two-login.json" "accessToken")"
OTHER_VENDOR_ADJUST_STATUS="$(request POST \
  "/api/inventory/products/${PRODUCT_ID}/adjustments" \
  "${TEMP_DIR}/other-vendor-adjust.json" \
  "${TEMP_DIR}/other-vendor-adjust.headers" \
  -c "${CUSTOMER_TWO_COOKIE_JAR}" -b "${CUSTOMER_TWO_COOKIE_JAR}" \
  -H "Authorization: Bearer ${OTHER_VENDOR_TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: ${CUSTOMER_TWO_CSRF}" \
  --data '{"quantityDelta":1,"reason":"NOT_OWNER"}')"
assert_status "${OTHER_VENDOR_ADJUST_STATUS}" "404" "other vendor inventory denial"

# Wait for both outboxes to drain, then inspect trace metadata and DLT offsets.
OUTBOX_DEADLINE=$((SECONDS + TIMEOUT_SECONDS))
while true; do
  ORDER_OUTBOX_PENDING="$(docker compose exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$1" -Atc "SELECT count(*) FROM outbox_event WHERE status <> '\''PUBLISHED'\''"' \
    sh "${ORDER_POSTGRES_DB:-orderflow_order}" | tr -d '[:space:]')"
  INVENTORY_OUTBOX_PENDING="$(docker compose exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$1" -Atc "SELECT count(*) FROM outbox_event WHERE status <> '\''PUBLISHED'\''"' \
    sh "${INVENTORY_POSTGRES_DB:-orderflow_inventory}" | tr -d '[:space:]')"
  PAYMENT_OUTBOX_PENDING="$(docker compose exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$1" -Atc "SELECT count(*) FROM outbox_event WHERE status <> '\''PUBLISHED'\''"' \
    sh "${PAYMENT_POSTGRES_DB:-orderflow_payment}" | tr -d '[:space:]')"
  DELIVERY_OUTBOX_PENDING="$(docker compose exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$1" -Atc "SELECT count(*) FROM outbox_event WHERE status <> '\''PUBLISHED'\''"' \
    sh "${DELIVERY_POSTGRES_DB:-orderflow_delivery}" | tr -d '[:space:]')"
  NOTIFICATION_OUTBOX_PENDING="$(docker compose exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$1" -Atc "SELECT count(*) FROM outbox_event WHERE status <> '\''PUBLISHED'\''"' \
    sh "${NOTIFICATION_POSTGRES_DB:-orderflow_notification}" | tr -d '[:space:]')"
  if [[ "${ORDER_OUTBOX_PENDING}" == "0" &&
        "${INVENTORY_OUTBOX_PENDING}" == "0" &&
        "${PAYMENT_OUTBOX_PENDING}" == "0" &&
        "${DELIVERY_OUTBOX_PENDING}" == "0" &&
        "${NOTIFICATION_OUTBOX_PENDING}" == "0" ]]; then
    break
  fi
  if (( SECONDS >= OUTBOX_DEADLINE )); then
    fail "outbox rows did not reach PUBLISHED"
  fi
  sleep 1
done

TRACE_EVENT_COUNT="$(docker compose exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$1" -Atc "$2"' \
  sh "${INVENTORY_POSTGRES_DB:-orderflow_inventory}" \
  "SELECT count(*) FROM outbox_event WHERE aggregate_id = '${ORDER_ID}'::uuid AND correlation_id = 'phase4-order-smoke' AND causation_id IS NOT NULL" |
  tr -d '[:space:]')"
[[ "${TRACE_EVENT_COUNT}" -ge 1 ]] ||
  fail "Kafka-derived inventory event did not preserve correlation and causation"

ORDER_DLT_OFFSET_AFTER="$(kafka_topic_end_offset "orderflow.order-events.v1.DLT")"
INVENTORY_DLT_OFFSET_AFTER="$(kafka_topic_end_offset "orderflow.inventory-events.v1.DLT")"
PAYMENT_DLT_OFFSET_AFTER="$(kafka_topic_end_offset "orderflow.payment-events.v1.DLT")"
DELIVERY_DLT_OFFSET_AFTER="$(kafka_topic_end_offset "orderflow.delivery-events.v1.DLT")"
NOTIFICATION_DLT_OFFSET_AFTER="$(kafka_topic_end_offset "orderflow.notification-events.v1.DLT")"
[[ "${ORDER_DLT_OFFSET_AFTER}" == "${ORDER_DLT_OFFSET_BEFORE}" ]] ||
  fail "an unexpected order event reached DLT"
[[ "${INVENTORY_DLT_OFFSET_AFTER}" == "${INVENTORY_DLT_OFFSET_BEFORE}" ]] ||
  fail "an unexpected inventory event reached DLT"
[[ "${PAYMENT_DLT_OFFSET_AFTER}" == "${PAYMENT_DLT_OFFSET_BEFORE}" ]] ||
  fail "an unexpected payment event reached DLT"
[[ "${DELIVERY_DLT_OFFSET_AFTER}" == "${DELIVERY_DLT_OFFSET_BEFORE}" ]] ||
  fail "an unexpected delivery event reached DLT"
[[ "${NOTIFICATION_DLT_OFFSET_AFTER}" == "${NOTIFICATION_DLT_OFFSET_BEFORE}" ]] ||
  fail "an unexpected notification event reached DLT"

SCHEDULER_RUNS="$(docker compose exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$1" -Atc "SELECT count(*) FROM scheduled_job_run"' \
  sh "${INVENTORY_POSTGRES_DB:-orderflow_inventory}" | tr -d '[:space:]')"
[[ "${SCHEDULER_RUNS}" -ge 1 ]] || fail "scheduler run history was not recorded"

if [[ ",${COMPOSE_PROFILES:-}," == *",observability,"* ]]; then
  wait_for_url "Prometheus" "http://localhost:${PROMETHEUS_PORT:-9091}/-/healthy"
  wait_for_url "Grafana" "http://localhost:${GRAFANA_PORT:-3000}/api/health"
fi

echo "Smoke tests passed:"
echo "  Phase 1 health, Eureka routing, metadata, and correlation checks passed."
echo "  Registration, JWT claims, refresh-cookie issuance, and current-user checks passed."
echo "  Customer access and admin denial passed."
echo "  Refresh rotation, reuse rejection, logout, and access revocation passed."
echo "  Product lifecycle, public cache reads, ownership, and category checks passed."
echo "  Cart snapshots, TTL-backed mutations, price validation, and security checks passed."
echo "  Inventory adjustment, reservation, rejection, release, and ownership checks passed."
echo "  Order creation, idempotency, cancellation, outbox, Kafka, and DLT checks passed."
echo "  Payment creation, confirmation, signed webhooks, replay, decline, refund, compensation boundaries, and payment DLT checks passed."
echo "  Delivery assignment, agent lifecycle, fulfillment propagation, ownership, outbox, and delivery DLT checks passed."
echo "  Durable notifications, preferences, unread/read flows, simulator email, deduplication, outbox, and notification DLT checks passed."
echo "  Cluster-safe scheduler history, operations ADMIN APIs, read model, reports, and Prometheus endpoint checks passed."
