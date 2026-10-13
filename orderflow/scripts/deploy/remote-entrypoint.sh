#!/usr/bin/env bash
set -euo pipefail
[ "$#" -ge 6 ] && [ "$#" -le 7 ] || { echo 'six target arguments and optional explicit initial-seed boolean required' >&2; exit 1; }
account=$1 region=$2 environment=$3 domain=$4 release=$5 artifact=$6
initial_seed=${7:-false}
case "$initial_seed" in true|false) ;; *) echo 'invalid initial-seed boolean' >&2; exit 1 ;; esac
cd -- "$artifact"
args=(--account "$account" --region "$region" --environment "$environment" --domain "$domain" --release "$release" --repo "$artifact" --manifest "$artifact/deploy/release-manifest.json" --runtime-env /opt/orderflow/config/runtime.env --state-dir /opt/orderflow/releases/state --verification-script "$artifact/scripts/cloud/verify-journey.sh")
./scripts/deploy/fetch-secrets.sh "${args[@]}"
if [ "$initial_seed" = true ]; then args+=(--initial-demo-seed); fi
./scripts/deploy/deploy-release.sh "${args[@]}"
