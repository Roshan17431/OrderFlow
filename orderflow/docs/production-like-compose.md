# Production-like Compose

`docker-compose.production-like.yml` is a local hardening exercise, not a cloud production deployment. It overlays the normal Compose file, publishes only the gateway, separates edge/internal/observability networks, runs service filesystems read-only with a bounded `/tmp`, applies memory/JVM limits, restricts Actuator, enables secure cookies and HSTS, and disables payment simulation.

Create protected RSA PEM files outside the repository, then create the external Docker secrets:

```bash
docker secret create orderflow-jwt-private /protected/path/jwt-private.pem
docker secret create orderflow-jwt-public /protected/path/jwt-public.pem
docker compose -f docker-compose.yml -f docker-compose.production-like.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.production-like.yml up -d
```

Supply all database, Redis, Grafana, OAuth, provider, and CORS values from an uncommitted environment file or secret manager. Values in `.env.example` are local placeholders and are not production credentials. Use a real payment provider configuration before enabling payments; the override intentionally supplies no webhook secret and disables the simulator. Replace `https://orderflow.invalid` with explicit HTTPS frontend origins.

The local memory caps are regression guidance, not sizing guarantees. Measure workload, GC, connection pools, Kafka lag, and outbox latency before production sizing. Termination must allow at least the configured 20-second Spring shutdown phase.
