# ADR 0006: Product Database Ownership

- Status: Accepted
- Date: 2026-07-26

## Decision

Product-service exclusively owns the `orderflow_product` logical database.
Auth-service exclusively owns `orderflow_auth`; neither service reads or writes
the other's tables. Cross-service identity and product information travel over
authenticated APIs.

## Rationale and consequences

Separate ownership prevents schema coupling, accidental joins across bounded
contexts, and authentication migrations from becoming catalog deployment
dependencies. Local development may place both logical databases on one
PostgreSQL server to reduce resource cost, with separate roles and Flyway
histories. An idempotent Compose job supports both new and existing volumes.
Production may retain logical isolation on one managed cluster or move each
database to a separate instance without application restructuring.
