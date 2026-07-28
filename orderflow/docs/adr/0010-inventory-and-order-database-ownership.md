# ADR 0010: Inventory and Order Database Ownership

- Status: Accepted
- Date: 2026-07-28

## Decision

Inventory-service exclusively owns `orderflow_inventory`; order-service
exclusively owns `orderflow_order`. Order-service obtains cart data through
cart-service. Inventory-service validates management access through
product-service. Neither service queries auth, product, cart, inventory, or
order storage owned by another service.

## Rationale and consequences

Independent schemas, migrations, roles, and credentials prevent hidden joins
and allow each service to evolve or scale separately. API and event contracts
make availability and compatibility costs visible.

Local Compose may host all logical databases on one PostgreSQL server to reduce
developer overhead. The idempotent bootstrap creates or repairs service roles
and databases even on volumes from earlier phases. Production may isolate
physical servers without application restructuring.
