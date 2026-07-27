# Product Catalog

## Domain

Products use UUID identity, a per-vendor normalized SKU, globally unique stable
slug, `BigDecimal` price at scale 2, validated ISO-style supported currency,
category, UTC timestamps, and JPA optimistic versioning. Lifecycle is
`DRAFT -> ACTIVE <-> INACTIVE -> ARCHIVED`; archived products cannot return.
Public purchasability means both product and category are active and does not
represent stock.

Categories use stable UUIDs, normalized unique names/slugs, active/inactive
status, timestamps, and optimistic versions. Electronics, Books, Home and
Kitchen, and Clothing are seeded by Flyway. An active product prevents category
deactivation.

## API behavior

Public list endpoints support `q`, category, minimum/maximum price, currency,
allowlisted sort, and bounded page/size parameters. JPA Specifications use
parameterized criteria and fetch category data without per-item queries.
Unsupported sorts, currencies, price ranges, or page bounds return standard
domain errors.

Vendor writes derive ownership from JWT `sub`. Slugs are generated only when
absent and do not drift on later name edits. SKU is trimmed/uppercased and
permits letters, digits, `-`, `_`, and `.`. Database constraints close
uniqueness races, while exception mapping hides SQL and constraint names.
Expected versions are required for updates and transitions.

`POST /api/products/resolve` deduplicates up to 100 UUIDs, executes one batch
query, and returns an entry for every unique request ID. Missing or private
products are represented as `purchasable=false` without exposing vendor fields.

## Persistence

Flyway owns the `orderflow_product` schema and Hibernate runs only in
`ddl-auto=validate` mode. The service never reads `orderflow_auth` tables. Local
Compose's bootstrap job creates the product role and database idempotently,
including when the PostgreSQL volume predates Phase 3.
