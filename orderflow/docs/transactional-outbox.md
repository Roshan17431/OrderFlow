# Transactional Outbox

Publishing directly during a database transaction creates a dual-write
problem: PostgreSQL may commit while Kafka fails, or Kafka may accept a message
while PostgreSQL rolls back. Both Phase 4 services instead insert an outbox row
in the same local transaction as their business change.

```mermaid
sequenceDiagram
    participant B as Business transaction
    participant D as PostgreSQL
    participant P as Outbox publisher
    participant K as Kafka
    B->>D: State changes + NEW outbox row
    D-->>B: One atomic commit
    P->>D: Claim bounded due rows FOR UPDATE SKIP LOCKED
    D-->>P: IN_PROGRESS rows owned by instance
    P->>K: Publish JSON with stored topic/key
    alt accepted
        P->>D: Mark PUBLISHED
    else temporary failure
        P->>D: Increment attempts + exponential backoff
    else attempt limit
        P->>D: Mark FAILED with bounded safe code
    end
```

Claims recover after a configurable lock timeout. Publisher batches, poll
delay, retry base delay, send timeout, and maximum attempts are configurable.
Failed rows remain inspectable. Backlog, failed count, publications, and
publication failures are exposed as low-cardinality metrics.

A crash after Kafka acceptance but before `PUBLISHED` can publish a row again.
The design therefore guarantees durable at-least-once intent, not exactly-once
delivery. Processed-event tables make duplicate delivery harmless.

## Ordering across publishers

The five outbox-owning services assign a database `event_sequence`. A claim
selects only the first unpublished row for each `(topic, message_key)`, including
when an earlier row is in progress, waiting for retry, or permanently failed.
This prevents another publisher from overtaking it. Independent aggregate keys
can proceed in the same bounded batch. A failed head row intentionally blocks
later events for that key until an operator resolves it. Consumers still enforce
business idempotency; this does not provide exactly-once delivery.
