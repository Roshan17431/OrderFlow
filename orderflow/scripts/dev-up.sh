#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
TIMEOUT_SECONDS="${STARTUP_TIMEOUT_SECONDS:-300}"

cd "${REPOSITORY_ROOT}"
docker compose up -d --build

wait_for_url() {
  local name="$1"
  local url="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  echo "Waiting for ${name}: ${url}"
  until curl --fail --silent --show-error "${url}" >/dev/null; do
    if (( SECONDS >= deadline )); then
      echo "${name} did not become healthy within ${TIMEOUT_SECONDS}s" >&2
      docker compose ps >&2
      docker compose logs --tail=100 >&2
      return 1
    fi
    sleep 3
  done
}

wait_for_url "service registry" "http://localhost:${EUREKA_PORT:-8761}/actuator/health/readiness"
wait_for_url "auth service" "http://localhost:${AUTH_SERVICE_PORT:-8081}/actuator/health/readiness"
wait_for_url "product service" "http://localhost:${PRODUCT_SERVICE_PORT:-8082}/actuator/health/readiness"
wait_for_url "cart service" "http://localhost:${CART_SERVICE_PORT:-8083}/actuator/health/readiness"
wait_for_url "API gateway" "http://localhost:${API_GATEWAY_PORT:-8080}/actuator/health/readiness"
wait_for_url "gateway discovery route" "http://localhost:${API_GATEWAY_PORT:-8080}/api/platform/ping"
wait_for_url "gateway product route" "http://localhost:${API_GATEWAY_PORT:-8080}/api/categories"

echo "OrderFlow is ready:"
echo "  Gateway:          http://localhost:${API_GATEWAY_PORT:-8080}"
echo "  Auth service:     http://localhost:${AUTH_SERVICE_PORT:-8081}"
echo "  Product service:  http://localhost:${PRODUCT_SERVICE_PORT:-8082}"
echo "  Cart service:     http://localhost:${CART_SERVICE_PORT:-8083}"
echo "  Eureka dashboard: http://localhost:${EUREKA_PORT:-8761}"
