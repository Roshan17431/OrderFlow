#!/usr/bin/env bash
# Validate syntax in a disposable Ubuntu userspace. No systemd daemon, AWS or poweroff runs.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec docker run --rm --memory 384m --cpus 1 --pids-limit 256 \
  -v "$SCRIPT_DIR:/units:ro" ubuntu:24.04 bash -euc '
  export DEBIAN_FRONTEND=noninteractive
  timeout 90 apt-get -qq -o Acquire::Retries=1 -o Acquire::http::Timeout=20 update
  timeout 120 apt-get -qq -y --no-install-recommends install systemd >/tmp/install.log 2>&1
  printf "#!/bin/sh\nexit 0\n" > /usr/local/sbin/orderflow-host
  chmod 0700 /usr/local/sbin/orderflow-host
  printf "[Service]\nType=oneshot\nExecStart=/usr/bin/true\n" > /etc/systemd/system/docker.service
  cp /units/orderflow-demo.service /units/orderflow-demo-stop.service /units/orderflow-demo-stop.timer /etc/systemd/system/
  mkdir -p /etc/systemd/system/docker.service.d
  cp /units/docker-orderflow-mount.conf /etc/systemd/system/docker.service.d/orderflow-mount.conf
  systemd-analyze verify --man=no /etc/systemd/system/orderflow-demo.service /etc/systemd/system/orderflow-demo-stop.service /etc/systemd/system/orderflow-demo-stop.timer /etc/systemd/system/docker.service
  echo "PASS Ubuntu systemd unit syntax; actual boot/shutdown NOT RUN"
'
