# AWS Mumbai real-backend acceptance runbook

Prepared 2026-10-27. **Every AWS/browser/API/reboot/rollback acceptance case below is NOT RUN.** This document authorizes no commands against AWS. Use it only after the specific resource, publication, deployment, DNS and synthetic-data actions have been approved. Local isolated evidence is in the dated readiness report.

## Target and boundaries

- Region `ap-south-1`; dedicated `cloud-demo` deployment, used for staging acceptance. Existing deployment and on-demand scripts deliberately reject another environment label.
- One `m7i.2xlarge`, x86_64, 8 vCPU / 32 GiB; application release platform `linux/amd64`. Run Chromium on the approved tester workstation, not inside the app host.
- All 11 Boot services, frontend, PostgreSQL, Redis, Kafka, Caddy and observability must be healthy. No mocked responses, local-fixture completion helper, direct database state changes or manually manufactured order events may stand in for the commerce journey.
- Payment/email/SMS use existing simulators. OAuth stays disabled for this acceptance unless real callback registrations and credentials receive separate approval.
- Restrict HTTPS to the tester's fixed public IPv4 `/32`. Keep only Caddy HTTP-01 challenge/redirect on public port 80. No SSH, database, Redis, Kafka, Eureka, actuator, Grafana or Prometheus public ingress.
- The report must identify commit, 12 ECR digests, platform, account, region, instance, UTC start/end and unique synthetic run IDs. Protect evidence files with mode 0600.

## Before first deployment

1. Obtain the approvals in `aws-staging-readiness-2026-10-27.md`. The current tree is uncommitted: the publishing workflow checks out a Git SHA and would otherwise build the old baseline. Do not label the current baseline SHA as a migrated release.
2. After separately authorized Git publication, require clean-SHA Java 21 CI, unchanged OWASP gate, npm audits, image builds, SBOMs and a reviewed OS/container scan. Publish all 12 **amd64** images to the selected account's Mumbai ECR repositories only when image publication is authorized. Record digests in the release manifest.
3. Review the authenticated Terraform plan using the completed Mumbai example, correct remote state, current AMI and approved AZ. Reject unexpected replacement/destruction, extra public ingress, a conflicting existing ECR/OIDC owner, or any DNS change. Apply remains a separate explicit approval.
4. Approve an existing hostname and stable EIP. Terraform `enable_route53=false` does not configure DNS. The public A record must already be correct or receive a separate DNS approval; remove conflicting AAAA/CDN proxy paths. An unapproved `.invalid` placeholder cannot obtain a certificate.
5. On the provisioned host, verify EBS identity/mount, encryption, free disk, Docker/Compose, chrony, SSM connectivity and **SSM Agent >=3.3.4851.0**. Refresh a preinstalled old agent before granting access. Use the approved operator SSO role; no static AWS keys.
6. Provision the five SSM SecureStrings **outside Terraform/state** under `/orderflow/cloud-demo/runtime/`: `runtime.env`, `jwt-private.pem`, `jwt-public.pem`, `backup.key`, `cloud-accounts.json`. Complete `infrastructure/cloud/staging-mumbai.env.example`; keep account/region/domain consistent with the manifest. Use distinct random database/Redis/Grafana/webhook/backup secrets. Preserve `APP_JWT_KEY_ID`, Kafka cluster ID and all existing key bytes on subsequent deployments.
7. JWT files must be UID/GID 10001, mode 0400, read-only mounts; protected configuration/accounts/backup-key files mode 0600 in the private config directory. `fetch-secrets.sh` rejects silent JWT/backup-key rotation. Never upload populated files or copy them into evidence.
8. The host-side deployed verifier calls the public hostname. Restricting ingress also blocks its public-IP loopback path. After independently checking external DNS from the tester, configure the **EC2 host only** to resolve that exact approved hostname to `127.0.0.1` in `/etc/hosts`. Keep the real HTTPS hostname/SNI and certificate verification. Do not disable TLS checks or change the tester's public DNS resolution. The on-demand status check already uses curl `--resolve`.
9. Only first installation may use `--initial-demo-seed`. Its synthetic accounts use distinct `@example.test` addresses and protected passwords, with explicit trusted-host role grants. Existing business data and roles must not be reset. If another pending delivery or busy fixture agent exists, the journey deliberately aborts rather than taking someone else's order.

For a separately authorized first deployment, the existing remote entrypoint accepts the target and immutable release artifact; it fetches secrets, backs up, starts containers, seeds only if explicitly requested, and runs the real API journey. Do not bypass its manifest/account/schema gates. Review an invocation against `scripts/deploy/remote-entrypoint.sh` and the protected GitHub deploy environment before dispatching it.

## Real browser and API execution

Run the browser from the allowlisted tester machine after installing the repo's locked dependencies and Chromium. Prepare a private local copy of the three synthetic operator accounts; do not save Playwright storage state, HAR, traces or videos with real tokens. The suite disables traces/video for cloud E2E and masks screenshots; review all retained artifacts before sharing.

After approval, fill ordinary nonsecret shell variables `APPROVED_ACCOUNT`, `APPROVED_HOSTNAME`, `PRIVATE_ACCOUNT_FILE`, `INSTANCE_ID`, `APPROVED_PROFILE` and `RELEASE_DIR`. Never source a shell file containing arbitrary copied secret text.

```sh
# Tester workstation; prerequisites above must already be satisfied.
export AWS_REGION=ap-south-1
export AWS_ACCOUNT_ID="$APPROVED_ACCOUNT"
export ORDERFLOW_TARGET_ENVIRONMENT=cloud-demo
export ORDERFLOW_CLOUD_E2E=true
export E2E_BASE_URL="https://$APPROVED_HOSTNAME"
export ORDERFLOW_PUBLIC_URL="$E2E_BASE_URL"
export ORDERFLOW_CLOUD_ACCOUNTS_FILE="$PRIVATE_ACCOUNT_FILE"
export PLAYWRIGHT_EXTERNAL_SERVER=true
# Do not set ORDERFLOW_LOCAL_TLS_TEST or ignore HTTPS errors for AWS.
cd "$RELEASE_DIR/frontend"
npm run test:e2e:cloud
```

The browser test creates an isolated category/product and stocks 10 units, then:

| Case | Required evidence | AWS result |
|---|---|---|
| Four-role login/customer registration | Real UI, role dashboards, HttpOnly/Secure/SameSite refresh cookie, no tokens in browser storage | NOT RUN |
| Browse/cart/checkout | Actual product and quantity 2; idempotent asynchronous order | NOT RUN |
| Reservation before confirmation | `RESERVED`; stock `[onHand,reserved,available]=[10,2,8]` | NOT RUN |
| Simulated payment | PROCESSING then signed backend callback; order PAID; inventory COMMITTED | NOT RUN |
| Assignment | Dedicated idle agent receives the exact fixture order/delivery | NOT RUN |
| Pickup | Before the next delivery action: reservation FULFILLED, stock `[8,0,8]` | NOT RUN |
| Delivery | PICKED_UP → OUT_FOR_DELIVERY → DELIVERED, customer tracking agrees | NOT RUN |
| Refund/notifications/reporting | Real partial refund through signed callback, frontend and operations totals agree | NOT RUN |
| Logout/mobile | Session cleared, private routes guarded, role workspaces fit mobile viewports | NOT RUN |

Run the complementary API journey on the host through its real Caddy HTTPS origin, using the protected host account file and private Prometheus. It creates a separate product with 5 units and orders 2; expected final quantities `[3,0,3]`.

```sh
# Host shell via approved SSM session, not SSM Run Command arguments containing secrets.
umask 077
export AWS_ACCOUNT_ID="$APPROVED_ACCOUNT"
export AWS_REGION=ap-south-1
export ORDERFLOW_TARGET_ENVIRONMENT=cloud-demo
export ORDERFLOW_CLOUD_E2E=true
export ORDERFLOW_PUBLIC_URL="https://$APPROVED_HOSTNAME"
export ORDERFLOW_CLOUD_ACCOUNTS_FILE=/opt/orderflow/config/cloud-accounts.json
export ORDERFLOW_PROMETHEUS_URL=http://127.0.0.1:9090
export ORDERFLOW_JOURNEY_REPORT="/opt/orderflow/backups/acceptance-UNIQUE-RUN.json"
cd "$RELEASE_DIR"
scripts/cloud/verify-journey.sh
```

This API verifier requires all eleven fresh application scrapes, no service restart during the run, actual authenticated SSE tied to a persisted customer notification, CSRF rejection, refresh rotation, revoked-token rejection, operations projection updates, zero failed outbox and DLT publications, and drained outboxes after the final actions. A connected SSE handshake alone is insufficient. Archive its actual exit status and private JSON output, not an inferred PASS.

## Negative payment/refund and authorization matrix

Use fresh synthetic customers/orders for each destructive scenario. Retain a fixture-ID ledger; do not mutate records belonging to existing customers. These are **operator acceptance cases**, not additional automated coverage already provided by the positive browser suite. Execute via the real UI where available and the guarded `cloud_common.Client` API client otherwise, with authenticated cookies/CSRF and unique idempotency keys. Preserve failed responses' safe code/status only, not bearer tokens or customer bodies.

| Case | Action / expected outcome | AWS result |
|---|---|---|
| Declined payment | UI “Simulate decline” (`tok_declined`) on a newly reserved order: confirmation HTTP 402 / PAYMENT_DECLINED; payment FAILED, order PAYMENT_FAILED; reservation RELEASED, reserved quantity returns to 0, on-hand unchanged; no delivery | NOT RUN |
| Additional authentication | UI “Simulate authentication required” (`tok_requires_action`): HTTP 202 / REQUIRES_ACTION; do not credit PAID without a valid signed completion | NOT RUN |
| Provider timeout/unavailability | API confirmation tokens `tok_timeout` / `tok_temporary_failure`: HTTP 503 with PAYMENT_PROVIDER_TIMEOUT / PAYMENT_PROVIDER_UNAVAILABLE; no fabricated success, duplicate charge or delivery; retry only with the original idempotency key | NOT RUN |
| Order/confirm idempotency | Same key and exact body returns same resource/result; altered body with same key returns the documented 409 conflict | NOT RUN |
| Invalid webhook signature | POST `/api/payments/webhooks/simulator`, current timestamp, synthetic event ID, invalid signature: HTTP 401 / PAYMENT_WEBHOOK_SIGNATURE_INVALID; no state change | NOT RUN |
| Invalid/stale webhook time | Malformed or outside ±5-minute timestamp: HTTP 400 / PAYMENT_WEBHOOK_TIMESTAMP_INVALID; no state change | NOT RUN |
| Signed replay | From the trusted host only, sign fixture payload bytes using existing verifier's `HMAC-SHA256(timestamp + "." + rawBody)` convention, without exposing the secret. Same event ID/body is idempotent; same ID with different **validly signed** body is HTTP 409 / PAYMENT_WEBHOOK_EVENT_CONFLICT. Totals/outbox must not double | NOT RUN |
| Invalid refund amount | Admin refund amount zero/negative: HTTP 400 (DTO validation or REFUND_AMOUNT_INVALID); no refund row/financial change | NOT RUN |
| Over-refund | Admin amount above remaining refundable balance: HTTP 409 / REFUND_AMOUNT_EXCEEDS_AVAILABLE; unchanged totals | NOT RUN |
| Wrong refund state | Refund a declined/cancelled payment: HTTP 409 / REFUND_STATE_CONFLICT | NOT RUN |
| Refund identity/idempotency | Customer cannot call admin refund API (403); identical admin retry returns same refund; changed amount/reason with same key gives 409 / REFUND_IDEMPOTENCY_CONFLICT | NOT RUN |
| Role/ownership | Anonymous protected API 401; customer/agent admin API 403; another customer's order/payment inaccessible using documented 404 concealment; foreign vendor/agent cannot read or mutate the fixture's private records | NOT RUN |
| CSRF/logout/SSE | Missing CSRF on cookie mutation 403; old bearer after logout 401; anonymous SSE 401; authenticated event belongs to the actual customer and no query-string token is used | NOT RUN |
| External boundary | Nonallowlisted machine cannot establish HTTPS; HTTP only redirects/challenges; `/actuator`, `/eureka`, internal paths return 404 through Caddy | NOT RUN |

Do not inject malformed messages into shared Kafka topics just to prove a DLT exists. A poison-event/replay exercise needs its own isolated topic/group and approval. Baseline normal acceptance must keep all real application DLTs empty, not merely show a zero process counter after a restart.

## Monitoring, outboxes and resource measurements

SSM port forwarding to localhost Prometheus/Grafana requires no inbound administration port. Restrict the operator to the intended instance and `AWS-StartPortForwardingSession`; remote-host forwarding is unnecessary here.

```sh
# Tester: open one private monitoring tunnel after approval.
aws ssm start-session --profile "$APPROVED_PROFILE" --region ap-south-1 \
  --target "$INSTANCE_ID" --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["9090"],"localPortNumber":["9090"]}'
```

Record baseline, browser/API peak, and post-drain samples; retain UTC timestamps and aggregate metrics only:

- `up{job="orderflow"}`: exactly 11 values equal 1; scrape timestamps no older than 45 seconds; expected host/blackbox targets also UP.
- `process_start_time_seconds`, `process_resident_memory_bytes`, JVM heap/nonheap used/max, GC pause/rate; distinguish cgroup limit from JVM heap.
- `node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes`, CPU load, swap activity, EBS free bytes and I/O. Require no OOM/restarts, available RAM at least 15% after stabilization and data disk free at least 20%; investigate sustained CPU >80% or process usage approaching its cap before increasing load.
- Host `docker stats --no-stream --format '{{json .}}'` every 5 seconds during each bounded run; capture per-container memory/limit/CPU. Inspect each container's `RestartCount` and `State.OOMKilled`, plus kernel/systemd OOM events. Do not archive full `docker inspect` (contains secret env values).
- `orderflow_outbox_backlog`: all five publishers drain to 0 after actions, `orderflow_outbox_failed=0`. Compare `orderflow_kafka_dlt_publications_total` before/after and actual DLT beginning/end offsets with `/opt/kafka/bin/kafka-get-offsets.sh`; any retained DLT record requires investigation, not deletion/reset.
- Check actual Kafka consumer groups with `kafka-consumer-groups.sh --describe --all-groups`: stable committed offsets and lag returning to baseline/zero. Keep topics, group names, cluster ID and retention unchanged.
- Operations overview/jobs must reflect completed work, no scheduler failures. Preserve initial counter/totals baseline and UTC day; crossing midnight invalidates simple same-day comparisons.
- Existing `performance-smoke.sh` is capped at 30 requests/concurrency 2. Set `ORDERFLOW_PERFORMANCE_SMOKE=true` only after the journey passes; its timings measure availability, not production capacity.

CloudWatch host logs/metrics are disabled in the template. Local monitoring is configured; alerts have no external notification delivery without a separately approved receiver. Do not claim CloudWatch memory monitoring or alert delivery based on Terraform alone.

## Restart, controlled deployment and rollback

1. Establish verified release **A** first; there is no previous AWS release yet. Record every digest, schema/Flyway history, signing key fingerprints/kid, Redis AOF configuration, Kafka cluster ID/topics/groups/offsets, and selected synthetic record IDs/hashes. Keep a protected ledger of any pre-existing business records that must remain unchanged.
2. Pause new tester activity; wait for in-flight work, Kafka lag and outbox backlog to drain. For a cross-database consistent logical backup, stop the **application services only** while PostgreSQL/Redis/Kafka remain running. Do not use the all-container quiesce command for that step. Take/verify the encrypted eight-database backup; test restore to a uniquely named isolated target, never the live database. Persist Kafka/Redis data; do not treat DB dumps as a Kafka/Redis or full-EBS backup.
3. Recreate application containers through the normal manifest tooling and confirm existing customer login, persisted refresh state, pre-restart JWT (within its TTL), same JWKS/kid, cart, product, order/payment/refund and stock. Then test graceful stop/start of the whole Compose stack with the **same** bind data directories, Kafka cluster ID and key bytes.
4. Use the dedicated on-demand start/stop scripts with exact account/region/instance/tags and confirmations. Verify timer active, SSM ready, all services return healthy, volume UUID/mount unchanged and HTTPS still valid. Conduct one EC2 stop/start/reboot only after that operation receives approval. Neither path may format a known filesystem, generate new JWT keys or use `down -v`.
5. Prepare release **B** and its immutable manifest, source bundle, append-only migration hashes, explicit backward-compatibility decision and verified rollback target A. To exercise orchestration with no business-code changes, A/B may be two approved release manifests of the same tested code; label this honestly as an orchestration rehearsal rather than proof of a different application's compatibility.
6. Take the predeploy backup, deploy B using existing scripts, and require its real health + API verification hook. Keep test agents offline when idle. A separately approved stop of one B application container can simulate an incident without modifying business data; collect the alert/readiness evidence.
7. Invoke `scripts/deploy/rollback-release.sh` with A's exact manifest/release/account/region/domain/runtime/state and the real verification hook. Existing code refuses changed/removed historic migrations, target mismatch or unapproved schema compatibility. It switches images on the current data; **it does not restore an old database**. If compatibility is not proven, stop and repair forward; do not force rollback flags.
8. Recheck the ledger of preserved records and all infrastructure identities, execute a fresh complete journey and monitoring drain, then record actual rollback duration. New verification fixtures increase row counts; compare preserved IDs/business values, not equality of total counts. No SQL truncation, role deletion, Redis FLUSH, Kafka offset reset/topic deletion, volume deletion or old-snapshot overwrite is part of cleanup.
9. Stop the EC2 host at the end using the guarded tool and verify AWS reports `stopped`. Retained EBS, EIP, ECR and S3 charges continue. Removal/expiration of any retained resource needs its own approval.

Local preparation cannot establish an AWS RTO, RPO, real browser success, full-host memory peak, agent package version, external TLS/allowlist behavior or safe live-data rollback. Fill each NOT RUN only from the corresponding executed evidence.
