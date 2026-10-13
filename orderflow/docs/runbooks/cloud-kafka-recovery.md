# Cloud Kafka recovery

1. Record broker health, disk usage, application outbox backlog, DLT counts and consumer lag before changing anything. Verify the intended AWS account/region, instance and release.
2. Inspect private broker logs with sensitive-data-aware handling, memory/OOM, permissions and `/opt/orderflow/data/kafka`. Compare persisted `meta.properties` cluster ID with protected `KAFKA_CLUSTER_ID`; do not print unrelated environment secrets.
3. Fix capacity or permissions deliberately. Restart the existing broker with the same cluster ID and persistent storage. Never run `kafka-storage.sh format`, reset offsets, delete topics or erase volumes as a routine repair.
4. Check `/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --describe` and consumer group lag from the private network, with bounded timeouts. Verify all expected topics and DLTs; replication factor one cannot recover a lost partition from another replica.
5. Let existing outbox and idempotent consumers replay after recovery. Watch deduplication, consumer errors, retries and notification/payment reconciliation. Do not manually publish business events without an approved replay plan.
6. Corrupted/lost broker data requires a documented recovery decision. PostgreSQL logical backups are not a Kafka backup. Reconstructing from retained outbox/audit data may be incomplete; record any loss and require approval for offset/replay changes.
7. Verify complete order/inventory/payment/delivery/notification flows and clearing backlogs before closing the incident. This single broker remains a single point of failure.
