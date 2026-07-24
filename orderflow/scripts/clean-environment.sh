#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

cd "${REPOSITORY_ROOT}"
echo "Stopping OrderFlow and permanently removing this project's PostgreSQL, Redis, and Kafka volumes."
docker compose down --volumes --remove-orphans
echo "OrderFlow local containers and persistent volumes were removed."
