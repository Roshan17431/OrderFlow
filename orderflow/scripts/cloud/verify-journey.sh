#!/usr/bin/env bash
set -euo pipefail
[[ $# == 0 ]] || { echo 'This hook takes no arguments; use guarded environment configuration' >&2; exit 1; }
exec python3 "$(dirname "$0")/verify_journey.py"
