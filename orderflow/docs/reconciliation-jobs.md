# Reconciliation jobs

- Inventory releases only still-RESERVED expired reservations under row locks.
- Payment reconciliation cancels overdue pending/action-required payments and asks
  the provider before deciding a PROCESSING payment. Unknown provider state is retried.
- Delivery reuses the existing assignment transaction and stops when no agent exists.
- Notification recovery only resets stale PROCESSING attempts; SENT is immutable and
  exhausted attempts become FAILED.

All queries are ordered and batch-limited. Outbox writes share the business transaction.
