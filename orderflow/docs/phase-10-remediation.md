# Phase 10 release blocker remediation

Reviewed on 2026-10-14 against published baseline `8540d58fc512e98d93d171e37f6bf041a79af117`. Spring Boot stays on 3.5.16. No suppression, threshold increase, scanner removal, unsupported Spring 7 override or Boot 4 migration was applied. No branches, commits, pushes, AWS resources, Terraform apply or DNS changes are authorized for this task.

## Seven Spring findings

The earlier report flags six advisories against `org.springframework:spring-core:6.2.19` and one against `spring-security-core` / `spring-security-oauth2-resource-server:6.5.11`. These broad scanner matches identify library families; the actual affected features belong to the modules described below. Fresh runtime dependency trees confirm Framework 6.2.19 and Security 6.5.11, including after the compatible Cloud update.

| CVE and official advisory | Affected current line / compatible fixed version | Component, conditions and disposition |
| --- | --- | --- |
| [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) | Framework 6.2.0–6.2.19; fix **6.2.20, enterprise only** | `XsltView` is present in `spring-webmvc`; no XSLT resolver/view configuration or implicit XSLT rendering found. Business controllers return JSON; Eureka's private views use FreeMarker. Prerequisites not observed; library remains unpatched and gate finding retained. |
| [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) | Framework 6.2.0–6.2.19; fix **6.2.20, enterprise only** | Requires MVC/WebFlux view fragments sent over SSE with attacker-controlled content for other users. OrderFlow streams owned structured `NotificationResponse` data through `SseEmitter`; no `FragmentsRendering` use found. Prerequisites not observed; retained. |
| [CVE-2026-47891](https://spring.io/security/cve-2026-47891/) | Framework 6.2.0–6.2.19; fix **6.2.20, enterprise only** | `Jaxb2XmlDecoder` exists in `spring-web`, but **Aalto is absent from every runtime dependency tree**. No Aalto codec configuration found. The vendor's XML-parser prerequisite is absent in the reviewed build; retained. |
| [CVE-2026-47892](https://spring.io/security/cve-2026-47892/) | Framework 6.2.0–6.2.19; fix **6.2.20, enterprise only** | Requires functional WebFlux endpoints hosted through `DispatcherServlet`. Gateway is reactive Netty; business MVC controllers use annotations. No such functional endpoint/servlet combination found. Prerequisites not observed; retained. |
| [CVE-2026-59313](https://spring.io/security/cve-2026-59313/) | Framework 6.2.0–6.2.19; fix **6.2.20, enterprise only** | Requires MVC functional `ServerResponse.sse()` with unstructured attacker-controlled text. Actual notification code uses `SseEmitter` and JSON payloads, keyed by authenticated user. Prerequisites not observed; retained. |
| [CVE-2026-59283](https://spring.io/security/cve-2026-59283/) | Framework 6.2.0–6.2.19; fix **6.2.20, enterprise only** | `SimpleEvaluationContext` exists in `spring-expression`. No application usage or IMMEDIATE/MIXED compiler setting found. A Java 21 probe of the resolved library confirms the parser's default compiler mode is **OFF**. Reviewed prerequisites are absent; retained. |
| [CVE-2026-59270](https://spring.io/security/cve-2026-59270/) | Security 6.5.0–6.5.11; fix **6.5.12, enterprise only** | Requires embedded `UnboundIdContainer` and reachable LDAP listener. **`spring-security-ldap`, UnboundID SDK and that class are absent from runtime dependencies**; no embedded LDAP configuration exists. Component/prerequisites absent in the reviewed build; broad scanner finding retained. |

No compatible public fix for these seven was available when Maven Central metadata was rechecked. Boot 3.5.16, Framework 6.2.19 and Security 6.5.11 remain the latest published versions of those public lines. No Maven enterprise repository/credentials are configured. Presence of a vulnerable class does not prove exploitation, and absence of observed prerequisites does not patch the library or authorize suppression. Vendor severity differs from the previous NVD/Dependency-Check scores; the raw CVSS 9 release gate stays enforced.

Available remediation paths are authenticated access to the vendor's compatible enterprise patches, a future compatible public patch if published, or a separately authorized supported-stack/policy review. Entitlement, artifact availability and the complete dependency set must be verified before using enterprise versions. Spring 7 / Security 7 are not compatible drop-in fixes for this Boot 3.5 stack. No policy exception was made here.

## Compatible changes and configuration defect

`spring-cloud.version` changes **2025.0.2 → 2025.0.3**, the latest public service release in the same compatible train. [Spring's compatibility table](https://spring.io/projects/spring-cloud/) maps 2025.0.x to Boot 3.5.x; [the release notes](https://github.com/spring-cloud/spring-cloud-release/releases/tag/v2025.0.3) describe the update. Resolved Gateway is 4.3.5, Netflix Eureka client 4.3.3 and Commons 4.3.3. This update does not claim to fix the seven Framework/Security advisories. The public Cloud train is marked end-of-life by the current vendor page; longer-term support is a separate ownership decision.

Previously verified Tomcat 10.1.61, Netty 4.1.139.Final, FreeMarker 2.3.35, HTTP Client 5.6.5 and matching HTTP Core 5.4.4 pins remain in place. Framework and Security BOM versions were not forced or partially upgraded.

The CI cloud-monitoring validator had a real Docker mount defect: mounting the entire base directory read-only at `/etc/prometheus` prevented Docker from creating the additional cloud-alert file mountpoint. Its original command failed with exit 125. The workflow now mounts only the rules directory, alongside the two individual cloud configuration files. The actual corrected command passes Prometheus validation of all **15 rules**, and actionlint passes. Runtime Compose already uses individual mounts; no monitoring/security gate was disabled.

## Frontend findings and cache recovery

Full `npm audit` reproduces **four high development findings**, all caused by one advisory: [CVE-2026-93687 / GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm). Chain:

`@next/eslint-plugin-next@16.4.0 → fast-glob@3.3.1 → micromatch@4.0.8 → braces@3.0.3`.

The advisory affects `braces <=3.0.3`; no patched version is published. Registry checks show 16.4.0 is the current stable Next lint plugin, 4.0.8 the current micromatch, and braces 3.0.3 the latest braces. Even newer fast-glob 3.3.3 retains that vulnerable micromatch/braces chain. npm's proposed 14.2.35 plugin downgrade crosses the application's Next 16 major line and was rejected. No incompatible override, unstable release, vendored parser replacement or lint-rule removal was added.

These packages are development dependencies. `npm audit --omit=dev` reports **zero findings**. The vendor plugin's glob processing consumes configured root-directory patterns; the current ESLint configuration sets no custom `settings.next.rootDir` and passes no customer inputs into it. This reduces observed exposure but does not repair braces or make the full audit pass. Future trusted tooling upgrades must be rechecked; maintain isolated, unprivileged CI rather than treating lint tooling as a public service.

The original frontend failed typecheck/build due to identical `routes.d 2.ts` and `routes.d 3.ts` copies under generated `.next/dev/types`. The full old `.next` cache was preserved at the private local path recorded in evidence and regenerated. Source TypeScript rules, includes and lint rules were kept. Clean build/lint/typecheck pass; **138 Vitest tests and 10 fixture browser tests pass**. `next-env.d.ts` was restored to its original generated import after fixture development-server execution. No frontend dependency downgrade or source workaround was necessary.

## Verification environment and real workflow

This Mac has **8 GiB physical memory**; Docker exposes 4,108,632,064 bytes (about **3.83 GiB**, configured as a 4 GiB VM). The owner confirmed there is **no existing larger verification host**. Full real-backend Playwright/API workflows are **NOT RUN in this remediation**. The earlier 2026-10-13 attempts remain **FAIL**, including a confirmed inventory OOM and API preflight failure. Fresh mocked browser tests are not credited as real commerce or deployed verification.

No application JVM heap or Docker Desktop memory setting was reduced/changed to force an end-to-end result. Existing cloud configuration totals **12.5 GiB of container limits**, reserving 3.5 GiB on the proposed 16 GiB host; Java heaps consume at most half their container limits. A **native Linux host with at least 16 GiB RAM and 4 vCPU** is the minimum evaluation environment implied by this configuration. **24–32 GiB RAM and 8 vCPU** is a more comfortable verification environment when builds, browser and diagnostics share the host. On Docker Desktop, allocate at least 16 GiB to Docker and retain host/browser headroom; the current 8 GiB Mac cannot meet that requirement. These are engineering budgets, not a measured throughput guarantee; [Docker documents the VM allocation boundary](https://docs.docker.com/desktop/settings-and-maintenance/settings/).

Once a suitable authorized demo/staging host exists, run the existing full signed-payment/delivery/SSE/operations journey through Caddy with the protected synthetic accounts and private Prometheus. Require stable all-eleven scrapes, zero failed outbox records, drained backlog and no DLT growth. Check registration/login, browsing/cart/checkout, reservation, PROCESSING then signed SUCCEEDED/PAID, commitment, exact owned delivery assignment, pickup/finalization/completion, real persisted notifications/SSE and operations projections. Do not activate a busy agent or complete other users' deliveries. No such complete PASS is recorded today.

Java verification initially stalled while scanning a duplicate generated `GatewayCorrelationContext 3.class`. The original artifacts were preserved. Tests were run on **761 copied and hashed current Maven source/configuration files** outside Documents; their bytes match the repository. Affected gateway/registry/auth/notification tests pass, followed by full Java 21 `clean verify`: **264 tests, zero failures/errors/skips, 88 XML reports**. Runtime dependency trees and the aggregate SBOM were regenerated. This is an isolated test build, not an independently provisioned full-stack host.

## Fresh check results

| Check | Result and scope |
| --- | --- |
| Affected Java unit tests | PASS: 97 tests, zero failures/errors/skips |
| Full Java 21 Maven verification | PASS: 264 tests, 88 XML reports, zero failures/errors/skips |
| Runtime dependency trees / source identity | PASS: resolved versions inspected; 761 current source/configuration files match |
| Frontend clean installation | PASS; full audit findings remain visible |
| Lint / typecheck / production build | PASS after preserving and regenerating the duplicate generated cache |
| Vitest / fixture Playwright | PASS: 138 / 10; fixture APIs are mocked |
| Full npm audit | FAIL: four high development findings from one unpatched braces advisory |
| Production npm audit | PASS: zero findings |
| Terraform fmt/init/validate/mocked tests | PASS: 10 mocked tests; no real plan/apply |
| Base/cloud/production-like Compose | PASS: base config, 12 runtime policy tests and production guard |
| Release / cloud-tool and TLS safety | PASS: 35 / 41 tests; isolated TLS transport is not the complete business journey |
| Caddy configuration | PASS; no public certificate issuance |
| Monitoring syntax | PASS: 8 base rules; 15 cloud rules after fixing the validator mount |
| Actionlint / shell syntax | PASS: corrected workflow; 32 shell scripts |
| Source secret scan / log-redaction regression | PASS |
| Updated gateway / registry AMD64 images | PASS: two local non-root builds; no registry publication |
| Actual real-backend browser/API workflows | NOT RUN: insufficient host resources; earlier attempts remain FAIL |
| AWS / DNS / live TLS / remote workflow execution | NOT RUN in this remediation |
| OWASP Dependency-Check | **FAIL: assessment incomplete**, official NVD refresh timed out after 25 minutes and again after a bounded 20-minute retry; no fresh CVE count or passing gate credited |

The unchanged OWASP command was actually attempted twice with updates and strict TLS enabled. The first NVD refresh reached its 25-minute bound; a retry reached 20 minutes and logged an official feed-download timeout. A separate strict-TLS curl request received HTTP 200 but only 1,258,491 of4,185,659 bytes within 30 seconds, confirming incomplete transfer. No new JSON/HTML vulnerability assessment was completed. The exact seven findings above are the **last complete 2026-10-13 assessment**, with current vulnerable versions, fixed-version availability and prerequisites freshly reverified. They are not presented as a new scan result. Preserve the release block and rerun the unchanged profile on a host/network that can complete the official feed refresh; do not use the partially updated cache with updates disabled.

Only the gateway and registry image builds were repeated for the changed Cloud components. The historical 24-image and isolated database-restore results retain their original date/scope. A complete new immutable image set must be produced for the final approved release after its required gates pass.

## Git review and future commit plan

The old **37 modified / 119 untracked** snapshot is historical: 155 Phase 10 files were subsequently published in 13 commits ending at `8540d58`; the remaining unrelated `.vscode/settings.json` was excluded. The published changes cover deployment/infrastructure, secure runtime/proxy/auth, demo callbacks, backup/release tooling, verification, CI and documentation. They were reviewed without rewriting or discarding them.

This remediation keeps `master` and HEAD unchanged. The unrelated settings file's SHA256 is recorded and rechecked; no contents are printed. New tracked changes are the compatible Cloud BOM/comment and the monitoring validator mount. New report/evidence/plan files and existing security/readiness pointers complete the reviewable change set. Generated caches are preserved outside Git.

After the owner approves future Git publication, use these logical groups:

1. Compatible Cloud BOM and refreshed dependency-security review.
2. Corrected CI monitoring mount, with reproduced failure and actual validation evidence.
3. Remediation evidence, resource/readiness report and verification plan.

No commits, branches, PRs or pushes were made in this remediation.

## AWS prerequisites and disposition

No AWS deployment has occurred. A passing unchanged security gate and a complete stable real workflow are still required before requesting live deployment approval. Also supply the intended AWS account/region, scoped role or SSO access, reviewed infrastructure inventory, approved resource plan/budget, owned domain and authorized DNS method, protected GitHub environments/OIDC roles, and private runtime/JWT/backup/operator SecureStrings. Optional OAuth requires provider applications and verified callbacks. Image builds/publication must match the approved architecture and reviewed release source; actual DNS, ACME, SSM, backups and monitoring must be verified after authorization.

Fresh validator results and the attempted dependency-assessment outcomes are recorded in [remediation evidence](../deploy/remediation-evidence.json). Historical Phase 10 image/restore/runtime results remain separately dated and are not presented as fresh verification.

**Final classification: NOT READY — CRITICAL BLOCKERS REMAIN.** No compatible accessible patch for the seven Spring findings was verified, the full npm development audit still fails, and the complete real-backend journey awaits sufficient resources. The unchanged dependency gate's actual outcome is recorded in evidence; functional PASS results do not waive it.
