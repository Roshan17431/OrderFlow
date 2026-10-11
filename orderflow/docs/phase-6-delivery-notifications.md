# Phase 6: delivery and notifications

Phase 6 adds delivery execution and durable user notifications without coupling either service to another service's database. `delivery-service` owns `orderflow_delivery` on port 8087 and `notification-service` owns `orderflow_notification` on port 8088. Both register with Eureka, validate the same RS256 access-token contract independently, and expose readiness and Prometheus endpoints.

After a successful payment, order-service stores `PAID` as its financial status and independently changes fulfillment to `DELIVERY_PENDING`. Its transactional outbox publishes `DELIVERY_REQUESTED` with an immutable shipping-address snapshot. Delivery events advance fulfillment through assignment, pickup, out-for-delivery, and delivery; they never rewrite payment or refund state.

Delivery-service creates one delivery per order, keeps status history, assigns available agents with database row locks, and publishes lifecycle events through its outbox. Notification-service consumes payment and delivery events idempotently, stores in-app notifications first, optionally creates email/SMS deliveries, and uses Kafka-backed dispatch plus deterministic local simulators. SSE is an acceleration channel only: persistent notification reads remain authoritative.

Local commands:

```bash
cp .env.example .env
docker compose up --build -d
./scripts/smoke-test.sh
docker compose down
```

No SMTP, SMS, or payment-provider credential is required. Local values are simulators and examples, and `.env` must remain uncommitted.

Phase 7 can add returns, carrier integrations, route optimization, and production notification providers behind the existing provider and event boundaries.
