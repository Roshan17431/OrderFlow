# Database query and index audit

Phase 8 reviewed product search/vendor lists, order customer/admin queries, inventory, payment, delivery, notification, operations reports, outbox polling, processed-event lookup, and scheduler leases. Collection APIs use `Page`/`Pageable` or explicit bounded limits; no controller/service exposes an unbounded repository `findAll()`.

Entity collections used in list responses are projected, entity-graphed, or fetched only for bounded result sets. No global eager-loading change was introduced. Testcontainers integration tests exercise fresh Flyway migrations and `ddl-auto=validate` for each database-owning service.

Existing versioned migrations contain supporting indexes for unique email, product status/category/vendor filters, order customer/status/time, inventory product/reservation, payment order/customer/status, delivery customer/agent/status, notification user/status, outbox status/next-attempt, processed `event_id`, scheduler lock key/time, and operations metric/report dates. No index gap requiring a Phase 8 migration was found, so no applied migration was edited and no no-op migration was added. Re-run `EXPLAIN (ANALYZE, BUFFERS)` against production-sized representative data before capacity decisions.
