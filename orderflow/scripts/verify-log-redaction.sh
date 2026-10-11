#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

generated_log="$(mktemp "${TMPDIR:-/tmp}/orderflow-redaction.XXXXXX")"
combined_log="$(mktemp "${TMPDIR:-/tmp}/orderflow-redaction-combined.XXXXXX")"
trap 'rm -f "${generated_log}" "${combined_log}"' EXIT

./mvnw -q \
  -pl api-gateway,auth-service,payment-service -am \
  -Dcyclonedx.skip=true \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='GatewaySecurityWebTest,GlobalExceptionHandlerTest,PaymentSecurityMvcTest' \
  test >"${generated_log}" 2>&1

cp "${generated_log}" "${combined_log}"
if [[ -n "${LOG_INPUT:-}" ]]; then
  [[ -f "${LOG_INPUT}" ]] || {
    echo 'Log redaction verification: FAIL (LOG_INPUT does not exist)'
    exit 1
  }
  sed -n '1,200000p' "${LOG_INPUT}" >>"${combined_log}"
fi

failures=0
check_log_rule() {
  local rule="$1"
  local pattern="$2"
  if rg --pcre2 --multiline --quiet -- "${pattern}" "${combined_log}"; then
    printf 'LOG_REDACTION finding rule=%s\n' "${rule}"
    failures=$((failures + 1))
  fi
}

check_log_rule RAW_JWT 'Bearer[[:space:]]+eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'
check_log_rule JSON_SECRET_FIELD '"(password|refreshToken|paymentMethodToken|webhookSecret|privateKey)"[[:space:]]*:[[:space:]]*"[^"*]{6,}"'
check_log_rule PRIVATE_KEY '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----\r?\n[A-Za-z0-9+/]{40,}'
check_log_rule LIVE_PAYMENT_TOKEN 'tok_(live|prod)_[A-Za-z0-9_-]{8,}'
check_log_rule FULL_CARD_NUMBER '(?<![A-Za-z0-9])[3456][0-9]{12,18}(?![A-Za-z0-9])'

if (( failures > 0 )); then
  printf 'Log redaction verification: FAIL (%d rule(s))\n' "${failures}"
  exit 1
fi

echo 'Log redaction verification: PASS'
