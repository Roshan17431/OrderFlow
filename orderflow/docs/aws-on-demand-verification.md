# AWS on-demand preparation — final verification

Assessment2026-10-15. Existing repository, Maven root `orderflow/`, frontend `orderflow/frontend/`; baseline **`a1b8d09276f75be52de4af832e95db8eccfd47de`**. No branches, commits, pushes, AWS account operations, paid resources, Terraform apply, DNS modifications or public deployment were performed. Initial Git tree was clean; existing Phase10/remediation work was reused. Public release remains blocked.

**Publication update:** After this preparation assessment, the owner explicitly authorized GitHub publication. The original Git inventory and machine-readable preparation snapshot below remain historical. Publication uses five logical commits and does not waive security gates or authorize AWS resources, DNS or deployment.

## A. Automation and persistence

- Added confirmed, account/region/instance/tag/STOP-guarded manual start/stop helpers. Starts are idempotent and preserve the current deadline; waits are bounded and remote output is suppressed.
- Added root-only startup/controller and independent eight-hour boot countdown, configurable by `ORDERFLOW_DEMO_MAX_RUNTIME_HOURS`/Terraform, with intentional bounded extension, safe normal poweroff and private persisted timestamp/reason records. No daily starts.
- Reused immutable release images, existing secret retrieval, Compose, health checks and release verification. Added a healthy resume pointer and pre-Docker verified boot marker. Failed candidates cannot be replayed automatically. Missing retained EBS blocks Docker rather than exposing new root-disk data directories.
- Fixed review-discovered expiry/extension and concurrent deployment/readiness races with shared locks; late extensions are rejected. Retained all existing container restart policies. Independent final review reports no important findings.
- PostgreSQL, Redis AOF, Kafka KRaft, Caddy certificates, JWT/backup keys, runtime configuration, release metadata and backups retain their existing encrypted EBS paths. Keys are never regenerated on boot. **Actual EC2 stop/start persistence: NOT RUN.** The operating guide provides the bounded synthetic-data/identity/key/certificate/session acceptance procedure; backups remain required.

## B. Costs and exact approval proposal

See [cost worksheet](aws-on-demand-costs.md) and [operation/verification guide](aws-on-demand-guide.md). Neither authorizes spending. Region/account/domain/budget/email are not configured. The current official Linux us-east-1 T3 table lists$0.1670/hour; the EBS$0.08 regional planning input still requires exact regional Calculator/SKU confirmation. Conditional example subtotal: **$16.05/month while stopped; $22.73 for40hours; $29.41 for80hours**, before S3, snapshots, DNS queries/renewal, monitoring, transfer, KMS and tax. Assumptions:130GiB gp3, retained EIP730hours,15GB ECR and$0.50 DNS allocation. Actual AWS bill is UNKNOWN.

```text
AWS account: UNKNOWN
Approved region: UNKNOWN (unapproved pricing example: us-east-1)
EC2 instance ID: UNKNOWN; proposed type t3.xlarge
vCPU / RAM: 4 /16GiB; evaluation target, capacity NOT VERIFIED
Architecture: x86_64 /linux/amd64 immutable application images
EBS: encrypted30GiB root +100GiB retained data, same AZ
ECR: 12 immutable private repositories
Public IPv4: proposed stable EIP; allocation disabled until approved
Domain /existing hosted zone /ACME email: UNKNOWN
HTTPS: Caddy80 redirect/ACME +443 application; persistent certificate store
Monthly fixed estimate: conditional$16.05; regional inputs/usage require confirmation
Running compute estimate: $0.1670/hour in the proposed example region
5-day /10-day base subtotal: conditional$22.73 /$29.41 plus extras
Auto-stop: 8hours from boot plus bounded checking/graceful-stop overhead
Monthly budget /alert email: UNKNOWN
Proposed alerts: actual$20/$40/$60; forecast100% of approved monthly budget
```

Expected resources requiring explicit approval: one VPC/public subnet/IGW/route table/security group; one EC2 plus root and retained encrypted EBS attachment;12 ECR repositories/lifecycles; one private encrypted versioned S3 backup/release bucket; scoped EC2 instance profile and existing GitHub publish/deploy OIDC roles/provider reuse or approved creation; five operator-supplied SecureStrings. Optional one EIP/association, one A-record in an existing owned zone, one monthly Budget, two CloudWatch alarms/log group and authorized SNS notification resources. No NAT gateway, load balancer, managed database or automatic startup scheduler is added.

EBS, EIP, stored images and backups still cost money while stopped. Standard CPU-credit mode avoids Unlimited surplus billing but can throttle. Budget alerts and the host timer are not guaranteed hard spending caps. **Before any AWS execution, explicitly approve the exact account/region/resource/address/domain/budget/email plan and reviewed regional pricing. Public deployment also requires security and real-journey blockers to be cleared.**

## C. Security disposition

Boot3.5.16, Framework6.2.19, Security6.5.11 and compatible Cloud2025.0.3 remain. Official advisory and Maven Central availability rechecked2026-10-15; compatible Framework6.2.20 and Security6.5.12 POMs still return404 on public Central and vendor lists enterprise-only availability. No repository entitlement is configured. No threshold change, suppression, scanner removal or incompatible Spring/Boot major override was made.

| Finding | Current affected line /compatible fix | Reviewed prerequisite and disposition |
|---|---|---|
| [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) | Framework6.2.0–6.2.19 /6.2.20 enterprise | XsltView class present; no XSLT view resolver/implicit rendering. Unpatched; retained. |
| [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) | Framework6.2.0–6.2.19 /6.2.20 enterprise | No attacker-controlled SSE view fragments; owned JSON notifications. Unpatched; retained. |
| [CVE-2026-47891](https://spring.io/security/cve-2026-47891/) | Framework6.2.0–6.2.19 /6.2.20 enterprise | Jaxb2XmlDecoder present, required Aalto absent in reviewed runtime trees. Unpatched; retained. |
| [CVE-2026-47892](https://spring.io/security/cve-2026-47892/) | Framework6.2.0–6.2.19 /6.2.20 enterprise | No functional WebFlux endpoints through DispatcherServlet. Unpatched; retained. |
| [CVE-2026-59313](https://spring.io/security/cve-2026-59313/) | Framework6.2.0–6.2.19 /6.2.20 enterprise | No functional MVC ServerResponse.sse(); SseEmitter with JSON. Unpatched; retained. |
| [CVE-2026-59283](https://spring.io/security/cve-2026-59283/) | Framework6.2.0–6.2.19 /6.2.20 enterprise | No SimpleEvaluationContext app usage/compiler enabling; reviewed compiler default OFF. Unpatched; retained. |
| [CVE-2026-59270](https://spring.io/security/cve-2026-59270/) | Security6.5.0–6.5.11 /6.5.12 enterprise | spring-security-ldap, UnboundID and embedded listener absent. Broad scanner match retained. |

The last complete scanner assessment (2026-10-13) broadly matches six against spring-core6.2.19 and one against security-core/oauth2-resource-server6.5.11; feature/module usage is distinguished above and in [the detailed assessment](phase-10-remediation.md). Absent prerequisites reduce observed exposure, but do not patch the dependencies or authorize suppression. Java application dependencies/source did not change during this on-demand task. Commercial compatible patches require verified entitlement/full-set testing; public future patches or a separately authorized support/migration decision remain options.

**Fresh OWASP assessment: NOT RUN.** Two actual2026-10-14 refresh attempts failed after25/20minutes. Today's official recent-feed probe again received HTTP200 but timed out after30seconds with1,471,531/2,271,710bytes. The unchanged CVSS9 gate has no completed passing assessment; no partial cache/offline bypass was used and no new CVE count is claimed. Release security gate remains **FAIL** until a reliable host/network completes the actual scan and findings are resolved.

Dependency change in this task: compatible development-only `@next/eslint-plugin-next` **16.4.0→16.4.1**. It still depends on fast-glob3.3.1→micromatch4.0.8→braces3.0.3. Latest published braces remains3.0.3 with no fixed release for [CVE-2026-93687 /GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm). Full npm audit **FAIL:4 high development findings**; production audit **PASS:0 findings**. No forced Next14 downgrade, rule removal or audit bypass was applied. Next16.4.0/ESLint10 compatibility and full checks passed after the plugin patch.

## D. Verification evidence

| Check | Result /scope |
|---|---|
| Full Java21 Maven `clean verify` | **PASS264**,0 failures/errors/skips,88 XML reports;3m55s |
| Source identity | **PASS761** Maven source/config files copied/hash-checked outside Documents |
| npm ci | **PASS** with final lockfile |
| Frontend lint /typecheck /build | **PASS /PASS /PASS** |
| Frontend Vitest /fixture Playwright | **PASS138 /PASS10**; mocked fixtures, not real commerce |
| Full npm audit /production audit | **FAIL4 high dev /PASS0** |
| Terraform fmt/init/backend=false/readonly lock/validate | **PASS**, no live plan/apply |
| Terraform mocked policies | **PASS14** |
| Base/cloud/production-like Docker configuration | **PASS**,12 cloud runtime policy tests |
| Caddy /monitoring | **PASS** config;8 base +15 cloud Prometheus rules, no public ACME/live targets |
| Systemd | **PASS** actual Ubuntu24 syntax; short elapsed countdown uses harmless recorder; real boot/shutdown NOT RUN |
| Shell syntax | **PASS35** scripts |
| Release/backup/initial-seed automation | **PASS36** |
| Cloud tools, on-demand safety and isolated TLS contracts | **PASS65**, including23 on-demand tests; not full-stack E2E |
| GitHub Actions syntax | **PASS** actionlint1.7.11; remote execution **NOT RUN** |
| Source secret scan /log redaction /diff-check | **PASS /PASS /PASS** |
| Fresh OWASP assessment | **NOT RUN**, unreliable feed; last complete result failed; gate not waived |
| Full real-backend browser/API E2E | **NOT RUN**,4GiB Docker VM insufficient, no new full-stack attempt |
| Actual EC2 boot/auto-stop/stop-start persistence | **NOT RUN** |
| AWS staging /public HTTPS /DNS | **NOT RUN /NOT RUN /NOT RUN** |
| New application image builds/publication | **NOT RUN**; runtime image sources unchanged; historical24 builds not recounted |

Original frontend build hit duplicate generated `.next/cache/.../LOG2`. Cache was preserved. A clean hash-matched source copy built successfully. Its first test run omitted the shared simulator CLI helper (136PASS/2FAIL); the identical helper was restored and all138 passed. Final lint-plugin/lockfile verification then reran installation, audits, lint, build, typecheck,138tests and10fixtures successfully except the accurately retained audit findings. No application source fix was needed for these verification-environment failures.

Current physical RAM8GiB; Docker VM4,108,632,064bytes (~3.83GiB). No repeated whole-stack attempt or arbitrary heap cuts. Proposed16GiB host has12.5GiB aggregate caps and must be measured with builds/browser off-host. Actual JVM/native/container/host memory, OOM/restart/CPU-credit metrics, bounded rate-limit probe, full business journey and restart comparisons remain staged acceptance items. See the guide for exact prepared checks and safe ownership constraints.

Historical image/restore results retain their original dates and are not credited as fresh cloud or persistence verification. Machine-readable evidence is in [on-demand-evidence.json](../deploy/on-demand-evidence.json).

## E. Git inventory and future grouping

HEAD remains **`a1b8d09276f75be52de4af832e95db8eccfd47de`**. No unrelated work was overwritten or discarded. 14 tracked files modified and 16 new files; nothing staged/committed/pushed by this task.

Modified tracked files:

- `.github/workflows/ci.yml`
- `orderflow/frontend/package-lock.json`
- `orderflow/frontend/package.json`
- `orderflow/infrastructure/bootstrap/bootstrap-ec2.sh`
- `orderflow/infrastructure/terraform/compute.tf`
- `orderflow/infrastructure/terraform/main.tf`
- `orderflow/infrastructure/terraform/monitoring.tf`
- `orderflow/infrastructure/terraform/terraform.tfvars.example`
- `orderflow/infrastructure/terraform/tests/security.tftest.hcl`
- `orderflow/infrastructure/terraform/variables.tf`
- `orderflow/scripts/cloud/tests/test_tools.py`
- `orderflow/scripts/cloud/verify_journey.py`
- `orderflow/scripts/deploy/cloud.py`
- `orderflow/scripts/deploy/tests/test_cloud.py`

Untracked files:

- `orderflow/deploy/on-demand-evidence.json`
- `orderflow/docs/aws-on-demand-costs.md`
- `orderflow/docs/aws-on-demand-guide.md`
- `orderflow/docs/aws-on-demand-verification.md`
- `orderflow/docs/superpowers/plans/2026-10-15-on-demand-aws.md`
- `orderflow/infrastructure/systemd/docker-orderflow-mount.conf`
- `orderflow/infrastructure/systemd/orderflow-demo-stop.service`
- `orderflow/infrastructure/systemd/orderflow-demo-stop.timer`
- `orderflow/infrastructure/systemd/orderflow-demo.service`
- `orderflow/infrastructure/systemd/validate-units.sh`
- `orderflow/infrastructure/terraform/on-demand.tf`
- `orderflow/scripts/cloud/on_demand.py`
- `orderflow/scripts/cloud/on_demand_host.py`
- `orderflow/scripts/cloud/start-demo.sh`
- `orderflow/scripts/cloud/stop-demo.sh`
- `orderflow/scripts/cloud/tests/test_on_demand.py`

Once separately authorized, logical publication groups are: (1) guarded operations/controller/systemd; (2) Terraform/bootstrap/cost controls; (3) release replay/locking safety and journey contracts; (4) compatible lint plugin/lockfile; (5) CI validation and dated guide/cost/evidence. This is a proposal only; no Git history was created.

**PREPARATION COMPLETE — SECURITY BLOCKERS REMAIN**
