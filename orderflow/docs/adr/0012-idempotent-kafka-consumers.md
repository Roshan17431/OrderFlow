# ADR 0012: Idempotent Kafka Consumers

- Status: Accepted
- Date: 2026-07-28

## Decision

Each consuming service stores a unique `(event_id, consumer_name)` record in
the same transaction as its business mutation.

## Rationale and consequences

Event IDs identify a logical publication across retries. Kafka offset commits
cannot atomically cover a separate PostgreSQL transaction, so redelivery is
normal after crashes or outbox retries. Checking and inserting the processed
record prevents duplicate stock changes and order history.

Consumer names keep independent handlers from incorrectly suppressing each
other. Invalid events are not marked processed; bounded retry sends them to
DLT for investigation.
