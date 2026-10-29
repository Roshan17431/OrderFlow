# Final infrastructure security remediation plan

> Use systematic debugging, test-driven changes and verification-before-completion. Independent read-only Kafka/exporter research runs in parallel; root serializes Docker work. The user's explicit request authorizes execution. No commits, pushes, publication, AWS operations, DNS, volume deletion or real-data changes.

**Goal:** Resolve or conclusively disposition the remaining infrastructure and codec blockers with exact-artifact evidence.

**Architecture:** Prefer current official vendor artifacts. Where they remain vulnerable, document build provenance/maintenance first, then test minimal derivatives built from pinned official sources/toolchains and vendor runtime bases. Keep storage identity/features and existing security boundaries. Do not reopen application migration/features or change Mumbai resources.

**Tech:** Go 1.27.3, Caddy 2.11.7 and compatible x/net patch, Java21, Kafka4.3.2, node-exporter, Docker/Compose, Trivy/OWASP and existing test harnesses.

**Spec:** `/Users/roshan/.codex/attachments/08b0c182-0e7c-414b-8179-7da22eee8f41/Pasted text.txt` (local evidence copy under `/Users/roshan/.cache/orderflow-infra-final/2026-10-28/request.txt`).

## Global constraints

- Preserve HEAD/master, all existing 25 modified tracked and nine untracked files; record starting hashes.
- No scanner suppressions/threshold changes or unsigned opaque binaries.
- Keep hardened application noexec `/tmp`, Kafka metadata3.9-IV0/static quorum/cluster/topic/group/offset state, and retained real storage untouched.
- Docker ~4GiB: isolated synthetic components only; no complete11-service run.
- Full real commerce/AWS acceptance remains NOT RUN until actually executed on a suitable host.
- No changes to AWS Mumbai resources or paid publication; registry digests unavailable until separately authorized publication remain explicit.

## Review focus

- A patched Go stdlib does not replace vulnerable x/net; verify both embedded versions and scan exact executable/image.
- CA package version alone does not prove distrust; check actual bad fingerprints, valid controls, positive/negative TLS behavior and Go denylist.
- Kafka JVM replacement can invalidate CDS archives; retain vendor launch behavior and verify changed runtime without formatting storage.
- Consumers may replay compression differing from producer defaults; test actual current Kafka client, both producer and consumer paths, with noexec temp filesystems.
- Custom images cannot silently fall back to unpatched vendor references; deployment configuration must require reviewed immutable identities while preserving rollback manifests.

## Task1 — Caddy first

Files: `infrastructure/images/caddy/{Dockerfile,main.go,go.mod,go.sum}` if vendor artifact remains unfixed; `infrastructure/images/README.md` provenance; affected proxy integration tests.

- [x] Snapshot source/Git and inspect prior exact image, executable metadata, CA store and h1/h2 configuration.
- [x] Verify official current vendor release and fixed Go/x/net versions; vendor Caddy2.11.7 image unchanged, supported custom-build pattern documented upstream.
- [x] Document supported pinned source/builder/runtime/maintenance and exact module checksums before adoption.
- [x] Reproduce both advisory version failures, build minimal patched image, inspect provenance and prove repeat binary hash.
- [x] Run TLS/h2 reverse proxy, local certificate validation/ACME behavior, SSE/auth/forwarded headers/internal blocks/body bound/redaction and exact-image scan.

## Task2 — Kafka then exporter

Files: scoped `infrastructure/images/kafka/` and `infrastructure/images/node-exporter/` recipes only if vendor fixes unavailable; existing storage guard retained.

- [x] Review research, document custom provenance and ownership before build; prefer official fixed artifacts.
- [x] Verify actual JVM/native packages, CA contents and vendor launch/CDS compatibility.
- [x] Run isolated old/new/old storage cycles with records, cluster/features, topic configuration and committed consumer-group offsets preserved; no reset/format.
- [x] Verify node-exporter fixed toolchain/sources and metrics/textfile/host collectors; retain explicit Desktop host-root propagation gap where applicable.
- [x] Scan exact artifacts and compare all previous53C/H flags, with no filters/suppression.

## Task3 — Codec and deployment integration

Files: isolated synthetic codec probe/test sources under `infrastructure/cloud/tests/`; cloud configuration/templates/validation only if custom artifacts adopted.

- [x] Inspect actual producer/consumer defaults and test current libraries with synthetic none/gzip/lz4/snappy/zstd records under cloud filesystem/security settings.
- [x] If native codec is needed, implement narrowly scoped supported library loading and regress it; otherwise document/test approved initial-stage codec constraints without legacy compatibility claims.
- [x] Adopt only verified custom references through explicit immutable configuration, preserving existing vendor rollback artifact behavior and Mumbai resource plan.

## Task4 — Final verification/evidence

- [x] Full Java21 Maven verify and unchanged OWASP gate; frontend checks/audits if affected.
- [x] Fresh comparable exact-image scans with complete Java DB for32images; retain old174 and all53C/H dispositions.
- [x] Compose/cloud/deploy policies, Terraform17offline tests, proxy/monitoring/persistence/codec tests and source secret checks.
- [x] Final evidence-backed matrix, exact changed files/Git preservation, no remote operations and honest remaining AWS prerequisites.
- [x] Conclude exactly one requested infrastructure-security status on executed evidence.

## Recorded outcome — October29

Original Caddy/Kafka/exporter remediation and scoped regressions completed. Final
Caddy/exporter binaries repeated byte-identically. Codec matrix raw FAIL retained;
user formally approved new-empty-storage none/gzip/Java-safe-LZ4 staging only.
All53 prior C/H rows rechecked; fresh baseline59C/H fully dispositioned. Comparable
raw189→80;24application images zero. Full Maven286 and OWASP unchanged gate PASS.
No security approval: newly reported Grafana go-jose HIGH remains unresolved.
The12.x source draft produced no verified image; official13.3 whole-image review
and384MiB runtime trial failed readiness (OOM killed). Native Linux exporter
mount/NIC checks and full-stack AWS acceptance remain NOT RUN. No commit, push,
publication or AWS/DNS operation occurred. Existing/named data volumes remained;
see the final report for Docker automatic anonymous scratch-volume cleanup during
promtool validation and its corrected tmpfs helper.

Follow-up explicitly remains open:
- [ ] Produce and scan a supported complete Grafana artifact and pass its actual
      persistence/restart/rollback tests on an adequately provisioned environment.
- [ ] Verify native Linux host-root propagation and host NIC counters on staging.

The checked tasks above record completed investigation/executed checks and their
explicit limitations; they do not assert all release requirements passed. Final
report: docs/infrastructure-security-remediation-2026-10-29.md.
