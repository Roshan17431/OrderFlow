# Database backup and restore

Back up each service database independently: `orderflow_auth`, `orderflow_product`, `orderflow_inventory`, `orderflow_order`, `orderflow_payment`, `orderflow_delivery`, `orderflow_notification`, and `orderflow_operations`. Kafka and Redis do not replace these authoritative records; cart data is Redis-owned and has a separately accepted loss model.

For each database, use a least-privileged backup identity and a custom-format dump:

```bash
pg_dump --host DB_HOST --username BACKUP_USER --format custom \
  --file orderflow_order-YYYYMMDD.dump orderflow_order
pg_restore --list orderflow_order-YYYYMMDD.dump
createdb --host RESTORE_HOST --username RESTORE_USER orderflow_order_restore_test
pg_restore --host RESTORE_HOST --username RESTORE_USER \
  --dbname orderflow_order_restore_test --clean --if-exists orderflow_order-YYYYMMDD.dump
```

Never put passwords on the command line or in the repository; use an operator-approved password file or secret injection. Encrypt backups in transit and at rest, restrict access, record checksums, and keep retention tiers aligned with legal/business requirements. A backup is not successful until a scheduled restore test passes Flyway/Hibernate validation and checks representative row counts, foreign keys, outbox/processed-event state, and application reads.

For a coordinated restore, stop writes, record Kafka offsets, restore all databases to a compatible point, start infrastructure, start consumers, then gateway traffic. Expect replay and rely on processed-event/idempotency records. Do not restore Redis as authoritative order/payment history.
