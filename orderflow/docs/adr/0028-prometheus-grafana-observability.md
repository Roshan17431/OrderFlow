# ADR 0028: Prometheus and Grafana

Micrometer provides a Prometheus pull endpoint. Provisioned local Grafana dashboards use
only bounded labels. The Compose profile is a development demonstration; production needs
secured, durable monitoring, independent capacity, TLS and alert routing.
