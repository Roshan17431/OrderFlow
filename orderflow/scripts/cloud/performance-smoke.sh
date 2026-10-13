#!/usr/bin/env bash
set -euo pipefail
[[ $# == 0 ]] || { echo 'Use guarded environment configuration; no arguments accepted' >&2; exit 1; }
exec python3 "$(dirname "$0")/performance_smoke.py"
