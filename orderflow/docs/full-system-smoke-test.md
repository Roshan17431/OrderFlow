# Full-system smoke test

Run the smoke test only against a freshly built local stack. It creates disposable
users, products, orders, payments, deliveries, notifications, and operational
read-model records.

```bash
./scripts/clean-environment.sh
./scripts/dev-up.sh
docker compose --profile observability up -d
./scripts/full-system-smoke-test.sh
./scripts/performance-smoke.sh
```

`clean-environment.sh` removes OrderFlow's local Compose volumes. Never run it
against an environment containing data that must be retained.

The underlying Phase 1–7 journey verifies registration and login, vendor product
activation, inventory reservation and rejection, cart validation, idempotent order
creation, payment success and decline, signed/replayed webhooks, a partial refund,
inventory compensation, delivery completion, notification deduplication, scheduler
history, operations data, trace metadata, drained outboxes, and unchanged DLT
offsets. The Phase 8 wrapper additionally verifies unauthorized and malformed-JWT
rejection, security headers, explicit CORS behavior, Redis-backed rate limiting,
and the live monitoring configuration.

Every poll has a deadline. A nonzero exit is a failed gate; inspect the named step
and relevant service logs before retrying. The scripts never print access tokens,
refresh tokens, webhook secrets, or credentials.

For machines with less than 6 GiB allocated to Docker, do not run Testcontainers
verification while the full Compose topology is active. Stop Compose first so
Kafka and PostgreSQL test containers are not starved.

