# Inventory reservation backlog

## Symptoms

Orders remain `INVENTORY_PENDING`, reservation expiry age rises, available/reserved quantities appear inconsistent, or inventory DLT/outbox metrics rise.

## Investigation

Check order/inventory Kafka lag, outbox state, product management access, database locks, reservation expiry scheduler lease, and the aggregate correlation ID. Compare `on_hand`, `reserved`, and active reservation item totals using read-only queries.

## Recovery and verification

Restore the failed dependency and let the idempotent consumer/scheduler resume. Verify each order has one reservation outcome, no partial multi-item reservation, `reserved <= on_hand`, expired stock is released once, and payment is not created for rejected inventory. Escalate oversell, negative quantities, or an order/reservation ownership mismatch.
