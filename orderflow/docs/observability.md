# Observability

Micrometer exposes low-cardinality service, Kafka, outbox, scheduler and reconciliation
metrics at `/actuator/prometheus`. The optional Compose profile provisions Prometheus,
local demonstration alerts, one datasource and three Grafana dashboards. Core services
do not depend on either monitoring component. Production alert routing is intentionally absent.
