#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

cd "${REPOSITORY_ROOT}"
echo "Stopping OrderFlow containers; persistent volumes will be retained."
docker compose down --remove-orphans
