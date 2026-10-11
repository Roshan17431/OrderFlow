# ADR 0018: Layered payment idempotency

## Status

Accepted

## Decision

Payment workflows use four independent idempotency layers:

- PostgreSQL scope/actor/key records for HTTP confirmation, cancellation, and
  refunds.
- Stable provider operation references for external requests.
- Event ID/consumer records for Kafka delivery.
- Provider/event ID plus payload hash for webhooks.

Each protects a different retry boundary. None is replaced by Kafka offsets,
Redis, or in-memory state. HTTP keys are validated, request input is normalized
and hashed, and PostgreSQL uniqueness serializes concurrency.

## Consequences

The same key and request replays the stored result; key reuse with different
input fails. At-least-once outbox publication remains safe but is not described
as exactly-once delivery.
