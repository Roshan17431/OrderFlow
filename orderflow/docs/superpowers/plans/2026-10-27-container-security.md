# Container security remediation implementation plan

> **For agentic workers:** Use systematic debugging and verification-before-completion; independent research is delegated, Docker execution is serialized. Track steps below. User explicitly prohibits Git commits/pushes and AWS operations; those override generic plan commit/handoff steps.

**Goal:** Resolve applicable container-image security blockers without changing business features, Java major version or persistent data formats.

**Architecture:** Retain the existing multi-stage Dockerfiles and Compose topology. Resolve supported vendor candidates to immutable multiarchitecture indexes, verify disposable runtime/data compatibility, then update configuration and scan the exact artifacts. Preserve baseline raw evidence and distinguish scan completion, vulnerability disposition and full-stack acceptance.

**Tech Stack:** Java 21, Boot 4.1.2, Cloud 2025.1.4, Node 22/Next standalone, Docker/Compose, Trivy 0.76.1, Terraform, PostgreSQL 16, Redis, KRaft Kafka and existing monitoring.

**Spec:** User attachment `/Users/roshan/.codex/attachments/7ae09c58-c58e-4fb2-a02e-7ad959f35a2b/Pasted text.txt`; immutable local copy under `/Users/roshan/.cache/orderflow-container-remediation/2026-10-27/request.txt`.

## Global constraints

- Keep Java 21 and the implemented Spring Boot 4 migration; unchanged OWASP threshold, no vulnerability suppressions.
- No commits, pushes, image publication, AWS account/resource operations, Terraform apply, DNS or secret uploads.
- No user-data deletion, PostgreSQL destructive migration, Redis flush, Kafka storage-format bump or cluster-ID reset.
- Docker has approximately 4 GiB: run bounded disposable components sequentially; full real acceptance remains NOT RUN.
- Preserve old evidence and existing application source; record incomplete checks honestly.

## Review focus

- Multiarchitecture tags may resolve different runtimes: inspect both child manifests and run each JVM/Node version and HTTP health.
- Removing npm must preserve traced standalone assets: real runtime HTTP request and no npm/npx/Yarn executables or global package trees.
- Vendor image updates may alter persistence: old-to-new-to-old disposable SQL/AOF/message probes; verify Kafka metadata and cluster IDs unchanged.
- Scanner success may omit Java libraries: complete metadata acquisition and report each analyzer's scope, exceptions and actual coverage.
- A fixed CVE can be replaced by new findings: compare advisory/package occurrences and unique IDs, retaining raw results and vendor prerequisites.

## Task 1: Baseline and grouping

Files: existing freeze report, image inventory and full findings CSV; local cache `baseline-grouped.json`.

- [x] Confirm HEAD `a1536d32cee31d6c1ea2ac7710ebca031bdeed2d` and initially clean `master`; preserve it.
- [x] Parse all 1,377 findings into advisory/package/installed/fixed/image/severity groups and preserve source references/applicability notes.
- [x] Enrich remaining HIGH/CRITICAL findings with official vendor applicability evidence; do not equate counts with proven exploits.

## Task 2: Java images

Modify: all eleven backend service `Dockerfile`s, FROM/build security packages only. Evidence: local `java-base-*.json`, build and runtime logs.

- [x] Evaluate official Temurin 21.0.13 availability; select verified supported Corretto 21.0.13.0.11 APK/runtime and Maven 3.9.16 for amd64/arm64 when Temurin container tags remain behind.
- [x] Reproduce old-image patch/Brotli findings; verify supported patched Alpine packages/base candidate by scan.
- [x] Pin patched builder/runtime references consistently; keep users, assets, health checks, JVM memory settings and service entrypoints.
- [x] Build all eleven backends for both architectures; inspect IDs/platforms, start each in bounded dependency-compatible probes and verify health.

## Task 3: Frontend runtime

Modify: `frontend/Dockerfile` only unless verification demonstrates another necessary change. Consumes supported Node 22 index; produces both architecture runtime images.

- [x] Trace final-stage tooling and reproduce old global npm findings; remove unused npm/npx/Corepack/Yarn files rather than moving them.
- [x] Keep build-stage tooling and production standalone/static assets, nonroot user and Node health check.
- [x] Run npm ci, both audits, lint, typecheck, full tests/build; build amd64/arm64 and verify HTTP plus absence of removed runtime tooling.

## Task 4: Infrastructure candidates and compatibility

Modify after verification: immutable references in `docker-compose.cloud.yml`, and corresponding base Compose/test fixtures if needed for consistent actual image use.

- [x] Resolve current supported vendor releases/digests for PostgreSQL, Redis, Kafka, Caddy, Prometheus, Grafana and exporters using primary sources.
- [x] Scan candidates first; execute isolated version/config/health probes, old→new→old persistent-data checks and applicable backup restore.
- [x] Kafka: use supported candidate only if documented/tested startup preserves cluster ID and existing metadata/storage format; otherwise keep a clearly documented blocker. Do not choose an archived release and describe it as supported.
- [x] Update references only for verified candidates; preserve all infrastructure/monitoring functions and security boundaries.

## Task 5: Complete image-library analysis

Evidence/cache only: isolated `java-coverage/`, new scan reports and commands.

- [x] Investigate failed metadata download with supported official registry/cache options; retry with bounded deadlines.
- [x] Scan OS packages, application JAR layers, broker bundled JARs and available Go/JS libraries, all severities/unfixed findings, no ignores.
- [x] Preserve failures and report exact unscanned scopes; scan execution alone is not a passing release policy.

## Task 6: Integrated verification

- [x] Java 21 `./mvnw -B -ntp clean verify`; fresh OWASP data/unchanged aggregate gate.
- [x] Full frontend checks and mocked fixtures; distinguish actual Docker HTTP smoke from full commerce.
- [x] Compose validation, Terraform fmt/validate/17 mocked tests, deployment/cloud/runtime policies, source secrets/log redaction, Caddy and monitoring validation.
- [x] Repeat affected persistence/backup checks against changed images using only uniquely named disposable resources; record full-stack NOT RUN.

## Task 7: Before/after evidence and review

Create: dated remediation report under `docs/`, portable evidence and complete raw-finding CSV under `deploy/`; raw logs/caches outside Git.

- [x] Inventory exact old/new configuration/index/child digests, architecture, runtime versions, raw severity totals and new/fixed/unresolved advisory IDs.
- [x] For applicable unresolved HIGH/CRITICAL findings document vendor prerequisite, supported fix availability and exact remaining blocker; no suppression.
- [x] Independent final diff/evidence review; fix substantive issues, verify Git work preserved, no commits or remote actions.
- [x] Conclude exactly one allowed container-security status based on executed evidence; do not claim full-service acceptance.

## Executed outcome — October 28

All investigation, safe changes and feasible verification steps above are complete; checked steps describe completed work, not security approval. Full comparable findings fall from 1,577 to 174. Application images (24) have zero Trivy findings, but infrastructure vendor gaps and native-codec compatibility failures remain. Maven 226 unit +60 integration, unchanged OWASP, frontend 138 tests, 10 fixture browser tests, 24 image builds, 22 backend startup probes, 129 automation tests and 17 mocked Terraform tests passed. Full commerce/AWS acceptance remains NOT RUN. See `docs/container-security-remediation-2026-10-28.md` and portable `deploy/container-*-2026-10-28.*` evidence. No commits, publication, AWS actions or user-data deletion. Final classification: **CONTAINER SECURITY REMEDIATION INCOMPLETE**.
