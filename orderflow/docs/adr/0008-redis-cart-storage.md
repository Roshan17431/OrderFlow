# ADR 0008: Redis Cart Storage

- Status: Accepted
- Date: 2026-07-26

## Decision

Store Phase 3 carts as namespaced Redis hashes with a rolling 30-day default TTL
and execute all mutations through Lua.

## Rationale and consequences

Carts are short-lived, high-write user state. Redis provides natural expiry and
server-side atomic operations. Lua prevents read/modify/write races while
enforcing quantity, distinct-item, currency, timestamp, TTL, and version rules.
Redis is therefore a cart-readiness dependency; unavailable Redis produces a
safe 503 rather than guessed state.

PostgreSQL cart persistence is deliberately deferred because Phase 3 does not
need durable order history and dual persistence would introduce coordination
without a business requirement. Phase 4 orders will be durable records separate
from expiring carts.
