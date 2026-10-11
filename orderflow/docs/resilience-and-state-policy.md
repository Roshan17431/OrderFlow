# Resilience and monotonic state policy

OrderFlow provides at-least-once event delivery with transactional outboxes and
idempotent consumers. It does not claim exactly-once delivery. A producer may
publish the same event again after a crash between Kafka acknowledgement and the
outbox `PUBLISHED` update; consumers use `eventId` and their processed-event table
to make that retry harmless.

Business state moves forward only. A succeeded payment cannot be downgraded by a
late failure, a succeeded refund cannot be reversed by a late refund failure, a
delivered or failed delivery cannot return to an in-progress state, and repeated
inventory release is idempotent. Unsupported event versions are retried within a
bounded policy and then routed to a DLT instead of being interpreted under new
semantics.

Dependency behavior is deliberate:

- authentication revocation checks and gateway rate-limit state fail closed when
  Redis is unavailable;
- product reads fall back from Redis cache to PostgreSQL, while carts return 503
  because Redis is authoritative;
- database resource failures return a sanitized 503 and readiness reports down;
- service clients have bounded connection/read timeouts, retry only transient
  infrastructure failures, and open their circuit breakers after configured
  thresholds;
- Kafka publication failure leaves the outbox record visible and retryable. On
  restoration, the publisher reclaims expired work and consumers deduplicate any
  repeat publication.

Operators must follow the outbox and Kafka runbooks rather than editing workflow
tables or resetting consumer offsets casually.

