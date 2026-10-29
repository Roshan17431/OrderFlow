# Migrated release freeze and AWS approval worksheet

Current follow-up: [October 29 infrastructure security remediation](infrastructure-security-remediation-2026-10-29.md) completes the original Caddy, Kafka and exporter remediation and records exact residual findings and runtime limitations. Grafana still blocks infrastructure security approval. The October 27 results below are retained as historical evidence.

Assessment: **2026-10-27**. Decision: **BLOCKED**. The migrated source is committed locally and its unchanged Maven security gate passes. Newly executed container scans report unresolved findings, the additional Java-image library analysis is incomplete, and the retained JRE predates the current Java 21 patch release. These prevent AWS approval. Full real-backend acceptance remains **NOT RUN**.

No Git push, image publication, AWS account operation, EC2 launch, Terraform apply, paid resource creation or DNS modification occurred. This worksheet authorizes none of those actions.

## Frozen source and Git review

Starting branch `master`, baseline `4197fbe1095663f0dbc32d2705bf95b202fb2702`: 110 modified tracked files and 61 untracked individual files. All 171 were reviewed. No unrelated user work was identified or discarded. Two infrastructure freeze changes were added: immutable cloud infrastructure-image references and their regression assertion.

| Local source commit | Scope |
|---|---|
| `1649f74007f086c3b14b962f4d9ca8d4db93b8e9` | Maven reactor/runtime migration to Boot 4; necessary main/test API adaptations and 44 synthetic compatibility fixtures; 146 paths |
| `a46d446dc3f00a8db18446213b852c32e1596bcd` | Focused compatibility tests, fixture provenance, migration evidence and documentation; 14 paths |
| `3c2b43661c2e3b96f57c1456d8d2788c1fc0ac75` | Restricted Mumbai staging configuration, persistence/acceptance preparation and immutable infrastructure images; 13 paths |

The final documentation/evidence commit follows these source commits. Its immutable full SHA, tree SHA, four-commit ledger and post-commit clean-tree result are recorded outside Git in `/Users/roshan/.cache/orderflow-release/2026-10-27/frozen-release.json` and returned to the user. A commit cannot contain its own SHA without changing that SHA. The verified application/configuration source commit is `3c2b43661c2e3b96f57c1456d8d2788c1fc0ac75`; the final commit changes evidence/documentation only.

Review found no probable real credentials or accidental runtime artifacts. The 44 original SQL/JSON/JWT/session fixture hashes are preserved; signed test JWTs contain synthetic identities and public verification material, with no private signing key. All 23 historical Flyway SQL migrations are unchanged. Markdown, JSON and the vulnerability CSV are intentionally versioned evidence. An independent code review found no Critical/Important correctness issues in the migration/staging changes.

Private keys, populated env/account files, Terraform state/plans, dependency databases, scan logs, local toolchains, `target/`, `.next/`, `node_modules/`, `.terraform/` and browser output remain outside Git or ignored. Generated Next.js declaration-path drift was returned to its exact original bytes. A clean tree does not imply deleting ignored local build/test data. Historical October 27 reports retain their original results and now identify this report as the current decision.

## Executed verification

Portable commands, counts and limitations: [release verification JSON](../deploy/release-verification-2026-10-27.json). Full local logs: `/Users/roshan/.cache/orderflow-release/2026-10-27/`.

| Verification | Result | Actual evidence / limits |
|---|---|---|
| Java 21 full clean Maven verification | PASS | 226 unit +60 integration tests; zero failures, errors or skips |
| Restored Boot 3 schema compatibility repeat | PASS | 40 integration tests against restored schemas; repeat subset, not 40 additional unique tests |
| Fresh OWASP data and unchanged gate | PASS | Dependency-Check 12.2.2, 166 dependency entries, CVSS threshold 9 unchanged, no suppressions or analysis exceptions; NVD checked `2026-10-27T14:42:53Z` |
| Frontend installation, audits, lint, typecheck, tests/build | PASS | `npm ci`, both audits, lint, typecheck, 138 Vitest tests and production build |
| Mocked browser fixtures | PASS | 10 Playwright fixture tests; no real commerce credit |
| Terraform | PASS | Backend-disabled init, fmt/validate and 17 mocked plan tests; no authenticated account plan |
| Cloud/deployment/runtime policy tests | PASS | 65 cloud +36 deployment +13 runtime policy tests =114 |
| Docker Compose and source safety checks | PASS | Production-like validation, source secret-pattern/log-redaction checks and Git whitespace checks |
| Caddy, systemd, monitoring | PASS | Isolated actual Caddy routing/SSE/redaction; Ubuntu `systemd-analyze verify`; default/cloud Prometheus configs and eight application +seven cloud rules. Real host boot/public TLS/live targets not tested |
| Restart persistence, fresh target architecture probe | PASS | Migrated amd64 auth/PostgreSQL/Redis/Kafka container recreation preserves user, Flyway history, AOF record, Kafka message, key bytes/JWKS and a pre-restart JWT. Unique temporary resources cleaned up; no existing data removed |
| Encrypted backup/isolated restore | PASS — earlier staging evidence | Eight-database isolated backup/restore passed in the preceding staging task; not rerun during freeze. This is not live AWS recovery |
| Application image identity/platform inventory | PASS | Existing 24 migration images inspected: 12 applications ×amd64/arm64. No application image rebuild in this freeze task; no production source changes after verification |
| Container OS/available non-Java library scans | PASS — execution | 32 completed scan executions; security disposition below is BLOCKED |
| Additional Trivy Java-image library analysis | NOT RUN | 944.5-MiB Java metadata download timed out after the 900-second limit (actual 915 seconds, exit 124). Reactor OWASP coverage does not cover Kafka broker bundled JARs |
| EC2 host OS/SSM patch verification | NOT RUN | No host provisioned; no host OS security pass claimed |
| Full commerce/browser/API/negative/refund/SSE/outbox/DLT acceptance | NOT RUN | Requires all actual services through Caddy on a sufficiently provisioned host |
| AWS boot, stop/start, retained EBS and A→B→A rollback | NOT RUN | Preparation/tests are available; no AWS operations executed |

The resolved migration remains Boot **4.1.2**, Cloud BOM **2025.1.4**, Framework **7.0.10**, Security **7.1.2** and Java **21**. The complete resolved matrix remains in the [migration report](boot4-migration-2026-10-27.md). No dependency-security threshold, suppression or exclusion was changed.

The seven original Spring findings are absent from the fresh OWASP report: **CVE-2026-47884, CVE-2026-47890, CVE-2026-47891, CVE-2026-47892, CVE-2026-59313, CVE-2026-59283 and CVE-2026-59270**. Three unsuppressed project dependency findings remain:

| Dependency | CVE | Severity / CVSS |
|---|---|---|
| Commons JXPath 1.3 | CVE-2022-40159 | MEDIUM /6.5 |
| Commons JXPath 1.3 | CVE-2022-40160 | MEDIUM /6.5 |
| Kafka clients 4.2.2 | CVE-2026-41115 | MEDIUM /4.3 |

Existing vendor disputes/prerequisite analysis remains in the migration report. The passing threshold permits these findings; it does not make the release vulnerability-free. Both fresh project `npm audit` and `npm audit --omit=dev` report **zero** findings, including no recurrence of the former lint-chain findings.

## Image identities, vulnerabilities and required remediation

[Image inventory](../deploy/release-image-inventory-2026-10-27.json) records all 24 application configuration IDs/platforms and all eight infrastructure index/amd64 child-manifest digests. Local application `RepoDigests` are empty: **ECR manifest digests do not exist yet**. A local configuration digest is not a registry manifest digest. A future authorized clean-SHA build/publication must create a 12-image amd64 ECR digest manifest and scan those exact published artifacts.

All eight infrastructure index digests are now pinned in cloud Compose to the previously verified baseline, including PostgreSQL, Redis, Kafka, Caddy, Prometheus, Grafana, node-exporter and blackbox-exporter. Target amd64 child manifests were pulled/scanned by digest. Pinning prevents tag drift and does not fix vulnerabilities. The development Compose file is not represented as frozen.

Trivy **0.76.1** was obtained from the [official release](https://github.com/aquasecurity/trivy/releases/tag/v0.76.1), archive SHA-256 verified against release metadata/checksums. Fresh vulnerability DB updated `2026-10-27T13:12:49.474279883Z`, downloaded `2026-10-27T14:43:41.614997Z`. Scans include all severities/unfixed findings, use `/dev/null` as ignore file and upload no image data. Exit 0 indicates scanner completion, not a security pass.

The [complete findings CSV](../deploy/release-container-findings-2026-10-27.csv) retains **1,377 image/package occurrences**, representing **399 advisory IDs: 391 CVEs +8 other advisory IDs**. Counts duplicate shared packages across images/binaries and are not counts of proven exploitable vulnerabilities. Nothing was suppressed or deleted.

| Scanned image group | Images | CRITICAL | HIGH | MEDIUM | LOW | UNKNOWN |
|---|---:|---:|---:|---:|---:|---:|
| Eleven Java apps, both architectures | 22 | 0 | 22 | 0 | 0 | 0 |
| Frontend, both architectures | 2 | 0 | 22 | 20 | 2 | 0 |
| blackbox-exporter amd64 | 1 | 2 | 50 | 57 | 3 | 2 |
| Caddy amd64 | 1 | 0 | 8 | 17 | 45 | 3 |
| PostgreSQL amd64 | 1 | 0 | 0 | 1 | 0 | 2 |
| Grafana amd64 | 1 | 14 | 171 | 261 | 63 | 10 |
| Kafka amd64 | 1 | 5 | 63 | 104 | 57 | 2 |
| node-exporter amd64 | 1 | 1 | 46 | 54 | 3 | 2 |
| Prometheus amd64 | 1 | 4 | 106 | 117 | 11 | 4 |
| Redis amd64 | 1 | 0 | 2 | 17 | 2 | 2 |
| **Total** | **32** | **26** | **490** | **648** | **186** | **27** |

Scope: OS packages in all 32; bundled Go/JS libraries where available in frontend/non-Java infrastructure. Additional Java image-library results are unavailable. The fresh Maven gate covers the application reactor, not broker JARs or the JRE itself.

Concrete dispositions and next work:

- **Java/Caddy Alpine Brotli:** CVE-2026-111497, `brotli-libs` 1.2.0-r1/r0; scanner/vendor fixed version **1.2.1-r0**. Malformed untrusted decompression is a vendor prerequisite, not a demonstrated OrderFlow exploit. Rebuild on patched supported base images and rescan. [Alpine advisory](https://security.alpinelinux.org/vuln/CVE-2026-111497).
- **Java runtime:** executed backend JRE is Temurin **21.0.12.1+1**. Official Temurin **21.0.13+9** was released October 22; the retained JRE is behind that security patch level. Updating within Java 21, rebuilding both architectures and repeating affected tests/scans is required before approval. Do not infer a clean JRE from the OS/Maven scan. [Official Temurin release](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.13%2B9), [Java 21.0.13 security baseline](https://www.oracle.com/java/technologies/javase/21-0-13-relnotes.html).
- **Frontend base-image tooling:** runtime Node is **22.23.4**; the 44 findings across both images are under global `usr/local/lib/node_modules/npm/node_modules/`, outside the project lockfile. Examples: `ip-address` 10.1.0 →10.3.1 (CVE-2026-69192), `picomatch` 4.0.3 →4.0.4 (CVE-2026-33671), `sigstore` 3.1.0 →4.1.1 (CVE-2026-48815), and `http-cache-semantics` 4.2.0 (CVE-2026-93748; no fixed version reported). Remove unused runtime package-manager tooling or update it compatibly, then rebuild/rescan; do not change ESLint to address image tooling.
- **Infrastructure:** pending vendor upgrades/rebuilds and applicability review include bundled Go stdlib/gRPC, Grafana plugins/libraries and Kafka Alpine packages. Scanner examples: gRPC CVE-2026-33186 →1.79.3, Go CVE-2025-68121 →1.24.13/1.25.7, pgx/v5 CVE-2026-33815/33816 →5.9.0, Kafka GnuTLS CVE-2026-33845/42010 →3.8.13-r0. These are scanner-reported fixes, not permission to replace embedded libraries arbitrarily. Prefer a supported vendor image and test it. Go's advisory has TLS configuration/session-resumption prerequisites. [Go vendor record](https://pkg.go.dev/vuln/GO-2026-4337).
- **Vendor applicability notes retained alongside raw severity:** five CVE-2026-31789 CRITICAL occurrences are OpenSSL's LOW, 32-bit-only certificate-conversion issue; scanned targets are 64-bit. One CVE-2025-41115 occurrence requires Grafana Enterprise SCIM enabled; configured OSS Grafana has neither prerequisite. Four older Grafana 2018/2023 matches use a pseudo-version and require identity reconciliation. None is removed. [OpenSSL advisory](https://openssl-library.org/news/secadv/20260407.txt), [Grafana SCIM advisory](https://grafana.com/security/security-advisories/cve-2025-41115/), [Grafana OAuth advisory](https://grafana.com/security/security-advisories/cve-2023-3128/).
- **Coverage gap:** finish the Trivy Java metadata download in a suitable network environment and scan Kafka broker/application JAR contents. No passing result can be substituted for the timed-out analysis. Confirm the actual EC2 host OS/SSM patch level after separately authorized provisioning.

Raw critical advisory IDs: CVE-2018-15727, CVE-2023-3128, CVE-2025-41115, CVE-2025-68121, CVE-2026-31789, CVE-2026-33186, CVE-2026-33815, CVE-2026-33816, CVE-2026-33845, CVE-2026-42010 and GHSA-r277-6w6q-xmqw. The CSV provides every affected image/package/version, reported fix, raw severity and current disposition. **1,323 occurrences still require remediation or vendor applicability review**; 44 global-npm occurrences and the ten vendor/identity notes above are separate categories, not approvals or suppressions.

## Mumbai plan review

The restricted template selects `ap-south-1`, **m7i.2xlarge**, x86/amd64, **8 vCPU /32 GiB**, encrypted **30-GiB root +100-GiB retained gp3 data**, stable retained EIP and `enable_route53=false`. Compose caps total **12.5 GiB** across 21 configured services (database bootstrap is one-shot). This host's Docker allocation is approximately 3.83 GiB with 8 GiB physical RAM. No reduced-heap workaround or full local-stack run was attempted. Thirty-two GiB provides initial headroom; real memory peaks/CPU, startup and repeated commerce stability remain acceptance gates.

| Reviewed control/resource | Static assessment and remaining conditions |
|---|---|
| Public ingress | Port 80 only for ACME HTTP-01/HTTPS redirect; 443 only approved tester IPv4 `/32`s. No SSH/internal/monitoring public ports. **Explicitly select the restricted template**: general Terraform default remains world-open HTTPS for existing consumers. Placeholder CIDRs are not real tester access |
| Network | Dedicated VPC/public subnet/IGW/route, no NAT/ALB; egress 80/443 +VPC-only TCP/UDP DNS. Real SG enforcement NOT RUN |
| IAM/OIDC/ECR | Three roles, one instance profile, seven scoped policies, exact-account/repository/environment trust; 12 immutable ECR repositories and lifecycle policies. Account ownership/existing OIDC provider, actual permissions and protected environments require review. Scan-on-push alone does not enforce vulnerability approval |
| Encryption/data retention | Encrypted root/data EBS; prevent-destroy data volume and private encrypted/versioned backup bucket; retained EIP and image/artifact storage continue charging while stopped. No destructive database restore in normal rollback |
| SSM/secrets | Five private Standard SecureStrings outside Terraform/state; files mode 0600, persisted JWT keys read-only UID/GID 10001 mode 0400. Verify host SSM Agent **>=3.3.4851.0**; bootstrap does not refresh an already installed old agent. [AWS CVE-2026-89049 bulletin](https://aws.amazon.com/security/security-bulletins/2026-107-aws/) |
| systemd/start-stop | Syntax and guarded automation passed; dedicated `cloud-demo` identity/account/instance checks, eight-hour stop guard, no scheduled automatic starts. Real boot/stop/timer behavior NOT RUN; timer/budgets are not spending caps |
| Persistence/recovery | Fresh bounded container-recreation probe passed. Full EBS reboot/stop-start, all-service recovery, measured RTO/RPO and data-preserving A→B→A rollback NOT RUN |
| Acceptance transport | Verify public DNS/TLS externally, then host-only hostname→127.0.0.1 resolution for host-side verifier while preserving TLS hostname/SNI; restricted SG otherwise prevents self-access via public IP. No public DNS change has occurred |

For one tester CIDR/new OIDC provider the template proposes **61 Terraform objects /60 AWS resources**, excluding the nested root disk: one EC2, one EBS data volume/attachment, one EIP/association, one VPC/subnet/IGW/route table/association, one SG +two ingress/four egress, 12 ECR repositories +12 lifecycle policies, one S3 bucket +six bucket controls, three IAM roles/one profile/seven policies/one OIDC provider, and one non-AWS `terraform_data` guard. Each extra CIDR adds one resource; reusing OIDC removes one. The executed mocked two-CIDR case has 62 creations, zero changes/deletes. This is not an authenticated plan of the account.

There is no RDS/MSK/ElastiCache, NAT, ALB, Route 53, new CMK, scheduled EBS snapshot, CloudWatch/SNS or budget resource in this selection. Remote state, five SSM parameters, domain ownership and GitHub environments are external prerequisites. Single AZ/broker and replication factor 1 are staging limitations, not HA guarantees.

## Expected AWS charges

USD public Mumbai On-Demand snapshot retrieved October 27 (EC2/S3 effective October 1; offer headers rechecked unchanged). Sources: [AWS EC2/EBS Mumbai offer](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonEC2/current/ap-south-1/index.csv), [S3 Mumbai offer](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonS3/current/ap-south-1/index.csv), [IPv4 pricing](https://aws.amazon.com/vpc/pricing/), [ECR pricing](https://aws.amazon.com/ecr/pricing/).

| Item | Assumption | Charge |
|---|---|---:|
| m7i.2xlarge | Linux/shared On-Demand | $0.4242/running hour |
| gp3 EBS | 130 GiB, $0.0912/GB-month, baseline IOPS/throughput | $11.856/month retained |
| EIP | $0.005/hour ×730 hours | $3.65/month retained |
| ECR | Assumed 10 GB ×$0.10/GB-month | $1.00/month |
| S3 | Assumed 20 GB including versions ×$0.025/GB-month | $0.50/month |

With all storage/EIP retained for the full 730-hour month: **8 compute hours ≈$20.40**, **40 hours ≈$33.97**, **always on ≈$326.67/month**. Retained subtotal alone is **$17.006/month**. These exclude taxes/FX, requests, variable data transfer, extra image/backup versions, optional snapshots/monitoring/KMS/backend costs and domain charges. Reprice the actual approved plan. A proposed $60 monthly budget needs an approved recipient; alerts/timers do not impose a hard cap.

## Final approval worksheet — inputs and required evidence

Every unchecked row is required before the corresponding future action. No secret value belongs in Git or this worksheet.

| Approval/input | Current value | Required before provisioning/deployment |
|---|---|---|
| Security disposition | **BLOCKED** | Remediate or evidence-review all applicable HIGH/CRITICAL image findings, patch Java 21 runtime, finish Kafka JAR scan, rerun affected tests and exact-digest scans; no suppressions/gate changes |
| Release identity | Local frozen source above; final SHA in external receipt | Approve semantic version/final SHA, later Git publication separately, clean-SHA CI and immutable 12-image amd64 ECR manifest; approve image publication separately |
| AWS identity | **REQUIRED** | 12-digit account, operator SSO profile/roles; verify STS identity before actions, dedicated `cloud-demo` ownership, existing resources/OIDC/state/repository conflict/import decisions |
| Region/capacity | Proposed `ap-south-1`, m7i.2xlarge | Approve region/AZ, exact supported Ubuntu AMI, quota/capacity, 30+100 GiB encrypted gp3 and retained EIP |
| Domain/TLS | **REQUIRED** | Owned approved hostname, public DNS status, ACME email; DNS work requires separate authorization |
| Tester access | **REQUIRED** | Actual fixed public IPv4 `/32` list; no documentation placeholders/world-open 443; approve public ACME/redirect-only 80 and private SSM monitoring access |
| Cost control | Estimates above; proposed eight-hour first run | Approved monthly USD budget, runtime maximum, actual/forecast alert thresholds and authorized recipient; accept retained/always-on exposure |
| State/plan | Offline tests only | Existing encrypted access-controlled remote-state backend/key/region/locking; authenticated saved Terraform plan reviewed for exact resources, no unexpected replacement/destruction, DNS disabled, retention intact; plan/state kept private |
| IAM/GitHub | Templates reviewed only | Exact repo/default branch, protected publish/deploy environments/reviewers, OIDC role/account/region variables, least-privilege operator and Session Manager document access |
| Secrets | **REQUIRED, private only** | Five `/orderflow/cloud-demo/runtime/` SecureStrings: `runtime.env`, `jwt-private.pem`, `jwt-public.pem`, `backup.key`, `cloud-accounts.json`; persisted key pair/key ID, Kafka cluster ID, synthetic accounts, simulator-only settings, OAuth disabled unless separately approved |
| Resource creation | **NOT AUTHORIZED** | Explicit approval for exact reviewed Terraform apply, EC2 and other billable resources; no creation by this task |
| Host/deployment | **NOT RUN** | SSM patch floor, OS patches, IMDSv2, clock, EBS mount/ownership, Docker/Compose/disk/memory, external TLS/SG tests; authorize exact-digest deployment separately |
| Real acceptance | **NOT RUN** | Authorize synthetic browser/API writes; execute registration→cart→checkout→reservation→signed simulated payment→PAID/commit→assignment→pickup/finalization→delivered, negative/refund/authz/SSE/reporting/outbox/DLT checks, monitoring and memory measurements in [acceptance runbook](aws-staging-acceptance.md) |
| Recovery/shutdown | **NOT RUN** | Encrypted backup/isolated restore, known healthy A before A→B→A rollback, preserved-record ledger, approved stop/start and final guarded shutdown; no restoring old business records or deleting live volumes |

**BLOCKED**
