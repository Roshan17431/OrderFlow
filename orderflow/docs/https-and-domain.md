# HTTPS and domain setup

`infrastructure/caddy/Caddyfile` defines one public HTTPS origin from `DOMAIN_NAME`, automatic ACME issuance/renewal and HTTP-to-HTTPS redirection. A real owned domain, correct A/AAAA DNS, reachable TCP 80/443, accurate time and outbound ACME access are prerequisites. An example `.invalid` hostname proves configuration parsing only; it cannot prove a public certificate. DNS changes, domain purchases and billable infrastructure require the user's authorization.

After authorization, point the selected domain A record to the approved public IPv4/Elastic IP. Add AAAA only if the host actually receives routed IPv6; stale AAAA records can break challenges. External DNS providers work without Route 53. Set `ACME_EMAIL` to the operator address. Preserve `/opt/orderflow/data/caddy/data` across container changes; it contains ACME account material and TLS private keys. Restrict host access and back it up securely. Do not delete storage or repeatedly restart issuance to fix rate limits.

Routes retain their original paths: `/api/**`, `/oauth2/authorization/**` and `/login/oauth2/code/**` reach the API Gateway. `/oauth2/success` and `/oauth2/failure` reach Next.js. Configure actual Google/GitHub applications with `https://<domain>/login/oauth2/code/google` and `https://<domain>/login/oauth2/code/github`; inspect provider settings before enabling OAuth. Do not point the browser at container DNS or localhost. Actuator, Eureka and internal routes are denied at the public boundary. SSE responses flush immediately; the backend notification timeout remains bounded at 30 minutes.

Caddy removes client `Forwarded`, all `X-Forwarded-*`, `X-Real-IP`, `True-Client-IP` and `CF-Connecting-IP` before proxying. It creates one X-Forwarded-For from its physical remote peer and sets the approved Host, scheme and forwarded host. Gateway forwarding trust is limited to the fixed Caddy address. This assumes direct Internet-to-Caddy access: introducing an ALB/CDN changes the trust boundary and requires an explicit CIDR/peer design and new spoofing tests. Correlation headers pass to the existing application validation. No proxy retry of mutating requests is configured.

Requests have a 2 MB body bound, 32 KB header bound, ten-second header timeout, thirty-second request body timeout, five-second upstream dial timeout and thirty-five-second response-header timeout. SSE is not subject to an ordinary sixty-second response read timeout. HSTS, nosniff and referrer policy are set; HSTS does not include subdomains because domain-wide control has not been established. The Caddy admin API is off; restart the proxy to apply configuration and account for stream interruption.

Access logs omit the entire URI and request/response header maps, preventing OAuth codes, tokens, Authorization, Cookie and Set-Cookie from appearing. Rotated JSON files retain three 10 MiB files for up to 72 hours. Runtime error logging is restricted to FATAL because ordinary reverse-proxy error records may include raw URIs; redacted access logs retain status/duration/client fields. Never enable debug or credential logging with live secrets. For safe diagnostics use configuration validation, private probes and synthetic requests without tokens, rather than increasing production log verbosity.

Validate on the host with the pinned Caddy image from Compose and a synthetic environment, then verify publicly only after deployment authorization:

```sh
curl --fail --silent --show-error --max-time 15 https://<domain>/
curl --silent --show-error --max-time 15 -I http://<domain>/
openssl s_client -connect <domain>:443 -servername <domain> </dev/null
```

Check certificate hostname/chain/expiry, HTTP redirect, actual OAuth callbacks, Secure cookies, blocked `/actuator`, gateway readiness through private administration, and authenticated SSE. Container validation and local HTTP proxy tests do not establish any of those live cloud outcomes. Use the certificate failure runbook if issuance or renewal fails.

References: [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https), [proxy forwarding and streaming](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy), [log filters](https://caddyserver.com/docs/caddyfile/directives/log), [Caddy 2.11.7 release](https://github.com/caddyserver/caddy/releases/tag/v2.11.7).
