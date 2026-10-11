# Phase 7 operations

Phase 7 makes background work safe across replicas. Inventory expiry, payment
deadline reconciliation, pending delivery assignment, notification recovery and
retention use service-owned PostgreSQL leases and bounded batches. Every attempt
has a correlation ID and `scheduled_job_run` history.

`operations-service` consumes business Kafka topics into an idempotent operational
read model. It never connects to business databases. ADMIN APIs expose the current
overview, daily UTC reports, incidents and operations-job state through the gateway.

Run the optional local monitoring stack with:

```bash
docker compose --profile observability up -d
```

Prometheus is at `localhost:9091` and Grafana at `localhost:3000`. Credentials in
`.env.example` are local-only. Production deployments need managed secrets, TLS,
durable Kafka/PostgreSQL, alert delivery, capacity planning and retention review.
