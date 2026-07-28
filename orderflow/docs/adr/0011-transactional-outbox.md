# ADR 0011: Transactional Outbox

- Status: Accepted
- Date: 2026-07-28

## Decision

Order and inventory business transactions insert event JSON into a
service-owned outbox table. Separate scheduled publishers claim bounded rows
and publish them to Kafka.

## Rationale and consequences

A direct PostgreSQL/Kafka dual write can lose an event or publish data whose
database transaction later rolls back. The outbox makes committed event intent
atomic with business state and supports bounded retry and inspectable failure.

Kafka acceptance followed by a crash before marking `PUBLISHED` can still
produce duplicates. Consumers must be idempotent. This is deliberately
at-least-once processing and does not claim exactly-once delivery.
