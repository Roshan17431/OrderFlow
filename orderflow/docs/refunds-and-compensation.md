# Refunds and compensation

Admins may request an explicit positive refund amount up to the remaining
refundable balance. PostgreSQL locking and constraints prevent concurrent
over-refunds. Provider confirmation is asynchronous.

```mermaid
sequenceDiagram
    participant A as Admin
    participant P as Payment service
    participant S as Provider
    participant K as Kafka
    participant O as Order service
    A->>P: refund(amount, reason, Idempotency-Key)
    P->>S: create refund with provider reference
    P-->>A: 202 PROCESSING
    S->>P: signed refund.succeeded
    P->>K: PAYMENT_REFUNDED
    K->>O: PARTIALLY_REFUNDED or REFUNDED
```

A failed refund never changes cumulative refunded amount and restores the
payment to `SUCCEEDED` or `PARTIALLY_REFUNDED`. Partial and full refunds do not
automatically restock inventory.

```mermaid
sequenceDiagram
    participant I as Inventory
    participant O as Order
    participant P as Payment
    participant S as Provider
    I->>O: INVENTORY_RELEASED (RESERVATION_EXPIRED)
    O->>P: PAYMENT_CANCEL_REQUESTED (SYSTEM_COMPENSATION)
    alt payment pending
        P->>S: cancel
    else payment already succeeded or late success arrives
        P->>S: full compensation refund
        S->>P: signed refund.succeeded
        P->>O: PAYMENT_REFUNDED
    end
```

A cancellation tombstone handles the race where cancellation arrives before
payment creation. Late capture after cancellation or deadline expiry starts a
full `SYSTEM_COMPENSATION` refund instead of leaving money captured without
inventory.
