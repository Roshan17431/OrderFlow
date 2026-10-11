# Disaster recovery

OrderFlow provides at-least-once workflows with transactional outboxes and idempotent consumers; it does not claim zero data loss, exactly-once delivery, or automatic multi-region failover.

| Incident | Immediate action | Recovery and consistency checks |
|---|---|---|
| PostgreSQL loss | Stop writes to the owning service; preserve evidence. | Restore the service database, run Flyway/Hibernate validation, compare row/outbox counts, then resume its consumers before traffic. |
| Redis loss | Fail authentication/cart/gateway limiter closed; keep product reads on PostgreSQL fallback. | Restore Redis, require users to reauthenticate where needed, accept documented cart loss, warm only safe caches, verify revocation keys. |
| Kafka loss | Keep committed outbox rows; stop manual publication. | Restore brokers/topics/ACLs, verify offsets, drain outboxes, confirm idempotency and DLT/lag recovery. |
| Payment provider outage | Keep payment pending/failed according to provider result; pause unsafe retries. | Restore provider connectivity, run reconciliation, compare provider IDs/amounts/refunds, investigate ambiguous attempts. |
| Service failure | Remove the unhealthy instance from routing. | Restart/replace it, verify readiness, outbox and consumer lag, then restore traffic gradually. |
| Outbox backlog | Preserve rows and reduce incoming load if necessary. | Follow the outbox runbook; restore Kafka/config and watch backlog drain without editing rows. |
| DLT growth | Stop blind replay and classify errors. | Deploy compatibility/config fix, replay a bounded sample, then controlled batches using original IDs. |
| Scheduler failure | Confirm lease ownership and database time. | Restart one healthy instance; verify no overlapping leases and run bounded catch-up. |
| Notification outage | Do not block authoritative order/payment state. | Restore provider/simulator, run reconciliation, verify attempt counts and one notification per event. |

After every recovery, run health checks, the relevant smoke subset, operations/read-model reconciliation, outbox/DLT/lag checks, and a security/log review. Escalate when payment state is ambiguous, backups fail validation, processed-event history is missing, or aggregate states disagree across owners.
