# OrderFlow on-demand operation

Prepared2026-10-15. Local preparation only. No AWS deployment, stop/start persistence test, live certificate or remote GitHub Actions run has occurred. Security findings and incomplete real-backend E2E still block public deployment. See [verification](aws-on-demand-verification.md), [security assessment](phase-10-remediation.md) and [costs](aws-on-demand-costs.md).

## Proposed host and approval boundary

Reuse `infrastructure/terraform/`, existing release workflows and Compose. Proposed evaluation target: Ubuntu24.04, t3.xlarge,4vCPU/16GiB,x86_64 matching linux/amd64 images,30GiB encrypted root and100GiB encrypted retained EBS. All11 Spring services, Next.js, PostgreSQL, Redis, Kafka, Caddy and monitoring remain present. Container caps total12.5GiB;16GiB is a starting evaluation size, not demonstrated capacity. Build images on CI, run browser automation from a separate workstation/runner and measure actual host headroom.

Before any billable action, approve account, region, exact resource plan, owned domain, address strategy, budget and real alert email. The intended public configuration admits80 solely for Caddy redirect/ACME and443 for HTTPS; databases, Kafka, Eureka, services, Prometheus and Grafana remain private. No SSH ingress. No automatic resource creation, daily startup schedule or DNS updater was added. An unresolved security gate prohibits public deployment; any private staging exception needs separate explicit risk/access approval.

Terraform explicitly sets `instance_initiated_shutdown_behavior="stop"`, Standard CPU credits and `OrderFlowOnDemand=true`. `enable_on_demand=true` is allowed only with `environment="cloud-demo"`; runtime is bounded0.25–24hours. Optional EIP, DNS, budget and AWS monitoring remain disabled by default. Existing hosts do not rerun cloud-init when user data changes: install reviewed assets through a separately approved SSM maintenance step and verify them before the first on-demand session.

## Start a session after authorized deployment

Use your approved AWS CLI v2/SSO session. No access keys are stored in scripts. Set explicit targets from reviewed Terraform outputs; the placeholders below deliberately fail validation:

```bash
export AWS_ACCOUNT_ID=REPLACE_WITH_APPROVED_ACCOUNT
export AWS_REGION=REPLACE_WITH_APPROVED_REGION
export ORDERFLOW_INSTANCE_ID=REPLACE_WITH_APPROVED_INSTANCE
export ORDERFLOW_ENVIRONMENT=cloud-demo
export ORDERFLOW_PROJECT=orderflow
./scripts/cloud/start-demo.sh
```

The helper displays account/region/instance/state, verifies STS account, one EBS-backed instance owned by that account, exact Project/Environment/ManagedBy/Purpose/on-demand tags, and OS shutdown behavior STOP. Type the displayed `start i-...` phrase. It starts only that existing instance, polls EC2 system/instance status and SSM online status, then checks container health and HTTPS frontend/product API through Caddy. Each polling phase has a bounded timeout (default600seconds; configurable30–1800), each AWS subprocess has a30second cap, and remote commands have explicit execution timeouts. Total duration can span multiple phases; timeouts do not imply that AWS stopped the instance.

An already running/pending instance is not restarted and its deadline is not reset. Readiness failure returns nonzero; inspect privately while the independent countdown continues. First provisioning has no healthy release pointer and therefore no application to resume. Perform the first immutable, approved release through the existing deployment workflow after its gates pass; startup does not seed a database or promote an unverified release.

Alternatively, select only the approved instance in EC2 Console→Instance state→Start. The boot timer also covers this path. Wait for SSM and run the helper/status check before opening the configured HTTPS hostname. Static IP/DNS and a valid certificate must already be authorized and configured.

## Boot implementation

`infrastructure/systemd/orderflow-demo.service` waits for network, Docker and `/opt/orderflow`. A dedicated Docker drop-in also requires the EBS mount, runs `mountpoint`, and invokes `boot-guard` before Docker starts. This prevents both an absent EBS mount exposing empty root-disk paths and Docker replaying unverified containers through `unless-stopped`. Bootstrap enables this gate only after installing the controller and AWS CLI. Before any application Compose replacement, deployment removes `boot-ready.json`; successful verification recreates it matching the immutable resume pointer. Missing/stale markers after a failed deployment block Docker startup and require explicit maintenance recovery with ingress blocked; they never silently resume the failed candidate. No formatting/reinitialization happens during a normal boot.

The root-owned `/usr/local/sbin/orderflow-host` calls the Terraform-installed `on_demand_host.py`. It checks IMDSv2 identity and AWS tags/STOP behavior, then uses `/opt/orderflow/releases/state/runtime-release.json`, written only after existing `verify()` succeeds. Its immutable release directory, healthy manifest and schema rollback compatibility must agree. Release selection, startup and readiness checks hold the same nonblocking deployment lock as deploy/rollback; a busy release is reported unready rather than applying or reporting stale metadata. It retrieves the existing approved SSM secrets, refuses changed signing/backup keys, authenticates to ECR, pulls digest-pinned prebuilt images as needed, and starts the existing Compose project with `--no-build`. Existing container restart policies remain in effect. It records/resumes the verified release; it does not silently adopt a failed release or rebuild Java/Next.js.

The host role adds only EC2 DescribeInstances/DescribeInstanceAttribute for the selected region. It gains no EC2 start, stop, terminate or DNS permissions. Normal IAM and SSM policies remain scoped to the existing deployment. The local operator needs StartInstances/StopInstances scoped to the exact approved instance ARN plus matching resource tags, the two EC2 Describe APIs, STS identity, SSM instance status and command-result reads, and SendCommand limited to AWS-RunShellScript and that exact tagged instance. Do not grant an account-wide administrative policy just to operate these helpers. No new GitHub start/stop role or pipeline was introduced.

## Stop a session

```bash
./scripts/cloud/stop-demo.sh
```

Review the displayed instance/application status and type `stop i-...`. The helper tries to quiesce the dedicated Compose application via SSM, then requests normal EC2 StopInstances, without force/hibernate/termination. If SSM is unavailable it reports that and still requests a normal confirmed EC2 stop. Polling must show `stopped`; EBS volumes remain attached. Console→Stop is also supported: systemd/container shutdown policies provide a bounded opportunity to stop PostgreSQL, Redis and Kafka cleanly.

Before ending an important session, take and verify a backup using [the existing protected backup procedure](cloud-backup-restore.md). Routine stopping preserves attached EBS, Docker bind-backed volumes, PostgreSQL, Kafka KRaft, Redis AOF, certificate storage, configuration, signing keys, release metadata and backups. It does not protect against corruption, operator error, disk loss or accidental future deletion. Never use termination, Terraform destroy or volume pruning for routine demo shutdown.

Confirm the EBS attachment still exists and review billing. A stopped instance continues to incur storage, EIP, registry and backup charges. No stop/start persistence claim is made until the authorized test below passes.

## Automatic shutdown and intentional extension

`orderflow-demo-stop.timer` starts30seconds after boot and checks every30seconds, independent of application startup/failure. `/run/orderflow-demo/session.json` stores the boot ID and a deadline measured against Linux boot uptime. Default `ORDERFLOW_DEMO_MAX_RUNTIME_HOURS=8` means eight hours from boot. Repeated checks, service restarts, helper starts and policy-file edits do not silently reset that boot's deadline. A new boot establishes a new session. There is no timer that starts EC2 every morning.

At expiry the controller rechecks exact host identity/tags and shutdown behavior STOP, records the timestamp/reason, attempts graceful Compose stop and requests normal OS poweroff. The30second check interval, AWS validation and bounded graceful shutdown add some overhead. The timer refuses unsafe/unknown host identity or TERMINATE behavior. IAM/network failure can therefore prevent shutdown; this is not a hard cost cap. Inspect failed units and independently verify the AWS state. Reasons also persist, when EBS is mounted, in `/opt/orderflow/releases/state/on-demand-events.jsonl`; only timestamps and fixed reasons are written.

Inspect:

```bash
python3 scripts/cloud/on_demand.py status
# Approved SSM session on the exact dedicated host:
sudo systemctl status orderflow-demo.service orderflow-demo-stop.timer
sudo journalctl -u orderflow-demo-stop.service --since today
```

To intentionally extend a running session, for example until eight hours from now:

```bash
python3 scripts/cloud/on_demand.py extend --hours 8
```

Type `extend i-...`. The new deadline must be later than the existing deadline, is capped at24hours from now, and is recorded. Deadline extension and the final shutdown claim share a lock: an extension accepted during host verification cancels a stale expiry; extension is explicitly refused once shutdown has been committed. It is an explicit one-session extension. To change future boots, set `demo_max_runtime_hours` in reviewed Terraform configuration and the corresponding root-only `/etc/default/orderflow-demo` value `ORDERFLOW_DEMO_MAX_RUNTIME_HOURS=8` on an existing approved host. Bootstrap preserves existing local policy files; existing-host changes need a reviewed maintenance step. Do not disable the timer to extend a demo.

Local safety tests exercise real short elapsed time against a harmless recorder. Ubuntu systemd unit syntax is validated in a disposable container; an actual Linux boot timer/EC2 shutdown has **NOT RUN**. On a dedicated authorized staging host, shorten the configured policy to0.25hours (minimum), start once through Console, confirm the deadline, let it expire, then verify via external CLI that the same instance is stopped and EBS retained. Restore eight hours before the next session. Never point this test at unrelated infrastructure.

## Persistence acceptance test — prepared, NOT RUN

Use only the dedicated approved staging/demo host and a quiet synthetic queue. Maximum target run30minutes plus at most15minutes recovery; stop on any mismatched identity or unexpected ownership. No volume/database deletion or reset is part of this test.

1. Run the real browser journey from a separate runner and the host-side API journey described below. Require PASS and save its root-only JSON report. It identifies synthetic customer, product, order, payment and delivery IDs. It now explicitly checks registered-customer login and reservation RESERVED→COMMITTED→FULFILLED plus exact stock5/2/3 before payment and3/0/3 after fulfillment.
2. Capture a protected baseline: each of the eight databases' Flyway version/checksum history; selected synthetic `users`/product/order/payment/delivery/notification/inventory rows tied to those exact IDs; Kafka `cluster.id`/`node.id` from its existing `meta.properties`; root-only SHA256 fingerprints of both JWT files, backup.key and healthy/runtime/schema release metadata. Discover actual schema/table names from the checked-in Flyway migrations rather than guessing SQL. Use read-only SQL with ON_ERROR_STOP and a10second statement timeout; never print credentials/PII or entire database dumps into CI logs.
3. Create one uniquely named synthetic Redis key with24hour TTL through a protected local redis-cli session (`REDISCLI_AUTH` in process environment, never password argv), and record only the test ID/value hash. Also keep a newly logged-in synthetic customer's refresh cookie and bearer in memory in the test runner. Record Caddy's current certificate SHA256 and expiry using strict TLS; an authorized renewal near expiry is expected, not data loss. Do not copy private keys to the runner.
4. Take an encrypted backup. Run the confirmed stop helper with `--timeout 900`; require `stopped`. Externally verify the same encrypted data EBS volume ID/attachment and stable EIP. Run the confirmed start helper with `--timeout 900`; require EC2, SSM, all containers and HTTPS ready. Do not change image manifests between capture and comparison.
5. Compare the synthetic rows' IDs, values, financial PAID/fulfillment DELIVERED and inventory3/0/3; verify notification rows, eight Flyway histories, Kafka identity, Redis marker, key/config/release fingerprints and persistent certificate storage. Verify the pre-stop unexpired bearer and refresh rotation still work; normal TTL expiry is not a signing-key persistence failure. Check no DB initialization, Kafka identity reset or app-image rebuild occurred. Remove only the uniquely owned Redis test key after comparison; preserve business fixtures as evidence.
6. Recheck all services, outbox drainage, DLT counters and host OOM/restart counters. Re-run the complete real journey after restart. Mark PASS only if every comparison and both journeys pass. On failure, preserve data/logs and investigate; never reset storage to manufacture success.

Normal stop/start persistence, refresh-cookie continuity and actual timer-triggered EC2 STOP are mandatory staging acceptance items and remain NOT RUN.

## Real staging verification and measurements

Reuse the existing guarded `scripts/cloud/verify-journey.sh` and frontend `npm run test:e2e:cloud`. They use real APIs/browser/Redis/Kafka/PostgreSQL/Caddy; fixture Playwright is a separate local contract check. Host release verification already passes the protected fake role accounts, private Prometheus address and target configuration to the API hook. Keep the report private and associate it with exact Git SHA/digest manifest and AWS instance identity.

For browser verification on a separate runner, use the explicit variables defined in `frontend/e2e/support/cloud-target.ts` and `cloud-fixtures.ts`: `ORDERFLOW_CLOUD_E2E=true`, authorized target environment/account, real HTTPS origin and mode0600 fake role-account file. Never expose simulator webhook secrets in the browser. Public TLS remains strict. For an explicitly approved private staging exception, review CIDR/SSM routing and a loopback-only test CA arrangement first; the current Terraform public ingress defaults must not be applied as a private exception without that review.

Run browser then API sequentially to retain exclusive fixture-agent assignment. Verify HTTPS→registration/login→catalog/cart→checkout/order→inventory reservation→simulator PROCESSING→signed backend webhook→PAID/SUCCEEDED→inventory commitment→delivery assignment/pickup/finalization→out-for-delivery→DELIVERED→persisted notifications/authenticated SSE→operations projections/outbox drain/no new DLT. The prepared API hook additionally checks invalid JWT, role/ownership denial, CSRF, secure cookies, refresh rotation and logout revocation. Add a final separately bounded login-rate-limit probe using a new synthetic identifier (maximum10attempts and expected429), after the business journey, so the shared IP limiter cannot disrupt it; document its cooldown. Do not claim that probe or the restart checks executed simply because unit tests pass.

Before, during and after each journey collect private `docker stats --no-stream`, inspect `OOMKilled`/RestartCount, `/proc/meminfo` available memory, JVM heap/nonheap/process metrics, container cgroup `memory.events`/`cpu.stat`, PostgreSQL/Redis/Kafka RSS and `journalctl -k` OOM evidence. Capture EC2 CPU utilization/credit balance/status via CloudWatch when authorized. Pause builds on this host. Record p50/p95 latency only from actual bounded measurements, including sample count; startup success alone is not a capacity benchmark. Diagnose sustained pressure/throttling before tuning; request approval before increasing size.

## Troubleshooting

| Symptom | Bounded investigation / action |
|---|---|
| Instance will not start | Confirm exact account/region/ID, quota/capacity and current state. Do not create a replacement automatically. |
| SSM unavailable | Inspect status checks, agent, scoped instance role, VPC DNS/outbound443 and clock. No public SSH workaround. Timer safety/readiness remains unverified until checked. |
| Docker or services unhealthy | Check EBS `findmnt`, Docker mount drop-in, boot service and last healthy pointer/manifest. Inspect private logs without tokens/keys. No volume removal. |
| PostgreSQL/Kafka unavailable | Inspect mount, permissions, disk space, configured owners and unchanged KRaft identity. Use existing database/Kafka recovery runbooks; never reformat or reseed. |
| Frontend unavailable | Verify digest/platform, container health, gateway readiness and private Compose networking. No on-host build. |
| HTTPS unavailable | Inspect authorized hostname/EIP/DNS, inbound80/443, persisted Caddy storage, clock and ACME logs. Never disable public TLS verification. |
| Outbox backlog/DLT growth | Check Kafka availability, consumer errors and exact event/ownership; retain evidence. Do not clear DLTs just to pass. |
| OOM/restarts | Record host available memory, cgroup/JVM/native usage and restart timing. Do not repeat full stack on the4GiB Docker VM. |
| Timer failure | Verify `orderflow-demo-stop.timer`, root-only policy/session, read-only EC2 permissions and STOP behavior; manually stop the confirmed instance when appropriate. |
| Unexpected billing | Confirm EC2 stopped; inspect EBS/EIP/ECR/S3 versions, snapshots, CPU-credit mode, optional monitoring and usage types in Bills/Cost Explorer. Alerts do not impose a hard cap. |

No commands here authorize billable execution before approval. Stop/start, public DNS/HTTPS, AWS staging and remote workflows remain NOT RUN until explicitly authorized and actually executed.
