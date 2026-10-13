# Phase 10 deployment readiness

Assessment date: 2026-10-13. **NOT READY — CRITICAL BLOCKERS.** The AWS deployment components are implemented, but the enforced dependency gate fails on seven Spring findings. The owner chose to keep Spring Boot 3.5 and document these findings. No suppression, threshold bypass or migration to Boot 4 was added. Full local business verification also needs a stable runtime with sufficient memory. No live deployment or live-demo URL exists.

PASS below applies only to the stated executed check. Local Docker, mocked AWS plans and fixture browser tests do not establish deployed cloud acceptance. The [deployment guide](phase-10-cloud-deployment.md), [security review](cloud-security-review.md) and [release checklist](cloud-release-checklist.md) explain the release process and remaining gates.

## A. Deployment architecture

| Component | Intended configuration | Actual verification |
| --- | --- | --- |
| Region/account | Proposed `us-east-1`; explicit approved twelve-digit account required | No authenticated AWS account; local `123456789012` is synthetic |
| EC2 | Ubuntu 24.04, x86_64 `t3.xlarge`, 4 vCPU/16 GiB evaluation starting point | NOT RUN on EC2; local Mac has 8 GiB RAM and a 4 GiB Docker VM |
| Runtime | Docker Engine/Compose; eleven Java services and Next.js behind Caddy | Both application architectures built; isolated native ARM64 simulation exercised |
| ECR | Twelve private, encrypted, immutable repositories; version/full-SHA tags and digest deployment | Terraform policies and local images PASS; ECR publication NOT RUN |
| Network | VPC/public subnet/IGW; only inbound 80/443; no SSH; SSM administration | Mocked policies and merged Compose isolation PASS; actual AWS reachability NOT RUN |
| DNS/HTTPS | Owned domain; optional existing Route 53 zone/EIP; persistent Caddy ACME storage | Local CA routing/redirect PASS; domain/public ACME NOT RUN |
| Storage | 30 GiB encrypted root and 100 GiB retained encrypted gp3 data volume, `/opt/orderflow` | Defined and policy-checked; no EBS created or mounted |
| Data | Eight private PostgreSQL databases/owners; authenticated Redis AOF; private persistent Kafka KRaft | Real PostgreSQL bootstrap/isolated restore PASS; isolated stack exercised |
| Secrets | Five scoped SSM SecureStrings; protected runtime/accounts/backup key; stable PEM files | Retrieval/safety contracts and actual local mounted-key restart PASS; SSM NOT RUN |
| Monitoring | Private Prometheus/Grafana, host/availability probes, outbox/DLT/backup alerts | Configuration/rules validated; local services exercised; external alert delivery NOT RUN |

This is a single-server portfolio demo with planned release downtime and no HA. Payment, refund, email and SMS providers remain simulators; no real money is charged.

## B. Terraform

[Terraform files](../infrastructure/terraform/README.md) cover providers/versions, account validation, network/security, EC2, storage, IAM/SSM, ECR, secrets metadata, optional DNS/monitoring, outputs, examples and mocked policy tests. The generated provider lock includes signed/checksummed Linux AMD64 and macOS ARM64 packages.

| Command/check | Result |
| --- | --- |
| Terraform 1.16.5 `fmt -check -recursive` | PASS |
| `init -backend=false -lockfile=readonly` | PASS; AWS provider 6.68.x |
| `validate` | PASS |
| `terraform test` with mocked AWS | PASS; 10 policy tests |
| Real account `plan` | NOT RUN; AWS access absent |
| `apply` / `destroy` | NOT RUN; no billable approval or resources created |
| Actual Ubuntu/EBS bootstrap | NOT RUN; source/shell checks do not prove host installation |

Terraform ignores state/plans/populated tfvars, does not manage secret values, and rejects a mismatched authenticated account. Human approval is required before real provisioning. Remote state bootstrap is documented and remains unexecuted.

## C. CI/CD

Four workflows reside in the Git root `.github/workflows`: CI, immutable image build, release orchestration and manually approved deployment. Local actionlint/YAML/path checks PASS. The reusable CI retains backend, frontend, dependency, secret, SBOM, image, Terraform, Compose, automation, monitoring and browser gates.

Publication uses the separate protected `cloud-demo-publish` OIDC environment; deployment uses `cloud-demo`. Trust is scoped to the exact `Roshan17431/OrderFlow` environment subjects and STS audience. No static AWS access keys are introduced. Actual GitHub protection settings and AWS trust have not been inspected in an account.

The release manifest records all twelve digests, full source SHA, platform, account, region, environment and reviewed migration compatibility/hashes. Artifact validation rejects unsafe archives and manifest/SQL mismatches. Deployment takes an encrypted predeploy backup, performs bounded health/Eureka/HTTPS/business/metrics verification and updates healthy/previous metadata only after success. Rollback requires compatible schema and a recorded healthy artifact; it never restores old business data automatically. First-install demo seeding has a separate default-false protected workflow option and refuses conflicting existing fixtures.

**Remote workflows, ECR publication, S3 transfer, SSM execution, real release deployment and rollback: NOT RUN.** The current security gate blocks publication even after the missing AWS/GitHub access is provided. See [CI/CD](cloud-ci-cd.md).

## D. Application

All eleven Java images and the Next.js standalone image built and were inspected for both Linux AMD64 and ARM64: **24/24 PASS**, with non-root runtime users. [Image evidence](../deploy/local-image-evidence.json) records exact local image IDs; those are local builds, not ECR-published release digests.

The cloud override clears inherited local builds, ports and development credential defaults. The fixed Caddy/gateway edge addresses avoid startup address collisions; only the trusted physical Caddy peer may supply one literal client address for rate limiting. Payment-confirmation limiting matches the actual API route. Frontend public API configuration is empty for same-origin requests. Forty-three built static JavaScript files were inspected with zero matches for the development gateway URL or deployment-secret/key patterns.

Local native ARM64 startup reached healthy services and all ten Eureka clients registered. Subsequent workloads exposed OOM restarts in the 4 GiB Docker VM, so that startup snapshot is not a capacity or complete end-to-end PASS. Three bounded browser attempts failed, and two API-journey attempts stopped at the all-eleven-scrapes preflight before making API mutations. A bounded restart with experimental smaller native JVM limits exceeded its 240-second limit; those flags were not retained in the preparation helper. [Local runtime evidence](../deploy/local-runtime-evidence.json) records the actual checks and retained fixture scope.

Cloud Java heaps are capped at 50%, with 640 MiB containers for gateway/auth/product/cart/operations, 1 GiB for inventory/order/payment/delivery/notification, and 320 MiB for the registry. All container limits total 12.5 GiB, leaving 3.5 GiB on the proposed 16 GiB host. The new effective Compose policy proves the defined budgets leave native and host reserve; actual EC2 capacity remains NOT RUN.

Actual local HTTPS auth restart verification PASS: stable JWKS, pre-restart bearer accepted, pre-restart refresh cookie accepted and rotated, Secure/HttpOnly/SameSite=Strict cookie, Redis-backed logout revocation, mounted PEMs readable as UID/GID 10001 with mode 0400. CSRF, ownership and JWT/revocation tests remain enabled. True production profiles reject simulators; the cloud-demo worker is explicit, bounded and submits normal signed loopback callbacks. Unit/HTTP verifier tests PASS; an earlier real browser attempt reached a signed PAID order but failed before completing the whole test.

Real provider OAuth callbacks: NOT RUN. Public provider buttons stay blank until protected backend credentials and exact HTTPS callbacks are configured. Actual authenticated application SSE/business verification is distinguished from the passing isolated proxy SSE test.

## E. Domain and HTTPS

Actual domain: **not configured**. Live-demo URL: **none**. DNS lookup to an approved EC2 host and publicly trusted certificate validation: **NOT RUN**.

The actual isolated Caddy integration test PASS covers six routes, five blocked paths, spoofed forwarding headers, immediate SSE flushing, body-limit rejection and access-log redaction. Caddy configuration validation PASS. The local full-stack proxy also returned HTTPS homepage/catalog 200, blocked public Actuator/Eureka 404 and HTTP 308 to `https://localhost:8443/` while validating its explicit local CA. This does not prove public trust, DNS or AWS ingress. No DNS record was modified or domain purchased.

## F. Data safety

Persistent cloud paths include PostgreSQL, Redis, Kafka, Caddy certificates, monitoring, release metadata, configuration, JWT keys and encrypted backups on the retained EBS mount. Database bootstrap uses eight separate nonprivileged application owners and is idempotent. Existing Flyway SQL files were not rewritten; rollback preserves the newest schema record.

Real local backup/encryption/decryption/isolated restore: **PASS for eight databases**, including schema/data, owner password authentication/privileges, Flyway SQL checksum/history, table counts and representative record hashes. [Restore evidence](../deploy/local-restore-evidence.json) states the exact scope. Spring application startup against those restored databases, live database recovery, S3 upload/download and AWS snapshots: **NOT RUN**. The restore tool cannot target a live database and removes only its own generated test container.

No existing database, volume or user record was deleted. Full-stack fixtures use separate fresh data and fake accounts. One synthetic order left by a failed browser run was recovered through normal owned delivery APIs after proving the exact customer/order/agent scope; no stock reset or data deletion occurred. Signing/backup key replacement during ordinary release fails closed.

## G. Monitoring

Prometheus base/cloud configuration and all 15 cloud alert rules PASS validation. Existing dashboards are reused. Local Prometheus and Grafana started successfully; real application targets are private. Host/blackbox/Grafana services were temporarily paused only in the owned local fixture to investigate memory pressure. After the bounded checks, the owned fixture was stopped with containers and data retained to release host resources. Test-phase availability is reported separately from configuration validation; no complete stable monitoring/business PASS is claimed.

Docker/Caddy logs have bounded rotation and redacted sensitive request fields. Source secret scanning and application log-redaction regression checks PASS. A failed Playwright transport error exposed a temporary local fixture bearer in ignored local diagnostics; it was redacted and the fixture account's sessions revoked. API test transport now sanitizes errors, disables credential-bearing redirects and has dedicated passing regression tests. No real AWS credential was used or published.

AWS CloudWatch alarms/log ingestion, budget alerts and external alert receivers: **NOT RUN/not configured in an account**. Same-host Prometheus cannot establish external EC2 availability or an on-call notification service. See [monitoring](cloud-monitoring.md).

## H. Executed tests

| Check | Result and scope |
| --- | --- |
| Java 21 `./mvnw -B -ntp clean verify` after dependency fixes | PASS; 264 tests, 0 failures/errors/skips, 88 XML reports; 3:41 |
| Tested source identity | PASS; 757 source/POM/main/test files match repository and isolated Maven verification snapshot |
| Frontend clean install, lint, typecheck, production build | PASS; both architecture images also execute clean production builds |
| Frontend Vitest | PASS; 138 tests in 15 files |
| Playwright fixture browsers | PASS; 10 tests, mocked API responses |
| Real local HTTPS browser journey | FAIL; three bounded attempts, resource-related service unavailability, inventory OOM confirmed |
| Actual local API verification hook | FAIL; two attempts refused before API mutation because all eleven scrapes were not healthy |
| Deployed AWS browser/API/negative/performance journeys | NOT RUN |
| Terraform fmt/init/validate/mocked tests | PASS; 10 mocked tests; real plan/apply NOT RUN |
| Effective cloud Compose runtime policies | PASS; 12 tests, including JVM native/host memory budgets |
| Existing production-like Compose guard | PASS; private internal services, secure cookies, CSRF/JWT configuration and disabled production simulators |
| Deployment/backup/initial-seed safety contracts | PASS; 35 tests |
| Cloud target, tool, monitoring and HTTPS transport safety | PASS; 41 tests, including actual isolated local TLS transport |
| Caddy validation and actual proxy integration | PASS; routing, blocking, trusted headers, SSE, body bound and redaction |
| Database bootstrap integration | PASS; eight owners, password quoting, safe idempotent rerun |
| Local Docker builds/architecture/non-root inspection | PASS; 12 services × 2 platforms |
| Auth key persistence/refresh/logout restart exercise | PASS; actual local HTTPS and private Redis |
| Encrypted backup/isolated PostgreSQL restore | PASS; eight databases; application startup/live recovery NOT RUN |
| Monitoring configuration/rules | PASS; 15 cloud rules and base configuration |
| Workflow actionlint / shell syntax | PASS; four workflows and 32 shell scripts |
| Source secret scan / structured log redaction | PASS; high-confidence source patterns and dedicated regression checks |
| CycloneDX SBOM | PASS; aggregate JSON, format 1.6, 242 components |
| OWASP Dependency-Check 12.2.2 | **FAIL**; official NVD bulk feed completed; 20 threshold findings reduced to 7 remaining Spring findings |
| Frontend production dependency audit | PASS; zero production findings |
| Full frontend dependency audit | FAIL; four high findings in the development lint dependency chain; no forced incompatible lint downgrade |
| Bounded local performance | Initial 12-request sample PASS; concurrency 2; p50 39.621 ms, p95/p99 8236.885 ms. Cold, constrained local sample; no EC2 capacity claim |
| Remote GitHub Actions / OIDC / ECR / SSM | NOT RUN |

Maven ran in `/tmp/orderflow-phase10-final` with Java 21 and source identity checked afterward, avoiding collisions with Docker packaging outputs. Machine-readable results are in `deploy/local-*-evidence.json`; detailed temporary logs are retained under `/tmp/orderflow-phase10-*`. Their persistence is limited to this local environment. No mocked test is credited as deployed verification. See [security review](cloud-security-review.md) for exact dependency pins, advisory applicability review and the retained release block.

## I. Costs

Resources actually created: **0**. Proposed resources and dated official references are in [cost management](cloud-cost-management.md). The illustrative 730-hour subtotal is **$137.02/month**: reference EC2 $121.47, 130 GiB gp3 $10.40, one public IPv4 $3.65 and assumed 15 GB ECR $1.50. The EC2 reference is not an independently verified Linux SKU quote, and ECR storage is unmeasured.

S3/state/backup versions and requests, snapshots, transfer, DNS/domain, optional KMS/monitoring/SNS and taxes are additional and unverified. No spending budget was approved. Terraform's optional budget threshold is an alert, not a hard cap; no alert exists in an account. Recheck exact approved-region prices and expected usage before a real plan. The 16 GiB host is a capacity-evaluation proposal, not a measured promise.

## J. Live deployment and blockers

**NOT READY — CRITICAL BLOCKERS.** Keep Spring Boot 3.5 as requested. Compatible dependency updates and functional checks are complete, but seven Spring findings still fail the unchanged release security threshold. Vendor feature preconditions were reviewed; that does not clear the raw gate. A supported patched dependency source or separately authorized policy/stack decision is needed before image publication. No such exception is implied by documenting the findings.

The complete business journey must also pass on a stable, sufficiently provisioned authorized target. Local startup and partial PAID/DELIVERED fixture recovery do not establish all workflow, SSE, operations, outbox/DLT or negative-test acceptance.

Before any real provisioning, separately supply:

1. Intended AWS account and region; scoped authorized role/SSO access; inventory of existing resources to reuse.
2. Owned domain and authorized DNS method/zone; approved contact for certificate issuance.
3. Explicit approval of the concrete Terraform resource plan and spending budget, including retained storage/address/backup charges.
4. Protected GitHub publication/deployment environments and approved role/instance/bucket variables.
5. Five privately provisioned SSM SecureStrings with stable matching JWT PEMs, backup key and dedicated fake operator accounts; first-install seed approval if required.
6. A reviewed release with a passing dependency gate, matching images/migration compatibility and an actual tested public HTTPS journey.

No approval for billable infrastructure, DNS, public deployment or Git writes has been inferred. Cloud acceptance criteria 73–92 are all **NOT RUN**, regardless of any corresponding local test result.

## K. Git status and publication authorization

The implementation assessment was recorded on `master` at baseline `b8a0dcf78270e934b9e985b42c66a409f7a49ad3`, before any Phase 10 commits or pushes. At that point there were 37 modified tracked files and 119 untracked files, including the unrelated pre-existing `.vscode/` file. The [pre-publication inventory](phase-10-git-status.txt) preserves that historical snapshot.

On 2026-10-13 the owner explicitly requested a GitHub push with **at most 13 commits**, superseding the original preparation-only Git restriction. The Phase 10 change set is grouped into 13 ordered commits on the existing `master` branch; the final pushed SHA and remote verification are reported in the chat. The unrelated `.vscode/` file is excluded. No new branch or pull request is required, and no history rewrite is authorized.

This Git publication authorization does not authorize AWS resources, DNS changes or live deployment. The security/runtime blockers and local/cloud test distinctions above remain unchanged. Remote workflow execution results must be checked separately; local verification is not proof of a passing GitHub Actions run.
