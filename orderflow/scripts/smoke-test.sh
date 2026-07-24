#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-180}"
GATEWAY_URL="http://localhost:${API_GATEWAY_PORT:-8080}"
AUTH_URL="http://localhost:${AUTH_SERVICE_PORT:-8081}"
EUREKA_URL="http://localhost:${EUREKA_PORT:-8761}"
KNOWN_ID="orderflow-smoke-test-123"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

cd "${REPOSITORY_ROOT}"

wait_for_url() {
  local name="$1"
  local url="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  until curl --fail --silent --show-error "${url}" >/dev/null; do
    if (( SECONDS >= deadline )); then
      echo "Timed out waiting for ${name}: ${url}" >&2
      docker compose ps >&2 || true
      return 1
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
      echo "Timed out waiting for ${application_name} to register UP in Eureka." >&2
      docker compose ps >&2 || true
      return 1
    fi
    sleep 2
  done
  echo "Registered UP: ${application_name}"
}

wait_for_gateway_ping() {
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  until curl --fail --silent --show-error \
      -H "Accept: application/json" \
      -H "X-Correlation-ID: ${KNOWN_ID}" \
      -D "${TEMP_DIR}/known.headers" \
      -o "${TEMP_DIR}/known.json" \
      "${GATEWAY_URL}/api/platform/ping" 2>/dev/null; do
    if (( SECONDS >= deadline )); then
      echo "Timed out waiting for the gateway to resolve AUTH-SERVICE." >&2
      docker compose ps >&2 || true
      return 1
    fi
    sleep 2
  done
  echo "Routed through Eureka: /api/platform/ping"
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
  awk -F ': *' 'tolower($1) == "x-correlation-id" {
    sub("\r$", "", $2)
    print $2
  }' "${file}" | tail -n 1
}

header_count() {
  local file="$1"
  awk -F ': *' 'tolower($1) == "x-correlation-id" { count++ }
    END { print count + 0 }' "${file}"
}

wait_for_url "service registry" "${EUREKA_URL}/actuator/health/readiness"
wait_for_url "auth service" "${AUTH_URL}/actuator/health/readiness"
wait_for_url "API gateway" "${GATEWAY_URL}/actuator/health/readiness"
wait_for_eureka_app "AUTH-SERVICE"
wait_for_eureka_app "API-GATEWAY"
wait_for_gateway_ping

KNOWN_HEADER="$(header_value "${TEMP_DIR}/known.headers")"
KNOWN_BODY="$(json_value "${TEMP_DIR}/known.json" "correlationId")"
if [[ "$(header_count "${TEMP_DIR}/known.headers")" != "1" \
    || "${KNOWN_HEADER}" != "${KNOWN_ID}" \
    || "${KNOWN_BODY}" != "${KNOWN_ID}" ]]; then
  echo "Known correlation ID did not propagate through header and body." >&2
  echo "Header: ${KNOWN_HEADER}; body: ${KNOWN_BODY}" >&2
  exit 1
fi

curl --fail --silent --show-error \
  -H "Accept: application/json" \
  -D "${TEMP_DIR}/generated.headers" \
  -o "${TEMP_DIR}/generated.json" \
  "${GATEWAY_URL}/api/platform/ping"

GENERATED_HEADER="$(header_value "${TEMP_DIR}/generated.headers")"
GENERATED_BODY="$(json_value "${TEMP_DIR}/generated.json" "correlationId")"
if [[ -z "${GENERATED_HEADER}" || -z "${GENERATED_BODY}" ]]; then
  echo "A missing correlation ID was not generated." >&2
  exit 1
fi
if [[ "$(header_count "${TEMP_DIR}/generated.headers")" != "1" \
    || "${GENERATED_HEADER}" != "${GENERATED_BODY}" ]]; then
  echo "Generated header and body correlation IDs differ." >&2
  exit 1
fi

curl --fail --silent --show-error \
  -H "X-Correlation-ID: ${KNOWN_ID}" \
  "${GATEWAY_URL}/api/platform/info" >"${TEMP_DIR}/info.json"
if [[ "$(json_value "${TEMP_DIR}/info.json" "schemaVersion")" != "1" ]]; then
  echo "Platform metadata was not read through the gateway." >&2
  exit 1
fi

echo "Smoke tests passed:"
echo "  Eureka, auth service, and gateway are healthy and registered."
echo "  Gateway resolved AUTH-SERVICE through Eureka."
echo "  Ping and database-backed info routes succeeded."
echo "  Supplied and generated correlation IDs propagated end to end."
