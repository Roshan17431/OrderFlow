# Kafka recovery

Consumer offsets indicate delivery progress, not completion of business semantics. Each idempotent consumer also records `eventId` in its service-owned `processed_event` table in the same transaction as its state change. Retention of those rows must exceed plausible replay horizons; deleting them early can turn replay into duplicate effects.

For lag or outage: stop unsafe manual retries, confirm broker/topic health, capture consumer-group offsets and lag, inspect service outbox backlog and DLT counts, restore brokers, and allow bounded publishers/consumers to recover. Verify outbox rows become `PUBLISHED`, lag falls, processed-event counts grow without duplicate business records, and readiness/alerts recover.

Inspect DLT payload metadata and headers without printing sensitive bodies. Classify the root cause and deploy a compatible consumer before replay. Replay through a controlled producer with the original `eventId`, event type/version, aggregate key, correlation ID, and a rate limit. There is deliberately no public automatic DLT replay endpoint.

Resetting offsets can redeliver every event in the selected range or skip unprocessed events. Require a change record, exact group/topic/partition/offset bounds, backups, a dry-run count, and an approved rollback plan. Never reset offsets merely to clear an alert. Duplicate messages are safe only for consumers whose transactional idempotency has been verified.
