# OrderFlow — AWS Mumbai staging readiness

> Historical preparation-task snapshot. Its uncommitted status, container-scan NOT RUN and preparation conclusion describe that earlier task. The subsequent [release freeze and approval worksheet](release-freeze-2026-10-27.md) records local commits, fresh scans and the current AWS approval decision: **BLOCKED**. No full-stack acceptance has run.

Assessment: **2026-10-27**. Baseline HEAD: `4197fbe1095663f0dbc32d2705bf95b202fb2702`, branch `master`. The migrated working tree remains uncommitted. **READY FOR AWS STAGING APPROVAL** means preparation is ready for the approval checklist below; it does not mean AWS acceptance or production readiness has passed.

No commits, pushes, branch creation, image publication, AWS account operations, Terraform apply, paid resource creation, EC2 launch or DNS modification occurred. Public AWS price-list downloads and offline mocked Terraform plans do not access an AWS account.

## Verification results

| Scope | Status | Executed evidence / limit |
|---|---|---|
| Uncommitted migration audit | PASS | Starting 106 modified tracked +54 individual untracked files; hashes of all 160 preserved during this task. No probable production secrets, accidental build artifacts or unrelated work identified. See audit below. |
| Boot/Cloud migration baseline | PASS — recorded | Existing migration evidence: Boot 4.1.2 / Cloud 2025.1.4, Java 21; 226 unit +60 integration tests, zero failures/errors/skips. Full Maven suite was not repeated for infrastructure/test preparation. |
| Dependency security baseline | PASS — recorded | Existing unchanged OWASP 12.2.2 gate, threshold 9, NVD checked `2026-10-27T01:24:13Z`; no suppressions/analysis exceptions. No dependency/application changes in this task. Not a new OWASP execution. |
| Frontend audit | PASS | Fresh `npm audit` and `npm audit --omit=dev`: 0 findings. |
| Frontend verification | PASS | Fresh lint, typecheck, 138 Vitest tests, production build, 10 mocked fixture Playwright tests. Lint/typecheck/cloud test discovery rerun after acceptance-test edits. |
| Real browser test preparation | PASS | Cloud test discovered successfully; now explicitly checks RESERVED, COMMITTED and FULFILLED/stock changes at pickup. Actual execution against all services: NOT RUN. |
| Application image compatibility | PASS | Recorded 24 successful builds, 12 apps ×arm64/amd64; retained image IDs/platforms re-inspected. Migrated auth image restart probes executed on both architectures. ECR publication/pull: NOT RUN. |
| Compose/security policies | PASS | Base and production-like validation; effective cloud topology and all 12 runtime policy tests. Only Caddy public, monitoring loopback, persistent read-only keys, simulators confined to cloud-demo. |
| Terraform | PASS | fmt/validate and 17 offline mocked plan tests, including Mumbai 32-GiB host, restricted HTTPS, invalid/empty CIDRs, EIP and architecture/account guards. Actual account/state plan: NOT RUN. |
| Deploy/start-stop automation | PASS | 36 deploy/backup safety tests +65 cloud/on-demand/transport tests. Real AWS IAM permissions, boot/stop timers and EC2 operations: NOT RUN. |
| systemd | PASS | Units validated by actual Ubuntu `systemd-analyze verify`; no systemd daemon or host shutdown exercised. |
| Caddy | PASS | Real isolated proxy test: routing, blocked internal paths, spoofed forwarding headers, immediate SSE, 413 and secret redaction. Public ACME/TLS and external SG enforcement: NOT RUN. |
| PostgreSQL bootstrap | PASS | All eight actual database owners; idempotency and quoted-password handling; app roles lack superuser/createDB/createRole/bypassRLS. |
| Isolated restart persistence | PASS | Actual Boot 4 auth, PostgreSQL, Redis and Kafka container removal/recreation on retained temporary volumes: user, Flyway history, AOF record, Kafka message, key bytes/JWKS and pre-restart JWT preserved. Both arm64 and amd64 auth images passed. |
| Backup/isolated restore | PASS | Fresh encrypted eight-database backup and isolated restore: owner-role login, real SQL schema/checksums, table counts and synthetic record hashes. Live recovery/app startup from that restore: NOT RUN. |
| Monitoring | PASS | Default and cloud Prometheus configs, eight application rules +seven cloud rules; Grafana asset validation. Live full-service targets, memory peaks and alert delivery: NOT RUN. |
| Source secrets/log redaction | PASS | Fresh repository scanners and diff whitespace check. This is a high-confidence pattern scan and file/scope review, not proof against every possible secret format. |
| Full real commerce, negative scenarios and operational rollback | NOT RUN | Prepared runbook; no sufficiently provisioned local full-stack environment or AWS deployment. No end-to-end success claimed. |
| Container OS/host package vulnerability gate | NOT RUN | No Trivy/Grype installed, no ECR scan results or running EC2 host. Required before promoting an approved deployment; Maven/npm scanning does not cover OS images. |

Verification logs and structured commands: `/Users/roshan/.cache/orderflow-staging/2026-10-27/`. Portable result summary: `deploy/aws-staging-readiness-2026-10-27.json`. Existing migration evidence: `deploy/boot4-migration-verification-2026-10-27.json` and `docs/boot4-migration-2026-10-27.md`.

An initial cloud-promtool command failed before starting because it tried to create a nested mount under a read-only directory mount. Mounting the three configuration paths separately passed with the unchanged cloud configuration. Terraform ingress regression tests first failed against the old unrestricted implementation, then passed after the change. These initial failures are retained in local logs; no outstanding failing verification is hidden.

## Uncommitted changes and secret/generated-file review

The 160 pre-existing migration files comprise Maven/starter alignment, Boot/Jackson/security API adaptations, compatibility/integration tests, synthetic SQL/JSON/JWT/session fixtures and migration reports. Their exact starting paths/hashes are retained in `migration-baseline.json` in the evidence directory; the existing migration report inventories them. No bytes in these 160 files were changed by this staging task.

- Synthetic `.jwt` files contain test-only signed tokens/public verification keys; `.base64` files are synthetic serialized OAuth session fixtures. No signing private key is included. Fixture provenance and SHA-256 manifest are documented in `docs/boot4-compatibility-fixtures.md`.
- Migration/staging verification JSON and Markdown are intentionally generated **evidence artifacts**, suitable for review. Some migration evidence contains local toolchain/log paths; these are nonsecret and not runtime configuration.
- No untracked `target/`, `.next/`, `node_modules/`, `.terraform/`, logs, JAR/class files, binary plans, state files, populated runtime env, real account files or private-key files are proposed for Git. A Next.js fixture run regenerated declaration paths in `next-env.d.ts`; only that generated drift was returned to its exact starting bytes.
- This task adds deployment preparation and tests only: Terraform CIDR support/tests, Mumbai Terraform/runtime templates, isolated persistence probe, browser acceptance assertions, runbook, readiness evidence and plan ledger. No business application source, POM, dependency version or historical Flyway migration was changed.
- Review the 110 modified tracked and 61 untracked individual files as **two logical scopes** if Git publication is later requested: migration/compatibility, then staging access/tests/documentation. Do not include cache/build directories or populated configuration. No commit has been created, staged or pushed.

## Changes prepared for staging

`infrastructure/terraform/variables.tf` adds `https_ingress_cidrs`; `security.tf` creates one HTTPS rule per approved IPv4 network. An empty list creates no HTTPS ingress. Invalid IPv4 CIDRs fail validation. Existing default public behavior is preserved for existing consumers; **the restricted Mumbai template must be selected explicitly**. Do not use general defaults for staging.

`infrastructure/terraform/staging-mumbai.tfvars.example` selects Mumbai, `m7i.2xlarge`/amd64, 30+100 GiB gp3, stable EIP, no Route 53, dedicated on-demand safety and documentation-only tester `/32`. The invalid account placeholder deliberately fails validation. `infrastructure/cloud/staging-mumbai.env.example` selects Mumbai and leaves all real identities, image digests and secrets blank. The cloud Compose required substitutions fail closed until populated privately.

Port 80 remains public **only for Caddy's automatic HTTPS redirect and HTTP-01 ACME validation**; application HTTPS is restricted. Caddy does not reverse-proxy business requests on port 80. TLS-ALPN on restricted 443 cannot be relied on; HTTP-01 must remain reachable with correct public DNS. See [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https).

Existing release/on-demand automation requires environment `cloud-demo`. This dedicated host is staging by purpose, while retaining that safe label and simulator profile. Do not weaken the guard to make `environment=staging` work. Do not use these simulator settings for production.

`infrastructure/cloud/tests/persistence_integration.py` creates uniquely named temporary containers/network/volumes, uses actual database bootstrap and migrated auth Flyway startup, recreates all four services, asserts persistence, and removes only resources it created. It uses direct loopback HTTP with a test-only insecure refresh-cookie override; **cloud secure-cookie configuration is unchanged** and not credited by this probe. It proves graceful container recreation, not crash durability, full-service recovery or EBS reboot behavior.

## Proposed Mumbai resources

For the supplied **one tester `/32`** template, with a new GitHub OIDC provider and all optional integrations disabled except EIP/on-demand:

| Resource | Exact proposed quantity/configuration |
|---|---|
| EC2 | 1 Ubuntu 24.04 `m7i.2xlarge`, 8 vCPU/32 GiB, x86_64, IMDSv2 required, OS shutdown behavior STOP |
| Storage | 1 encrypted 30-GiB gp3 root disk (nested EC2 block); 1 encrypted retained 100-GiB gp3 data volume +attachment, `/opt/orderflow` |
| Public IPv4 | 1 EIP +association, retained/billable while stopped |
| Networking | 1 VPC, 1 public subnet in one approved AZ, 1 IGW, 1 route table +association; no NAT, load balancer or VPC endpoint |
| Security group | 1 group; 1 HTTP ingress +1 tester HTTPS ingress; 4 egress rules (80/443 plus VPC-only TCP/UDP DNS); no IPv6/SSH/internal ingress |
| ECR | 12 immutable repositories +12 lifecycle policies; scan-on-push, release-tagged images retained |
| Backup/artifact S3 | 1 private encrypted versioned bucket +6 policy/ownership/encryption/versioning/public-block/lifecycle resources; backup retention 30 days, noncurrent versions 90 additional days; release artifacts not expired |
| IAM | 3 roles (EC2, GitHub publish, GitHub deploy), 1 EC2 instance profile, 7 scoped inline policies, 1 GitHub OIDC provider **unless reusing the account's existing provider** |
| Terraform guard | 1 local `terraform_data` guard; not an AWS billed service |
| Out-of-state prerequisites | 5 Standard SSM SecureStrings, approved remote-state backend, existing DNS ownership/hostname, approved GitHub environments and operator SSO permissions; none created by this work |
| Explicitly absent | RDS, MSK, ElastiCache, ECS/EKS, NAT, ALB, Route 53 records/zones, CloudWatch log group/alarms/SNS, budget objects, new CMKs and scheduled EBS snapshots |

This is **61 Terraform-managed objects /60 AWS objects**, excluding the nested root disk, for one `/32` and a new OIDC provider. Each additional tester CIDR adds one ingress object; reusing OIDC removes one. The executed mocked Mumbai test uses **two** documentation `/32`s and therefore records 62 planned creations /61 AWS objects, zero changes/deletes. These are offline proposals, not an authenticated plan of the user's account. Account discovery/import decisions, capacity, quotas, selected AMI/AZ and real resource IDs remain NOT RUN.

This is a single-AZ/single-broker staging design with replication factor 1. Retained EBS and backups preserve data across normal restarts; they do not provide high availability or a proven disaster-recovery RTO/RPO.

## Current regional cost estimate

USD public On-Demand Linux/shared pricing retrieved **2026-10-27**, EC2/S3 price-list effective date **2026-10-01**. No Savings Plan, reservation, Spot, free-tier credit, tax or FX assumption. Sources: [official Mumbai EC2/EBS offer CSV](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonEC2/current/ap-south-1/index.csv), [official Mumbai S3 offer CSV](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonS3/current/ap-south-1/index.csv), [AWS IPv4 pricing](https://aws.amazon.com/vpc/pricing/), [AWS ECR pricing](https://aws.amazon.com/ecr/pricing/).

| Item | Rate / assumption | Estimate |
|---|---|---|
| Recommended `m7i.2xlarge` | $0.4242/hour, 8 vCPU /32 GiB | $3.3936 for 8 running hours; $309.666 for 730 hours |
| Smaller `m7i.xlarge` | $0.2121/hour, 4 vCPU /16 GiB | Not preferred for first complete acceptance |
| Existing default `t3.xlarge` | $0.1792/hour, 4 vCPU /16 GiB, Standard credits | Burstable CPU may throttle; not the recommended acceptance host |
| 130 GiB gp3 | $0.0912/GB-month, baseline IOPS/throughput | $11.856/month while retained |
| One EIP | $0.005/hour, 730 retained hours | $3.65/month including stopped time |
| Private ECR storage | $0.10/GB-month, **assume 10 GB retained** | $1.00/month |
| S3 Standard storage | $0.025/GB-month first 50 TB, **assume 20 GB including versions** | $0.50/month |

With disks/EIP/assumed images/backups retained for the **entire 730-hour month**, baseline retained costs are **$17.006/month** plus compute:

| Running time in that month | Estimated subtotal |
|---|---:|
| 8 hours total | **$20.40** |
| 40 hours total | **$33.97** |
| 730 hours (always on) | **$326.67** |

These are estimates, not caps: ECR compressed storage and S3 versions must be measured. Add S3 requests, any billable internet/cross-region data transfer, optional snapshots, advanced SSM/high-throughput/KMS usage, optional monitoring/notifications/backend storage and applicable taxes. No paid optional CloudWatch/NAT/ALB/managed databases are included. Same-region ECR-to-EC2 transfer has no ECR transfer charge. Existing hostname/domain cost is excluded. Reprice the exact approved plan before apply.

The Compose container caps sum to **12.5 GiB across 21 configured services** (database bootstrap is one-shot). Sixteen GiB leaves limited OS/Docker/cache/startup headroom; 32 GiB is a conservative first verification choice. Local Docker has only ~3.83 GiB and the Mac has 8 GiB physical RAM. No heaps were arbitrarily reduced and the full local stack was not attempted. Actual AWS memory/CPU requirements remain a measurement gate, not a capacity guarantee.

## Security and release conditions

The seven prior Spring CVEs remain absent in the **recorded** passing migration scan: CVE-2026-47884, -47890, -47891, -47892, -59313, -59283 and -59270. The three unsuppressed MEDIUM findings remain documented: Commons JXPath 1.3 CVE-2022-40159/40160 (6.5, disputed vendor findings), Kafka clients 4.2.2 CVE-2026-41115 (4.3, advisory prerequisites reviewed in migration report). A passing unchanged threshold is not zero vulnerabilities.

The host introduces a separate check: [AWS advisory CVE-2026-89049](https://aws.amazon.com/security/security-bulletins/2026-107-aws/) affects SSM Agent versions below **3.3.4851.0**. No host version is known yet. Bootstrap installs the snap when absent but does not refresh an already installed old agent. The approval/deployment runbook therefore requires checking/updating it before access; restrict Session Manager documents and principals. No remote-host forwarding is needed for local Prometheus/Grafana tunnels.

Public infrastructure images mostly use version tags, not immutable digests. Before acceptance publication/deployment, inventory and scan the exact resolved PostgreSQL/Redis/Kafka/monitoring/base-image digests and record them with the release. ECR `scan_on_push` does not itself enforce an image vulnerability policy. No Docker OS security pass is claimed.

Signing-key or backup-key rotation, old snapshot restoration, database destructive restore, deleting volumes/topics, resetting offsets or wiping existing users are outside the normal rollback path. The existing rollback tool uses compatible images against current business data and refuses unsafe schema history. Full data-preserving AWS rollback must be executed before production approval.

## Exact approval checklist

No paid/deployment action is authorized by a checked preparation result. A human must approve each applicable action/input below before it is executed.

- [ ] Review all migration and staging diffs, fixture provenance and intentionally generated evidence; separately authorize a commit/push if desired. Freeze the exact migrated release SHA and semantic version.
- [ ] Supply the intended 12-digit account, operator SSO profile and dedicated `cloud-demo` ownership; verify real identity, no conflicting repository/OIDC/state ownership and no shared production host/data target.
- [ ] Approve Mumbai `ap-south-1`, `m7i.2xlarge` x86/32 GiB, approved AZ/AMI, 30+100 GiB encrypted gp3 and retained EIP. Approve eight-hour first runtime and the above retained-cost/always-on exposure; specify budget amount/recipient if enabling alerts. Budgets/timer are not a hard spending cap.
- [ ] Supply tester egress `/32`s and approve public port 80 solely for certificate challenge/redirect. Verify no world-open 443, SSH or internal/monitoring ports in the exact real plan.
- [ ] Supply approved hostname and validate current public DNS. Explicitly approve any DNS work separately; no automatic Route 53 creation is configured. Approve host-only loopback resolution for the deployed verifier.
- [ ] Review remote-state backend and authenticated Terraform plan: expected resources only, no unexpected replacement/destruction, retained-data/bucket protections intact. **Separately authorize apply/EC2 creation.**
- [ ] Approve protected GitHub publish/deploy environments, exact Mumbai variables and restricted OIDC trust. Require clean-SHA CI, fresh unchanged OWASP scan, npm audits, SBOM/image OS policy and the documented MEDIUM finding disposition. **Separately authorize publishing the 12 ECR images.**
- [ ] Provision/review the five private SecureStrings and secret/key permissions outside Terraform; verify persisted signing/backup keys and Kafka cluster ID. Confirm simulators only, OAuth disabled, no real messaging/payment credentials.
- [ ] Verify host SSM Agent >=3.3.4851.0, patches, IMDSv2, clock, EBS mount/ownership, Docker/Compose version, disk and memory. Approve only intended operator/session documents and private monitoring tunnels.
- [ ] Approve deployment of the reviewed immutable artifact and first-install synthetic account/role seeding only if appropriate; existing data remains intact. Do not approve an initial-seed flag on an established release.
- [ ] Approve browser/API synthetic writes and the complete positive/negative/authorization/SSE/outbox/DLT/memory cases in [the acceptance runbook](aws-staging-acceptance.md). Require fresh metrics and actual DLT offsets, not mocked routes or restarted counters.
- [ ] Approve encrypted backup +isolated restore, graceful restart and EC2 stop/start exercise, compatible A→B→A rollback, preserved-record ledger and final guarded shutdown. Database restore/snapshot overwrite is not included.
- [ ] Require executed PASS for every applicable real acceptance/recovery/security gate before considering production deployment. Record any failure honestly and keep that release blocked until resolved.

**READY FOR AWS STAGING APPROVAL**
