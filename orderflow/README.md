# OrderFlow

OrderFlow is a Java 21 and Spring Boot microservice platform. Phase 4 adds
authoritative inventory, durable orders, and reliable Kafka workflows to the
Phase 1 platform, Phase 2 authentication, and Phase 3 product/cart foundation.

## Architecture

```mermaid
flowchart LR
    Client["Browser or API client"] --> Gateway["API Gateway :8080"]
    Gateway -->|"public catalog and protected management"| Product["Product service :8082"]
    Gateway -->|"authenticated cart API"| Cart["Cart service :8083"]
    Gateway -->|"vendor/admin inventory API"| Inventory["Inventory service :8084"]
    Gateway -->|"customer/admin order API"| Order["Order service :8085"]
    Gateway --> Auth["Auth service :8081"]
    Product --> ProductDB[("orderflow_product")]
    Inventory --> InventoryDB[("orderflow_inventory")]
    Order --> OrderDB[("orderflow_order")]
    Product --> Redis[("Redis :6379")]
    Cart --> Redis
    Inventory --> Redis
    Order --> Redis
    Cart -->|"batch resolve via Eureka and LoadBalancer"| Product
    Inventory -->|"management access via Eureka"| Product
    Order -->|"authoritative cart via Eureka"| Cart
    Order -->|"order events + orderId key"| Kafka["Kafka :9092"]
    Kafka --> Inventory
    Inventory -->|"inventory events + orderId key"| Kafka
    Kafka --> Order
    Auth --> AuthDB[("orderflow_auth")]
    Auth --> Redis
    Auth -. "optional audit transport" .-> Kafka
    Gateway --> Eureka["Eureka :8761"]
    Auth --> Eureka
    Product --> Eureka
    Cart --> Eureka
    Inventory --> Eureka
    Order --> Eureka
```

Every protected service independently validates RS256 access tokens, issuer,
expiry, required claims, revoked `jti` values, and the user's current token
version. Ownership comes from the JWT subject. Client-supplied identity headers
are stripped by the gateway and never determine product or cart ownership.

## Modules and ports

| Module | Port | Responsibility |
| --- | ---: | --- |
| `platform-contracts` | — | Shared `ApiError`, error codes, and correlation contracts |
| `event-contracts` | — | Framework-light, immutable, versioned workflow events |
| `service-registry` | 8761 | Eureka registry |
| `api-gateway` | 8080 | Reactive routing, JWT security, CSRF, CORS, safe errors |
| `auth-service` | 8081 | Users, credentials, roles, JWTs, refresh sessions, OAuth2 |
| `product-service` | 8082 | Categories, vendor products, lifecycle, search, Redis caching |
| `cart-service` | 8083 | JWT-owned Redis carts, Lua mutations, product snapshot validation |
| `inventory-service` | 8084 | Stock adjustments, reservations, releases, expiration |
| `order-service` | 8085 | Durable orders, idempotency, lifecycle, Kafka coordination |

PostgreSQL is exposed on 5432, Redis on 6379, and Kafka's host listener on 9092.

## Product catalog

New products begin in `DRAFT`. A vendor may manage only products whose
`vendorId` equals the JWT subject; an administrator may manage any product.
Activation requires a valid non-archived product and an active category.
`INACTIVE` products can be reactivated, while `ARCHIVED` is a permanent normal
API transition. Public reads return only `ACTIVE` products in `ACTIVE`
categories. A vendor querying another vendor's private management resource gets
404; explicit role denial gets 403.

Categories are administrator-managed. The migration seeds stable Electronics,
Books, Home and Kitchen, and Clothing UUIDs. A category containing active
products cannot be deactivated and returns `CATEGORY_IN_USE`.

Public endpoints:

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/products` | Search, category/price/currency filters, allowlisted sort, bounded paging |
| GET | `/api/products/{id}` | Publicly visible product only |
| GET | `/api/products/slug/{slug}` | Publicly visible product only |
| GET | `/api/categories` | Active categories |
| GET | `/api/categories/{slug}/products` | Public products in an active category |

Protected endpoints:

| Access | Endpoints |
| --- | --- |
| Any authenticated user | `POST /api/products/resolve`, all `/api/cart/**` |
| `VENDOR` or `ADMIN` | all `/api/vendor/products/**` |
| `ADMIN` | all `/api/admin/categories/**` and `/api/admin/products/**` |

The resolution API accepts at most 100 product IDs and uses one batch database
query. It reports purchasability without exposing vendor-private fields or
inventing inventory. Inventory-service uses a protected management-access
endpoint to validate vendor ownership without reading the product database.

Product data lives in the separate logical database `orderflow_product`;
authentication data remains in `orderflow_auth`. Compose can host both on one
PostgreSQL server for local efficiency. An idempotent bootstrap container also
creates or repairs the product role/database for existing Phase 2 volumes.

Public detail, slug, search, and category results use JSON Redis caches. Keys
are under `orderflow:catalog:v1`, nulls are not cached, and default TTLs are
five minutes for details/slugs, two minutes for searches, and ten minutes for
categories. Every catalog mutation increments the exact `catalog:version` key.
Search and category keys include that version, providing broad invalidation
without `KEYS`, scans, or cache-key enumeration. Targeted product caches are
evicted directly. Redis cache failures are logged safely; PostgreSQL reads and
committed writes remain authoritative. Product readiness therefore includes
PostgreSQL but deliberately excludes optional Redis caching.

## Shopping cart

Cart ownership is always the JWT subject. The service accepts no user ID in a
path, query, request body, or client identity header. A cart is a namespaced
Redis hash at `orderflow:cart:{subject}` with reserved metadata fields and one
JSON item field per product. It never contains access tokens, cookies, or
refresh credentials.

Add, quantity update, remove, and validation replacement use Redis Lua scripts.
They atomically enforce currency consistency, quantity and distinct-item limits,
version increments, timestamps, and TTL refresh. The defaults are 100 distinct
products, quantity 1–99 per product, and a rolling 30-day TTL. Removing the last
item deletes the key; reading a missing cart returns an empty HTTP 200 response.

Each item stores a product name, price, currency, and product-version snapshot.
This keeps stored carts readable while product-service is unavailable.
`POST /api/cart/validate` resolves all IDs in one request, refreshes snapshots,
marks unavailable products without deleting them, and reports
`PRICE_CHANGED`/`PRODUCT_UNAVAILABLE`. Order creation repeats authoritative cart
validation and recalculates every line and order total. Inventory availability
is then resolved asynchronously through the reservation workflow.

Cart-to-product requests use `http://PRODUCT-SERVICE` through Eureka and Spring
Cloud LoadBalancer. Authorization and the existing correlation ID are
propagated, while cookies are removed. Configured connection/read timeouts,
Resilience4j retry, and a circuit breaker protect temporary connection,
timeout, 502, 503, and 504 failures only. Stored cart reads make no product call.
Mutations or validation return safe `PRODUCT_SERVICE_UNAVAILABLE` errors when
resolution cannot be trusted. Cart readiness includes Redis but not
product-service.

## Inventory, orders, and Kafka workflows

Inventory-service owns whole-number stock in PostgreSQL. For each product,
`availableQuantity = onHandQuantity - reservedQuantity`; database constraints
prevent negative values and prevent reserved stock from exceeding on-hand
stock. `VENDOR` users may view and adjust only their products, while `ADMIN`
may manage any product. Every adjustment stores its actor, reason, resulting
quantities, timestamp, and correlation ID.

Reservations are all-or-nothing. Inventory locks requested product rows in
stable UUID order using PostgreSQL pessimistic locks, treats a missing row as
zero stock, and creates one `RESERVED` or `REJECTED` reservation per order.
Cancellation releases reserved quantities once. A bounded poller can expire
stale reservations and publish a release event. Redis is used only for token
revocation checks, never as the inventory consistency lock.

Order-service accepts only `expectedCartVersion`; customer identity, items,
names, quantities, and prices come from the authenticated cart-service
response. It validates the cart, recalculates `BigDecimal` totals, and writes
the order, immutable item snapshots, initial history, idempotency result, and
`ORDER_CREATED` outbox row in one transaction. `Idempotency-Key` is required
for create and cancel operations. Repeating the same normalized request returns
the same order; reusing a key for a different request returns HTTP 409.

```mermaid
stateDiagram-v2
    [*] --> INVENTORY_PENDING
    INVENTORY_PENDING --> INVENTORY_RESERVED: INVENTORY_RESERVED
    INVENTORY_RESERVED --> PAYMENT_PENDING: payment required
    INVENTORY_PENDING --> INVENTORY_REJECTED: reservation failed
    INVENTORY_PENDING --> CANCEL_REQUESTED: cancel
    INVENTORY_RESERVED --> CANCEL_REQUESTED: cancel
    PAYMENT_PENDING --> CANCEL_REQUESTED: cancel
    CANCEL_REQUESTED --> CANCELLED: inventory released or no reservation
```

`PAYMENT_PENDING` means only that inventory is reserved; it is not paid or
completed. Phase 4 deliberately leaves the cart unchanged.

Order-service publishes `ORDER_CREATED` and `ORDER_CANCEL_REQUESTED` to
`orderflow.order-events.v1`. Inventory-service publishes
`INVENTORY_RESERVED`, `INVENTORY_RESERVATION_FAILED`, and
`INVENTORY_RELEASED` to `orderflow.inventory-events.v1`. Every message uses
`orderId` as its Kafka key. Derived events preserve the correlation ID and use
the triggering event ID as `causationId`.

Both services use PostgreSQL transactional outboxes, bounded
`FOR UPDATE SKIP LOCKED` claims, exponential retry, and inspectable failed
rows. Consumers write `(eventId, consumerName)` to a processed-event table in
the same transaction as the business change, so duplicate publication is
harmless. Invalid or unsupported events are retried a bounded number of times
and then sent to the matching `.DLT` topic. This is at-least-once processing,
not exactly-once delivery.

Service database ownership is strict: auth, product, inventory, and order each
have separate logical databases and credentials. Local Compose hosts those
databases on one PostgreSQL server, while production may isolate servers
without changing service code. No service reads another service's tables.

## Build, test, and run

Prerequisites are a Java 21-compatible JDK, Docker Engine with Compose v2,
Bash, and `curl`. Docker must be available for the PostgreSQL and Redis
Testcontainers integration suites.

```bash
./mvnw -B clean verify
docker compose config
./scripts/dev-up.sh
./scripts/smoke-test.sh
./scripts/dev-down.sh
```

Focused verification:

```bash
./mvnw -B -pl product-service -am verify
./mvnw -B -pl cart-service -am verify
./mvnw -B -pl api-gateway -am test
./mvnw -B -pl inventory-service -am verify
./mvnw -B -pl order-service -am verify
```

The smoke test preserves Phase 1–3 checks, then creates stock and two orders,
observes reservation success and failure through real Kafka, checks create and
cancel idempotency, waits for release and cancellation, verifies authorization,
confirms the outboxes drain, and checks both DLT topics. It never prints tokens.

## Configuration and secrets

`.env.example` contains local placeholders only. Never commit `.env`, cookie
jars, copied JWTs, OAuth secrets, passwords, private keys, keystores, or PEM key
material. Docker's ephemeral RSA keys are local-development behavior only;
production must mount protected keys and disable ephemeral generation.

Important Phase 4 variables in addition to the Phase 1–3 settings:

| Variables | Purpose |
| --- | --- |
| `PRODUCT_POSTGRES_HOST`, `PRODUCT_POSTGRES_PORT`, `PRODUCT_POSTGRES_DB` | Product-owned database location |
| `PRODUCT_POSTGRES_USER`, `PRODUCT_POSTGRES_PASSWORD` | Product database credential |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Shared server with namespaced data |
| `EUREKA_DEFAULT_ZONE` | Service discovery endpoint |
| `APP_AUTH_JWK_SET_URI`, `APP_JWT_ISSUER` | Independent JWT validation |
| `APP_CATALOG_DEFAULT_CURRENCY`, `APP_CATALOG_SUPPORTED_CURRENCIES` | Catalog currency policy |
| `APP_CATALOG_MAX_PAGE_SIZE` | Public/vendor page bound |
| `APP_CATALOG_PRODUCT_CACHE_TTL`, `APP_CATALOG_SEARCH_CACHE_TTL`, `APP_CATALOG_CATEGORY_CACHE_TTL` | Cache TTLs |
| `APP_CART_TTL`, `APP_CART_MAX_DISTINCT_ITEMS`, `APP_CART_MAX_QUANTITY_PER_ITEM` | Cart expiration and limits |
| `APP_PRODUCT_CLIENT_CONNECT_TIMEOUT`, `APP_PRODUCT_CLIENT_READ_TIMEOUT` | Cart downstream timeouts |
| `INVENTORY_POSTGRES_*`, `ORDER_POSTGRES_*` | Service-owned database locations and credentials |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers used by consumers and outbox publishers |
| `APP_ORDER_EVENTS_TOPIC`, `APP_INVENTORY_EVENTS_TOPIC` | Workflow topics |
| `APP_ORDER_EVENTS_DLT`, `APP_INVENTORY_EVENTS_DLT` | Dead-letter topics |
| `APP_INVENTORY_RESERVATION_TTL` | Reservation lifetime |
| `APP_INVENTORY_EXPIRATION_POLL_DELAY`, `APP_INVENTORY_EXPIRATION_BATCH_SIZE` | Bounded expiration polling |
| `APP_CART_CLIENT_CONNECT_TIMEOUT`, `APP_CART_CLIENT_READ_TIMEOUT` | Order-to-cart timeouts |
| `APP_OUTBOX_POLL_DELAY`, `APP_OUTBOX_BATCH_SIZE`, `APP_OUTBOX_MAX_ATTEMPTS` | Outbox polling and retry bounds |
| `APP_OUTBOX_LOCK_TIMEOUT`, `APP_OUTBOX_RETRY_BASE_DELAY` | Stale-claim recovery and backoff |

All handled failures use the shared `ApiError` and preserve
`X-Correlation-ID`. Health, liveness, readiness, info, metrics, and Prometheus
are exposed; secret-bearing actuator endpoints are not.

Detailed design:

- [Phase 4 orders and inventory](docs/phase-4-orders-inventory.md)
- [Inventory consistency](docs/inventory-consistency.md)
- [Order lifecycle](docs/order-lifecycle.md)
- [Kafka event contracts](docs/kafka-event-contracts.md)
- [Transactional outbox](docs/transactional-outbox.md)
- [Idempotent consumers](docs/idempotent-consumers.md)
- [Phase 3 product and cart](docs/phase-3-product-cart.md)
- [Product catalog](docs/product-catalog.md)
- [Redis caching](docs/redis-caching.md)
- [Cart consistency](docs/cart-consistency.md)
- [Phase 2 authentication](docs/phase-2-authentication.md)
- [JWT and key management](docs/jwt-and-key-management.md)
- [API error contract](docs/api-error-contract.md)

## Phase 5 preview

Phase 5 can consume the existing `PAYMENT_PENDING` state and versioned event
contracts to add payment authorization and completion without restructuring
orders or inventory. Payment failure, refunds, provider integration, and safe
post-payment cart clearing are intentionally deferred, as are delivery,
notifications, coupons, reviews, recommendations, and product media.
