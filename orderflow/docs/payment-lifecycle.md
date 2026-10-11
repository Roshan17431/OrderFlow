# Payment lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> PROCESSING: confirm
    PROCESSING --> REQUIRES_ACTION: provider challenge
    REQUIRES_ACTION --> PROCESSING: retry
    PROCESSING --> SUCCEEDED: signed success
    PROCESSING --> FAILED: decline/failure
    PENDING --> CANCEL_REQUESTED: cancel
    PROCESSING --> CANCEL_REQUESTED: cancel
    CANCEL_REQUESTED --> CANCELLED: provider confirmation
    SUCCEEDED --> REFUND_PENDING: refund request
    PARTIALLY_REFUNDED --> REFUND_PENDING: further refund
    REFUND_PENDING --> PARTIALLY_REFUNDED: partial success
    REFUND_PENDING --> REFUNDED: full success
```

Each provider confirmation creates a numbered attempt without persisting the
opaque token. Success and failure webhooks update the latest attempt. A failure
after success is ignored; duplicate terminal webhooks are idempotent. A failed
payment may enter processing only through a new attempt.

```mermaid
sequenceDiagram
    participant P as Payment service
    participant K as Kafka
    participant O as Order service
    participant I as Inventory service
    P->>K: PAYMENT_FAILED
    K->>O: store failure and PAYMENT_FAILED
    O->>K: ORDER_CANCEL_REQUESTED
    K->>I: release reservation
    I->>K: INVENTORY_RELEASED
    K->>O: record releasedAt, keep PAYMENT_FAILED
```

Order states are monotonic: `PAYMENT_PENDING` becomes `PAID`,
`PAYMENT_FAILED`, or cancellation. Paid orders become partially or fully
refunded. Refunds do not change inventory.
