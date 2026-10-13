# Cloud rollback runbook

Rollback changes application images and runs health/customer checks. It never restores PostgreSQL, deletes business records, removes volumes or rewinds Kafka offsets.

Find the previously healthy artifact from `/opt/orderflow/releases/state/previous.json`. After a failed candidate, the most recent healthy release remains in `healthy.json`; `schema-release.json` identifies the newest attempted migration set. Review both before selecting an older artifact. The target must retain the same account/region/environment and its SQL files must be an unchanged subset of the attempted schema. The attempted release must explicitly declare `backwardCompatible=true`. If a migration was destructive or compatibility is unknown, stop and prepare a forward fix.

Use the **old release's extracted source artifact** so Compose/configuration and migration hashes match its manifest:

```bash
cd /opt/orderflow/releases/PREVIOUS_RELEASE_DIRECTORY
./scripts/deploy/rollback-release.sh \
  --account "$APPROVED_ACCOUNT_ID" --region "$APPROVED_REGION" --environment cloud-demo \
  --domain "$APPROVED_DOMAIN" --release "$PREVIOUS_RELEASE_VERSION" \
  --manifest "$PWD/deploy/release-manifest.json" --repo "$PWD" \
  --runtime-env /opt/orderflow/config/runtime.env --state-dir /opt/orderflow/releases/state \
  --verification-script "$PWD/scripts/cloud/verify-journey.sh"
```

A filesystem lock serializes changes; preflight, a new encrypted predeployment backup, bounded readiness, registration, HTTPS and full journey verification are required. Healthy metadata advances only after verification. `schema-source.json` preserves the newest attempted source artifact so a pre-rollback backup validates newer applied SQL, even while older application code is selected. Keep that artifact available. The newest schema metadata is retained after rollback because reverting code does not revert database migrations. Existing runtime secrets stay protected and must remain compatible with the target; retrieve an older environment configuration only after a separate reviewed secret-rotation decision.

If older code cannot start against the current schema, roll forward. Database recovery is a separate emergency process requiring explicit authorization; see [database recovery](cloud-database-recovery.md). A Docker `up` exit zero by itself is not a rollback success claim.
