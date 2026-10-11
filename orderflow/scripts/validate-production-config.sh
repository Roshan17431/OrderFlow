#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

command -v jq >/dev/null || {
  echo 'Production configuration validation: FAIL (jq is required)'
  exit 1
}

config_file="$(mktemp "${TMPDIR:-/tmp}/orderflow-production-config.XXXXXX")"
trap 'rm -f "${config_file}"' EXIT

docker compose --profile observability \
  -f docker-compose.yml -f docker-compose.production-like.yml \
  config --format json >"${config_file}"

jq -e '
  . as $root |
  .services["auth-service"].environment.APP_JWT_ALLOW_EPHEMERAL_KEYS == "false" and
  .services["auth-service"].environment.APP_AUTH_REFRESH_COOKIE_SECURE == "true" and
  .services["auth-service"].environment.APP_BOOTSTRAP_ADMIN_ENABLED == "false" and
  .services["payment-service"].environment.APP_PAYMENT_SIMULATOR_ENABLED == "false" and
  .services["payment-service"].environment.APP_PAYMENT_SIMULATOR_ALLOW_IN_PRODUCTION == "false" and
  .services["payment-service"].environment.APP_PAYMENT_SIMULATOR_WEBHOOK_SECRET == "" and
  (.services["api-gateway"].environment.APP_CORS_ALLOWED_ORIGINS | contains("*") | not) and
  .services["api-gateway"].environment.APP_SECURITY_HSTS_ENABLED == "true" and
  .services["api-gateway"].environment.APP_RATE_LIMIT_ENABLED == "true" and
  (.services | to_entries | all(
    .key == "api-gateway" or ((.value.ports // []) | length == 0))) and
  .networks.internal.internal == true and
  .networks.observability.internal == true and
  (["service-registry","api-gateway","auth-service","product-service","cart-service",
    "inventory-service","order-service","payment-service","delivery-service",
    "notification-service","operations-service"] | all(. as $service |
      $root.services[$service].read_only == true and
      ($root.services[$service].environment.MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE
        == "health,info,prometheus")))
' "${config_file}" >/dev/null || {
  echo 'Production configuration validation: FAIL (unsafe production-like value)'
  exit 1
}

echo 'Production configuration validation: PASS'
