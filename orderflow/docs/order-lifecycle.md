# Order Lifecycle

An order begins at `INVENTORY_PENDING`. Reservation success records
`INVENTORY_RESERVED` and immediately advances to `PAYMENT_PENDING`; this means
payment is required, not authorized or completed. Reservation failure ends at
`INVENTORY_REJECTED`.

```mermaid
sequenceDiagram
    participant C as Customer
    participant O as Order service
    participant F as Kafka
    participant I as Inventory service
    C->>O: POST /orders/{id}/cancel
    O->>O: Lock order, validate state and owner
    O->>O: CANCEL_REQUESTED + history + outbox
    O-->>C: 202
    O->>F: ORDER_CANCEL_REQUESTED
    F->>I: Consume cancellation
    I->>I: Lock reservation and stock; release once
    I->>F: INVENTORY_RELEASED
    F->>O: Consume release
    O->>O: CANCEL_REQUESTED -> CANCELLED
```

Cancellation is allowed from `INVENTORY_PENDING`, `INVENTORY_RESERVED`, or
`PAYMENT_PENDING`. It never writes inventory directly. The same idempotency key
and normalized request returns the same order; a different request using that
key returns 409. An already cancelled order returns a successful current
representation. `INVENTORY_REJECTED` is terminal and returns a safe conflict.

Race rules are explicit:

- `INVENTORY_RESERVED` arriving after `CANCEL_REQUESTED` stores the reservation
  but keeps cancellation pending until release.
- `INVENTORY_RESERVATION_FAILED` while cancelling moves to `CANCELLED`.
- Expiration release from `PAYMENT_PENDING` records cancellation and terminal
  cancellation history.
- Duplicate inventory event IDs do not add history twice.

Every accepted state change stores previous/new status, a safe reason code,
event ID when applicable, correlation ID, and timestamp.
