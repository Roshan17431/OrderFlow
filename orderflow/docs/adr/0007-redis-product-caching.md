# ADR 0007: Redis Product Caching

- Status: Accepted
- Date: 2026-07-26

## Decision

Use Redis/Spring Cache for public product detail, slug, search, and category
DTOs. Detail entries default to five minutes, search to two, and categories to
ten. Catalog mutations evict known detail keys and increment
`catalog:version`; list keys include that version.

## Rationale and consequences

Redis supports consistent caching across replicas. The TTLs constrain abandoned
versioned entries while reflecting the different volatility of products,
searches, and categories. Version invalidation avoids unsafe key scans. Values
are JSON, nulls and private products are not cached, and keys are namespaced.
PostgreSQL remains authoritative: Redis errors cause safe cache misses and do
not roll back committed writes, so Redis is not a product-readiness dependency.
