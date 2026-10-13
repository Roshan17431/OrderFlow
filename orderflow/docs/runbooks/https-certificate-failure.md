# HTTPS certificate failure

1. Confirm the failing hostname and exact client certificate error. Check DNS A/AAAA points to the approved endpoint, expiry/SAN/chain with `openssl s_client`, EC2 status and Caddy liveness privately.
2. Inspect allowed TCP 80/443, accurate host time, outbound DNS/ACME reachability, `DOMAIN_NAME` and `ACME_EMAIL`. Verify no stale AAAA or other proxy sends challenges elsewhere.
3. Validate the Caddyfile using its pinned image. Inspect persistent certificate data mount, permissions and free disk without printing TLS private keys. Verify existing `/opt/orderflow/data/caddy/data` was not replaced by an empty volume.
4. Caddy access logs deliberately redact URI/header maps; runtime logging is FATAL to avoid raw OAuth query disclosure. Use synthetic token-free requests and configuration/probe checks for diagnostics. Do not enable production debug logging while real OAuth traffic is active.
5. Honor ACME rate-limit/retry windows. Use an isolated staging test with synthetic hostname/config if necessary; never wipe certificate storage to force repeated issuance. DNS edits or domain changes require authorization.
6. After a safe correction, restart only the proxy during an agreed interruption. Its admin endpoint is disabled, and active SSE sessions may reconnect.
7. Verify public trusted certificate, expiry/hostname/chain, HTTP redirect, browser Secure cookie flow and actual OAuth callback URLs. Certificate validation inside a container with `.invalid` DNS is not evidence of public HTTPS recovery.
