# Cart Consistency

Redis is the Phase 3 cart system of record. Each key is
`orderflow:cart:{JWT-subject}` and holds reserved metadata plus `item:{UUID}`
JSON snapshot fields. The client cannot select the key owner.

## Atomic mutation model

Add, update, remove, and validation replacement run server-side Lua scripts.
The scripts atomically enforce currency, quantity, distinct-item count,
timestamps, rolling TTL, and a monotonically increasing cart version. Failed
operations do not increment the version. Removing the final item deletes the
hash.

The defaults are 100 distinct items, quantity 1–99, and a 30-day TTL; all are
validated configuration. Money is stored as a decimal string and calculated
with `BigDecimal`, avoiding Lua/JSON floating-point conversion.

Concurrent tests verify no lost same-item additions, bounded quantities and
distinct items, no mixed-currency cart, and unique versions for every
successful mutation.

## Snapshots and validation

Items store name, unit price, currency, product version, availability flags,
and added/updated timestamps. GET reads only Redis and remains useful during a
product outage. Validation resolves every product ID in one batch, marks price
changes and unavailable products, replaces snapshots atomically, and never
silently removes an unavailable item.

Redis failure returns `CART_STATE_UNAVAILABLE` because the service cannot safely
invent cart state. Product failure affects add, update, and validation with
`PRODUCT_SERVICE_UNAVAILABLE`, but does not make cart readiness depend on the
product service. Phase 4 checkout must revalidate both products and
authoritative inventory before creating an order.
