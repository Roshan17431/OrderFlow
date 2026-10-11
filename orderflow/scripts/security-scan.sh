#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

failures=0

scan_rule() {
  local rule="$1"
  local pattern="$2"
  local matches
  matches="$(rg --hidden --files-with-matches --pcre2 --multiline \
    -g '!**/target/**' \
    -g '!.git/**' \
    -g '!.idea/**' \
    -g '!scripts/security-scan.sh' -- "${pattern}" . 2>/dev/null || true)"
  if [[ -n "${matches}" ]]; then
    while IFS= read -r file; do
      [[ -n "${file}" ]] || continue
      printf 'SECURITY_SCAN finding rule=%s file=%s\n' "${rule}" "${file#./}"
      failures=$((failures + 1))
    done <<<"${matches}"
  fi
}

# High-confidence credential formats only. Values are deliberately never printed.
scan_rule PRIVATE_KEY '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----\r?\n[A-Za-z0-9+/]{40,}'
scan_rule AWS_ACCESS_KEY 'AKIA[0-9A-Z]{16}'
scan_rule GITHUB_TOKEN 'gh[pousr]_[A-Za-z0-9]{30,}'
scan_rule STRIPE_LIVE_KEY '(sk|rk)_live_[A-Za-z0-9]{20,}'
scan_rule SLACK_TOKEN 'xox[baprs]-[A-Za-z0-9-]{20,}'
scan_rule GOOGLE_API_KEY 'AIza[0-9A-Za-z_-]{30,}'
scan_rule OPENAI_STYLE_KEY 'sk-[A-Za-z0-9_-]{32,}'
scan_rule LIVE_PAYMENT_TOKEN 'tok_(live|prod)_[A-Za-z0-9_-]{12,}'
scan_rule EMBEDDED_BEARER 'Authorization:[[:space:]]*Bearer[[:space:]]+[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}'

credential_files="$(rg --hidden --files . \
  -g '*.pem' -g '*.key' -g '*.p12' -g '*.pfx' -g '*.jks' -g '*.keystore' \
  -g '!**/target/**' -g '!.git/**' 2>/dev/null || true)"
if [[ -n "${credential_files}" ]]; then
  while IFS= read -r file; do
    [[ -n "${file}" ]] || continue
    printf 'SECURITY_SCAN finding rule=CREDENTIAL_FILE file=%s\n' "${file#./}"
    failures=$((failures + 1))
  done <<<"${credential_files}"
fi

if (( failures > 0 )); then
  printf 'Security scan: FAIL (%d high-confidence finding(s))\n' "${failures}"
  exit 1
fi

echo 'Security scan: PASS (no probable committed production secrets)'
