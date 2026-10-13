# Cloud backup and restore

Eight PostgreSQL databases retain independent ownership: auth, product, inventory, order, payment, delivery, notification and operations. Cart data lives in Redis. The scripts make PostgreSQL custom-format `pg_dump` backups, one per owner, in a new UTC timestamp/random-suffix directory. Dumps are encrypted locally with OpenSSL AES-256-CBC, PBKDF2-SHA256 and 200,000 iterations using protected `config/backup.key`. Files are 0600 and directories 0700. AES ciphertext and plaintext SHA256 checksums detect corruption; protection of the metadata and encryption key remains essential.

```bash
./scripts/backup/backup-databases.sh \
  --runtime-env /opt/orderflow/config/runtime.env \
  --repo /opt/orderflow/releases/RELEASE_DIRECTORY \
  --key-file /opt/orderflow/config/backup.key \
  --output /opt/orderflow/backups
./scripts/backup/verify-backup.sh --backup /opt/orderflow/backups/EXACT_BACKUP_DIRECTORY
./scripts/backup/restore-to-test.sh --isolated-test \
  --backup /opt/orderflow/backups/EXACT_BACKUP_DIRECTORY \
  --key-file /opt/orderflow/config/backup.key \
  --repo /opt/orderflow/releases/RELEASE_DIRECTORY
```

The source Compose set and image/environment substitutions must match the release; `IMAGE_*` values come from its digest manifest during deployment. Database credentials are read from the container's private environment, not host command arguments. No secret command output is displayed.

Optional upload adds `--s3-uri s3://APPROVED_PRIVATE_BUCKET/backups --region APPROVED_REGION`. Instance IAM supplies temporary credentials. Uploads request SSE-AES256, and Terraform enforces a private encrypted/versioned destination with prefix-scoped IAM. Local encryption also protects dumps outside S3. Upload metadata alongside all eight ciphertexts. Preserve the encryption key through independently protected SSM/KMS recovery access; losing it makes the dumps unusable.

The backup records table counts, representative record hashes and Flyway history/checksums from the release's SQL. It checks source fingerprints before and after capture; writes during that window can make validation fail. Quiesce demo writes through a reviewed maintenance procedure for repeatable restore proofs. Each `pg_dump` is transactionally consistent for one database; sequential dumps do not give an atomic cross-service snapshot. Kafka/Redis state and concurrent distributed transactions need their own recovery coordination. See [PostgreSQL pg_dump](https://www.postgresql.org/docs/16/app-pgdump.html).

Predeployment backup is mandatory. First deployment may back up entirely empty schemas after the private database bootstrap, before Flyway. A nonempty database without Flyway history fails closed; missing healthy metadata never permits silently skipping business-data backup. Subsequent backups validate all applied SQL migration checksums.

`restore-to-test` has no live-host/database target option. It starts a unique `orderflow-restore-<random>` PostgreSQL 16 container, with no published ports, no live network, no existing volumes and a bounded tmpfs. It decrypts and restores all eight archives, creates isolated per-service owner roles, restores each database under its owner, proves password-authenticated owner SQL connectivity, and validates restored tables/counts/sample hashes and applied Flyway SQL checksums, then removes only its generated labeled test container. It never restores into a source database or removes user volumes. Application startup/Flyway application validation and live recovery remain separate checks and are explicitly **NOT RUN** in this tool's evidence.

For a safe local exercise using real release migrations and synthetic data:

```bash
python3 scripts/backup/test-isolated-restore.py
```

This uses unique temporary source/restore containers and writes `deploy/local-restore-evidence.json`; it requires Docker and PostgreSQL 16/OpenSSL. It is not an AWS or live recovery test.

Default local retention preserves every backup. No automatic pruning is implemented. Before enabling the optional Terraform S3 lifecycle policy, review its explicit retention period with the owner; retain multiple recent daily backups and a verified older recovery point. Versioned S3 objects have separate noncurrent-version retention. Keep release manifests and metadata as long as corresponding database backups. To rotate encryption keys, preserve old keys with controlled recovery access until all older backups expire.

Successful backup and restore proofs update `/opt/orderflow/data/node-exporter/backup.prom` with `orderflow_backup_last_success_timestamp_seconds` and `orderflow_backup_restore_verified_timestamp_seconds`. Schedule a daily backup on the instance only after provisioning approval, and an isolated restore proof at least monthly. Monitoring alerts on missing/stale timestamps do not establish recoverability themselves.

Daily logical backups imply a proposed RPO of up to 24 hours plus failed-job delay. RTO is unmeasured: recovery depends on infrastructure reconstruction, artifact/key retrieval, restored data size and application validation. Logical dumps do not provide point-in-time recovery. EBS snapshots are separately approved, encrypted infrastructure protection and do not replace logical restore exercises.
