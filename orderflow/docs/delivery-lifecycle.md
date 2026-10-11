# Delivery lifecycle

The delivery state machine is deliberately separate from order payment status:

```text
PENDING_ASSIGNMENT -> ASSIGNED -> PICKED_UP -> OUT_FOR_DELIVERY -> DELIVERED
                              \-> FAILED
PENDING_ASSIGNMENT/ASSIGNED/PICKED_UP -> CANCELLED
```

Every accepted transition stores a timestamp and a `delivery_status_history` row with actor, correlation, and causation metadata. State regressions are rejected. A delivered or out-for-delivery shipment cannot be cancelled automatically. Completion or failure releases a busy agent back to `AVAILABLE`; cancellation releases an assigned agent when applicable.

Customer reads return 404 unless the delivery belongs to the JWT subject. Delivery agents see and mutate only work assigned to their profile. Administrators can inspect history and manually assign a pending delivery.
