# ADR 0019: Refunds and compensation

## Status

Accepted

## Decision

Payment-service supports explicit partial and full refunds. It locks the
payment, validates the remaining balance, creates one durable refund, and waits
for a signed provider webhook before increasing cumulative refunded amount.

A late payment success after cancellation, deadline expiry, or inventory
expiry starts a full `SYSTEM_COMPENSATION` refund. A cancellation arriving
before payment creation creates a durable order-keyed tombstone. These rules
resolve the inventory-expiry/payment-capture race without cross-service locks.

Refund events update order financial state but do not restock inventory.
Returns, inspection, and restocking require a later workflow and separate
business policy.

## Consequences

Temporary inconsistency is observable while compensation is processing.
Failed refunds preserve the paid/partially-refunded state and emit a safe
failure event for operations.
