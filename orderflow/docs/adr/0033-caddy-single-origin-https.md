# ADR 0033: Caddy single-origin HTTPS boundary

Status: accepted for the Phase 10 cloud portfolio configuration.

Use one Caddy endpoint for Next.js and API Gateway, preserving actual Spring OAuth authorization/callback paths and retaining frontend success/failure routes. Caddy handles automatic HTTPS and persistent ACME storage. Only TCP 80/443 are public; the admin API is disabled. Request sizes/timeouts are bounded without buffering SSE. Public actuator/Eureka/internal paths are denied.

Caddy creates forwarding headers from the physical client peer. A dedicated edge alias makes gateway connections use fixed Caddy address `172.31.250.2`; the gateway trusts that exact peer only. Adding a CDN/ALB requires a new trust design. Access logs omit URI and request/response header maps; runtime logs use FATAL to avoid raw error-query disclosure. This sacrifices some diagnostic detail in exchange for a clear secret boundary.

The proxy is a single point of failure and restarts can interrupt streams. Local tests prove routing/header/redaction behavior only; public DNS/ACME, OAuth registration and certificate renewals require live verification after authorization.
