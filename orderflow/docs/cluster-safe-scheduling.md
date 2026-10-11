# Cluster-safe scheduling

`scheduler-support` performs an atomic PostgreSQL `INSERT ... ON CONFLICT ... WHERE
locked_until <= locked_at RETURNING` acquisition. One replica wins; others record
`SKIPPED_LOCKED`. Expiration permits crash recovery, while bounded, idempotent work
makes duplicate execution harmless. Run records contain counts and safe error codes,
never payloads or credentials. Lock durations and instance IDs are configurable.
