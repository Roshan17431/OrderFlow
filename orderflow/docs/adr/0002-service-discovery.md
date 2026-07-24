# ADR 0002: Eureka for Phase 1 discovery

## Status

Accepted for local and educational Phase 1 environments.

## Context

OrderFlow must demonstrate client registration, logical service names, and
gateway-side load balancing while remaining runnable in Docker Compose and from
a developer host.

## Decision

Use Spring Cloud Netflix Eureka. The gateway resolves `AUTH-SERVICE` through
Spring Cloud LoadBalancer, while applications publish health-aware registrations.
The registry does not register with itself.

## Benefits

- Service discovery behavior is visible through a dashboard and API.
- Host and Compose deployments share the same logical routing model.
- The approach demonstrates leases, registration, and client-side load balancing.

## Drawbacks

- Eureka is an extra stateful control-plane service to operate.
- Client-side discovery adds configuration and startup convergence time.
- Cloud orchestration platforms may already supply equivalent discovery.

## Consequences

Eureka is not a mandate for every production deployment. A later
Kubernetes-based architecture can replace it with Kubernetes Services and native
DNS/service discovery while keeping the public gateway routes and service
boundaries. Local-only aggressive eviction and disabled self-preservation are
profile-scoped; safer defaults remain outside local and Docker development.
