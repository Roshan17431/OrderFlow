# Inventory Consistency

Each inventory row stores `onHandQuantity` and `reservedQuantity`.
`availableQuantity` is calculated, never stored:

```text
availableQuantity = onHandQuantity - reservedQuantity
```

Database checks require on-hand and reserved quantities to be nonnegative and
reserved quantity not to exceed on-hand. Manual adjustments lock the product
row and may not reduce on-hand below reserved stock.

```mermaid
flowchart TD
    Event["ORDER_CREATED"] --> Sort["Merge duplicate items and sort product UUIDs"]
    Sort --> Lock["Lock all existing inventory rows in that order"]
    Lock --> Check{"Every product has enough available stock?"}
    Check -->|yes| Reserve["Increase all reserved quantities"]
    Reserve --> Success["Store RESERVED reservation, inbox, and INVENTORY_RESERVED outbox"]
    Check -->|no| Reject["Change no quantities"]
    Reject --> Failure["Store REJECTED reservation, inbox, and failure outbox"]
```

The business transaction locks all involved rows using PostgreSQL
`PESSIMISTIC_WRITE`. Stable UUID ordering bounds deadlock risk when orders
contain the same products in different sequences. Redis locks and Java
`synchronized` are not correctness controls.

One reservation exists per order. Duplicate event IDs are ignored through the
processed-event table; an existing order reservation also prevents applying a
second event ID twice. Cancellation locks the reservation and product rows,
decrements reserved stock once, and marks it `RELEASED`. Rejected or
cancel-before-reserve cases publish a safe no-stock release result so order
cancellation can finish.

Reservations have a configurable expiry. The optional bounded poller uses
`FOR UPDATE SKIP LOCKED`, releases due rows, and emits `INVENTORY_RELEASED`
with reason `RESERVATION_EXPIRED`. A later scheduling phase may improve
cross-instance orchestration, but multiple current instances cannot claim the
same locked row concurrently.
