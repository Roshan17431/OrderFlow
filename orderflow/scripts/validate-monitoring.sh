#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

command -v jq >/dev/null || {
  echo 'Monitoring validation: FAIL (jq is required)'
  exit 1
}

for dashboard in monitoring/grafana/dashboards/*.json; do
  jq empty "${dashboard}"
  jq -e '.title | type == "string" and length > 0' "${dashboard}" >/dev/null
done

jq -e --arg uid 'prometheus' \
  'all(.panels[]?; all(.targets[]?; (.datasource.uid? // $uid) == $uid))' \
  monitoring/grafana/dashboards/*.json >/dev/null

if rg --quiet --ignore-case \
  '(password|authorization|bearer|customer[_ -]?(email|phone|address)|private key)' \
  monitoring/grafana monitoring/prometheus; then
  echo 'Monitoring validation: FAIL (possible secret or PII in monitoring assets)'
  exit 1
fi

if rg --quiet --pcre2 \
  '\.(tag|tags)\("(userId|customerId|orderId|paymentId|deliveryId|eventId|correlationId)"' \
  --glob '*.java' --glob '!**/target/**' .; then
  echo 'Monitoring validation: FAIL (high-cardinality metric tag found)'
  exit 1
fi

if command -v promtool >/dev/null; then
  promtool check config monitoring/prometheus/prometheus.yml
elif command -v docker >/dev/null && docker info >/dev/null 2>&1; then
  docker run --rm --entrypoint promtool \
    -v "${PWD}/monitoring/prometheus:/etc/prometheus:ro" \
    prom/prometheus:v3.5.0 \
    check config /etc/prometheus/prometheus.yml
else
  echo 'Monitoring validation: FAIL (promtool or Docker is required)'
  exit 1
fi

prometheus_url="${PROMETHEUS_URL:-http://localhost:${PROMETHEUS_PORT:-9091}}"
if curl --connect-timeout 3 --max-time 10 --fail --silent --max-time 2 "${prometheus_url}/-/healthy" >/dev/null 2>&1; then
  targets="$(curl --connect-timeout 3 --max-time 10 --fail --silent "${prometheus_url}/api/v1/targets")"
  jq -e '.status == "success" and (.data.activeTargets | length >= 11)' <<<"${targets}" >/dev/null || {
    echo 'Monitoring validation: FAIL (expected service targets are missing)'
    exit 1
  }
  down="$(jq '[.data.activeTargets[] | select(.health != "up")] | length' <<<"${targets}")"
  [[ "${down}" == "0" ]] || {
    echo "Monitoring validation: FAIL (${down} Prometheus target(s) are down)"
    exit 1
  }
  rules="$(curl --connect-timeout 3 --max-time 10 --fail --silent "${prometheus_url}/api/v1/rules")"
  jq -e '.status == "success" and (.data.groups | length > 0) and all(.data.groups[]?.rules[]?; .health == "ok")' \
    <<<"${rules}" >/dev/null
  echo 'Monitoring validation: PASS (static assets, rules, and live targets)'
else
  if [[ "${REQUIRE_LIVE_MONITORING:-false}" == "true" ]]; then
    echo 'Monitoring validation: FAIL (required live endpoint unavailable)'
    exit 1
  fi
  echo 'Monitoring validation: PASS (static assets and rule syntax); live targets: NOT RUN'
fi
