# ADR 0013: Pessimistic Inventory Locking

- Status: Accepted
- Date: 2026-07-28

## Decision

Inventory reservation and release use PostgreSQL pessimistic row locks.
Multi-product operations merge quantities, sort product UUIDs, and acquire
locks in that deterministic order.

## Rationale and consequences

Stock checks and reserved-quantity increments must be one critical section.
Database locks coordinate every service instance and make PostgreSQL the source
of truth. Redis locks are not the primary consistency control because their
failure and lease model would be separate from the stock transaction.

Pessimistic locks trade some concurrency for simple no-oversell guarantees.
Stable ordering reduces deadlocks; bounded item sets and short transactions
bound lock time. Optimistic `@Version` remains useful for ordinary updates.
