# ADR 0037: Encrypted logical backups and isolated restore verification

Status: accepted.

Back up all eight service databases using PostgreSQL custom-format logical dumps with container-internal administrator credentials. Timestamped backups never overwrite an existing backup by default. Encrypt archive data with AES-256 and PBKDF2 using an SSM-provisioned protected `config/backup.key`. Metadata records cipher checksums and schema fingerprints; private encrypted/versioned S3 can supply an approved off-host destination. Credentials and keys are excluded from metadata and command output.

Verify integrity and decrypt/restore only into an isolated temporary PostgreSQL test instance with no public port. Exercise `pg_restore`, Flyway history and representative table counts before reporting restore success. Actual production data replacement requires a separate reviewed recovery plan and explicit approval. Deployment/rollback does not restore business data automatically; schema compatibility and predeployment backup determine safe image rollback.

Track backup and isolated-restore timestamps through node-exporter textfile metrics and alert on missing/stale data. Proposed objectives are daily backups and monthly restore exercises; measured RPO/RTO remain unknown until real schedules and recovery exercises run. Logical backups have per-database consistency, not a cross-service transaction snapshot. Kafka offsets/logs and Redis revocations require their own persistence/recovery plan; losing them cannot be repaired by a PostgreSQL dump alone. JWT/ACME keys remain restricted persistent/secret-management assets.

The single EC2/EBS topology requires a separate encrypted off-host copy. Local archive creation, mock uploads or tmpfs restore tests establish tooling behavior only; they do not prove S3 durability, IAM access or a live disaster-recovery outcome.
