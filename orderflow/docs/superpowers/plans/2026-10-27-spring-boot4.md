# Spring Boot 4 Migration Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement task by task.

**Goal:** Migrate the existing reactor to Boot 4.1.2 / Cloud 2025.1.4 on Java 21, preserving wire formats and persistent state.
**Architecture:** BOM-aligned modular starters, Jackson 3 application JSON, explicit compatibility regression tests. No business feature changes.
**Spec:** /Users/roshan/.codex/attachments/d1a7f1c7-e1f1-4b8b-a0e7-b6f391f23380/Pasted text.txt

## Global Constraints
No commits, pushes, AWS resources, public deployments, DNS changes, live data changes, volume removal or historical Flyway SQL edits. Preserve CVSS 9 security gate. Full-stack acceptance is NOT RUN on this 4 GiB Docker VM.

## Tasks
1. [x] Preserve synthetic Boot 3 JSON fixtures (all 25 events, API error, cart, catalog) and migration checksums; execute baseline tests. New fixture tests must first fail for missing fixtures, then pass with Boot 3 generated fixtures.
2. [x] Migrate root dependency management and shared modules/registry. Compile and run focused shared tests before service changes.
3. [x] Migrate auth/gateway, product/cart: supported starters, health/test packages, Jackson APIs, Redis compatibility and security tests. Preserve keys, namespaces, rules and token claims.
4. [x] Migrate workflow services, persistence, Kafka, scheduler, notifications and operations. Run real sequential PostgreSQL/Redis/Kafka integration tests; investigate each failure without weakening assertions.
5. [x] Run actual-root clean verify, fresh OWASP gate, resolved trees, frontend checks, infrastructure validation, image builds and source/log scans. Verify synthetic existing schema and rollback JSON; label unexecuted cross-version/runtime boundaries.
6. [x] Document exact results, versions, files, blockers and rollback limits. One fresh-context final review, fix material findings with regression tests, then report without Git publication.

## Review Focus
Old/new monetary and timestamp encodings; restricted polymorphic catalog values; old persisted cart and outbox fields; JWT/filter/CSRF ordering; Hibernate schema/locking; Kafka ordering/recovery. Full-stack and external OAuth remain acceptance gates if not executed.

## Execution ledger
- Baseline HEAD 4197fbe1095663f0dbc32d2705bf95b202fb2702, master, clean.
- Ruling: Use the existing checkout without creating a branch or committing: the user explicitly authorizes implementation here and prohibits Git publication. Changes remain uncommitted for review.
- Ruling: Keep execution records and evidence; no commit exists to recover them from.
- Pre-flight interfaces: shared contract fixture formats feed all service compatibility checks; root BOM/starter changes feed all service contexts; unchanged SQL checksums feed database and rollback gates.

- Task 1: complete — actual-root Boot 3 clean verify: {'unit': {'tests': 218, 'failures': 0, 'errors': 0, 'skipped': 0}, 'integration': {'tests': 50, 'failures': 0, 'errors': 0, 'skipped': 0}}. Missing event fixture assertion failed before capture; 28 plain JSON and 2 polymorphic catalog fixtures captured with Boot 3, plus Security 6 JWT/public key. No private signing keys persisted.
- Task 2: started — BOM/starter alignment, no individual Spring Framework/Security pins; retain Jackson 2.21.8 for residual vendor dependencies.

- Task 2: complete — shared modules/registry verify passed on Boot 4 (boot4-shared-retry.log). Testcontainers 2 PostgreSQL APIs migrated; Jackson 3 event writer is readable by Jackson 2 for all event types.
- Task 3: gateway/auth/product suites passed in boot4-core-final; cart exposed unchecked Jackson 3 exception escaping its old IOException boundary (existing downstreamHeadersAndFailuresAreHandledSafelyWithoutBreakingReads RED: expected 502, actual 500). Catch JacksonException alongside I/O errors, preserving existing fallback.
- Compatibility: catalog native Jackson 3 serializer passes old typed fixtures in both directions and rejects unapproved types. Keep synchronous cache writes explicitly because Redis 4 defaults can be asynchronous.
- Frontend: npm ci/lint/typecheck/test/build/audit/audit --omit=dev/10 Playwright fixtures all passed. Initial checks found ignored stale .next duplicate types; preserved generated cache in node_modules/.cache, rebuilt. No frontend source changes.
- Fresh NVD update passed in 196 seconds; no scanner gate changes.

- Task 3: complete — core verification passed per-module; cart-fixed real Redis tests green after unchecked JSON exception correction. Security 6 JWT fixture accepted under fixed clock, issuer/expiry negatives preserved. OAuth2AuthorizationRequest serialVersionUID remains 620; actual JDK session deserialization on Security 7 passed (oauth-session-compatibility.log), added byte-preserving regression check.
- Task 4: in progress — all six workflow services migrated to Jackson 3, relocated health/test APIs, MockitoBean, Testcontainers 2 and modular Kafka/Flyway starters. Preserve Kafka broker 3.9.1.
- Existing-data verification: 8 genuine Flyway 11 schema dumps include old history, JSON/precision probes and synthetic business rows; explicit second run restores these and reads original business entities before existing test cleanup. Default run remains fresh DB coverage.

- Task 4: complete — focused inventory/order/notification/operations verification passed; payment/delivery final verification passed. Preserved signed-webhook behavior by configuring test mappers with the same Jackson 2 compatibility defaults as application JSON (missing primitive retryable retains false). Added explicit false assertion. No business assertions relaxed.
- API status contract: Spring 7 canonicalizes HTTP 413 to CONTENT_TOO_LARGE. Assert numeric 413 while additionally pinning existing public error text "Payload Too Large" and code PAYLOAD_TOO_LARGE.
- Task 5: started — actual-root Boot 4 clean verify. Eight original Flyway migration sets unchanged, checked against baseline SHA-256 manifest. No user DB/Redis touched.
- Infrastructure checks passed: base and production Compose, cloud effective configuration tests, monitoring static assets/promtool (live targets NOT RUN), isolated Caddy handler/spoof/SSE/redaction checks, Terraform validation + 14 mocked tests. No terraform apply or AWS calls.

- Verification finding: byte-identical JDK session serialization was an over-strong test assumption (collection order differs). Confirmed Security 6 actually reads the Security 7 rewrite in a separate legacy process, then changed regression assertions to all supported session fields and stable serialVersionUID. No application behavior changed. See oauth-session-rollback.log.

- Full actual-root clean verify passed: 226 unit +60 integration, zero failures/errors/skips (boot4-clean-verify-final.log), prior to the new Kotlin fix.
- Fresh aggregate Dependency-Check scanned 166 dependency entries with no analysis exceptions: all seven Spring findings absent; new Kotlin stdlib 2.3.21 / CVE-2026-53914 (NVD 9.8) fails unchanged gate. Vendor/CNA fix is 2.4.20; apply released stable Kotlin BOM 2.4.21 via Boot property, aligned supporting runtime modules. Compiler/cache prerequisite is absent in Java-only OrderFlow, but no suppression or threshold change. New full verification and scan required.
- Final fresh-context review: no Critical/Important findings, declined-to-judge list empty. Two housekeeping findings (old version comment/generated Next declaration drift) addressed within requested documentation and scope hygiene. No behavior changes from review.

- Task 5: complete — final Kotlin 2.4.21 actual-root clean verify 226 unit +60 integration; restored Boot 3 schemas 40 tests across 8 suites; fresh OWASP aggregate PASS (166 dependency entries, 3 medium retained), all seven Spring CVEs absent. Runtime trees confirm no Framework/Security 6 mix. Both npm audits 0; frontend138/fixtures10. All 24 ARM64/AMD64 image builds inspected; registry readiness/Prometheus and frontend HTTP smoke passed. Terraform14 mocked tests and configuration/security/log checks passed.
- Reverse database compatibility: isolated Hibernate7 writes read successfully by original Hibernate6.6.53 in 7 entity services; operations unchanged numeric schema checked separately. Original Flyway SQL23 files unchanged. Actual legacy Security6 readers accept the new JWT/session fixtures.
- Task 6: complete — final report, exact versions, raw-result metadata, fixture hashes and full changed-file inventory written. Independent review found no Critical/Important issues, no declined behaviors, no deferred findings. Full real-stack acceptance and external provider callbacks NOT RUN; no larger host available. Final classification: MIGRATION IMPLEMENTED — FURTHER VERIFICATION REQUIRED. No commits/pushes/cloud/DNS changes.
