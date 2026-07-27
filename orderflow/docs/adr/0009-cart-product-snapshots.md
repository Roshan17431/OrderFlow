# ADR 0009: Cart Product Snapshots

- Status: Accepted
- Date: 2026-07-26

## Decision

Store product name, price, currency, product version, and purchasability
snapshots in each cart item.

## Rationale and consequences

Snapshots allow cart reads when product-service is unavailable and preserve what
the customer last saw. They can become stale when catalog price, currency,
lifecycle, or category status changes. Cart validation therefore resolves all
IDs in one batch, refreshes snapshots, marks price changes/unavailability, and
retains unavailable items for explicit customer handling.

Snapshots are not price guarantees. Phase 4 checkout must perform another
authoritative product validation and an inventory check immediately before
order creation.
