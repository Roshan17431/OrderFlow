#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
TIMEOUT_SECONDS="${STARTUP_TIMEOUT_SECONDS:-300}"

cd "${REPOSITORY_ROOT}"
# Keep build JVMs from competing with application startup on smaller Docker hosts.
for service in service-registry api-gateway auth-service product-service cart-service \
    inventory-service order-service payment-service delivery-service notification-service operations-service; do
  docker compose build "${service}"
done
docker compose up -d

wait_for_url() {
  local name="$1"
  local url="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  echo "Waiting for ${name}: ${url}"
  until curl --connect-timeout 3 --max-time 10 --fail --silent --show-error "${url}" >/dev/null; do
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
wait_for_url "inventory service" "http://localhost:${INVENTORY_SERVICE_PORT:-8084}/actuator/health/readiness"
wait_for_url "order service" "http://localhost:${ORDER_SERVICE_PORT:-8085}/actuator/health/readiness"
wait_for_url "payment service" "http://localhost:${PAYMENT_SERVICE_PORT:-8086}/actuator/health/readiness"
wait_for_url "delivery service" "http://localhost:${DELIVERY_SERVICE_PORT:-8087}/actuator/health/readiness"
wait_for_url "notification service" "http://localhost:${NOTIFICATION_SERVICE_PORT:-8088}/actuator/health/readiness"
wait_for_url "operations service" "http://localhost:${OPERATIONS_SERVICE_PORT:-8089}/actuator/health/readiness"
wait_for_url "API gateway" "http://localhost:${API_GATEWAY_PORT:-8080}/actuator/health/readiness"
wait_for_url "gateway discovery route" "http://localhost:${API_GATEWAY_PORT:-8080}/api/platform/ping"
wait_for_url "gateway product route" "http://localhost:${API_GATEWAY_PORT:-8080}/api/categories"

echo "OrderFlow is ready:"
echo "  Gateway:          http://localhost:${API_GATEWAY_PORT:-8080}"
echo "  Auth service:     http://localhost:${AUTH_SERVICE_PORT:-8081}"
echo "  Product service:  http://localhost:${PRODUCT_SERVICE_PORT:-8082}"
echo "  Cart service:     http://localhost:${CART_SERVICE_PORT:-8083}"
echo "  Inventory service:http://localhost:${INVENTORY_SERVICE_PORT:-8084}"
echo "  Order service:    http://localhost:${ORDER_SERVICE_PORT:-8085}"
echo "  Payment service:  http://localhost:${PAYMENT_SERVICE_PORT:-8086}"
echo "  Delivery service: http://localhost:${DELIVERY_SERVICE_PORT:-8087}"
echo "  Notification svc: http://localhost:${NOTIFICATION_SERVICE_PORT:-8088}"
echo "  Operations svc:   http://localhost:${OPERATIONS_SERVICE_PORT:-8089}"
echo "  Eureka dashboard: http://localhost:${EUREKA_PORT:-8761}"
