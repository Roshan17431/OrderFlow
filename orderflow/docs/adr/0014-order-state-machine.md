# ADR 0014: Order State Machine

- Status: Accepted
- Date: 2026-07-28

## Decision

All order status changes pass through one explicit state-machine component.
Every accepted transition creates an immutable history row.

## Rationale and consequences

Central rules reject illegal transitions and make Kafka/API races predictable.
Reservation arriving after a cancellation request records reservation details
without reactivating the order. Reservation failure or release completes a
pending cancellation. Terminal rejected/cancelled orders cannot become active.

Cancellation publishes an event and never changes inventory synchronously.
`PAYMENT_PENDING` means inventory is reserved and payment is required; it does
not mean authorized, paid, or completed. Payment states and provider behavior
remain Phase 5 work.
