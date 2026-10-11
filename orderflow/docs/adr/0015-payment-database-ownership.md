# ADR 0015: Payment database ownership

## Status

Accepted

## Decision

Payment-service exclusively owns payments, provider references, attempts,
refunds, webhook ledgers, idempotency records, and its outbox in
`orderflow_payment`. Order-service stores only order-facing payment lifecycle
facts such as payment ID, paid/failed timestamps, safe failure code, and
cumulative refunded amount.

Services exchange versioned events and never query each other's database.
This keeps provider details out of the order domain and lets payment-service
enforce its own transactional and security invariants.

Local Compose hosts the logical databases on one PostgreSQL server for
efficiency. Separate roles and credentials preserve ownership. Production may
place them on separate servers without code changes.

## Consequences

Cross-service state is eventually consistent and requires outboxes,
idempotent consumers, compensation, and operational visibility. Database joins
between orders and payments are intentionally unavailable.
