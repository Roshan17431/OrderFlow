# OrderFlow infrastructure security remediation — October 29, 2026

**INFRASTRUCTURE SECURITY REMEDIATION INCOMPLETE.** The original Caddy, Kafka and node-exporter blockers have patched, tested amd64 candidates. Fresh data introduces **CVE-2026-116587 in Grafana12.4.14**, which remains unresolved. No infrastructure security approval or full-stack acceptance is claimed.

Java21, Spring Boot4.1.2 and Spring Cloud2025.1.4 remain unchanged. This assessment continues the [historical October28 report](container-security-remediation-2026-10-28.md). All work remains local and uncommitted on `master`, HEAD `a1536d32cee31d6c1ea2ac7710ebca031bdeed2d`. No commit, push, image publication, AWS operation, Terraform apply, DNS change or real-business-data mutation occurred. Existing and named persistence volumes were retained; the scratch-volume cleanup exception is recorded below.

## Blocker and regression matrix

| Component | Advisory | Previous → verified runtime | Evidence and disposition |
|---|---|---|---|
| Caddy2.11.7 | CVE-2026-78669 | Go1.26.8/x-net0.59.0 → Go1.27.3/x-net0.62.0 | Patched both affected stdlib and x/net. Final scan removed both flags. HTTPS/h2 retained. |
| Caddy2.11.7 | CVE-2026-111943 | Go1.26.8 → Go1.27.3 | Official compromised-intermediate denylist included. Actual certificate trust positive/negative and local ACME issuance checks PASS. System bundle still includes an EmSign root; root removal is **not** claimed. |
| Kafka4.3.2 | CVE-2026-111497 | Brotli1.2.0-r1 → 1.2.1-r0 | Signed Alpine patch; exact image scan flag removed. |
| Kafka4.3.2 | CVE-2026-77214 | Expat2.8.5-r0 → 2.9.0-r0 | Signed Alpine patch; exact image scan flag removed. |
| Kafka4.3.2 | CVE-2026-114820 | Temurin21.0.12.1 → Corretto21.0.13.0.11-r0 | Actual JVM/package identity checked. Both implicated Java CA fingerprints absent; legitimate controls present. Supplemental manual CA finding, outside raw Trivy totals. |
| node-exporter1.12.1 | CVE-2026-33818, CVE-2026-56860 | Go1.26.5 → Go1.27.3 | Authenticated upstream source/tag, locked compatible modules, no source-logic patch. Final scan removes flags; metrics/textfile/read-only/PID regression PASS. |
| Caddy go-jose/v4 | CVE-2026-116587 | 4.1.5 → 4.1.6 | Compatible module patch; flag removed from final exact image. |
| Caddy go-jose/v3 | CVE-2026-116587 | 3.0.5 retained; no HIGH fix | Affected parser symbols **present** in final binary. Exact reviewed proxy has admin off and no PKI/ACME-server/JWT input handler; explicit config regression PASS. Conditional absence of configured untrusted input, not a patched-library claim. Reopen before issuer/auth/JWK configuration changes. |
| Grafana go-jose/v4 | CVE-2026-116587 | 4.1.4 retained; fixed4.1.6 available | **OPEN.** Saved SSO configuration may override disabled environment defaults. No verified patched12.x artifact produced; vendor13.3 trial not selected. |

The maintained [recipes and provenance](../infrastructure/images/README.md) document authentication, builders, runtime bases, checksums, patch ownership and return-to-vendor conditions. Caddy follows the [official build model](https://caddyserver.com/docs/build). CA handling follows the [Go incident response](https://go.dev/blog/emudhra-incident) and [Java CA alert](https://www.oracle.com/security-alerts/alert-cve-2026-114820.html). The [go-jose advisory](https://github.com/go-jose/go-jose/security/advisories/GHSA-mgcc-cww5-fp2q) provides the v4 fix and confirms no v3 patch; v3 receives critical-only maintenance.

## Exact artifacts

Custom IDs below are local image configuration IDs, **not ECR/registry manifest digests**. Their registry publication status is NOT PUBLISHED. Vendor references are actual registry child manifests. Full32-image identities, OS/library package counts and database metadata are in the [inventory](../deploy/infrastructure-image-inventory-2026-10-29.json).

| Component | Previous amd64 vendor manifest | Selected local config ID or retained vendor manifest |
|---|---|---|
| caddy-amd64 | `caddy@sha256:04913b1da43339a40ad1ea87827d70a576ac5aa6cf674c6e24fdca675887533a` | `sha256:716553983668bb5c11ee1fd653814a9eba175a1879f86cab7fc5cdf7cfbbe9ce` |
| kafka-amd64 | `apache/kafka@sha256:8f325fc00143fed78eabe00b033a86747078bb06edc21345d06651f77ae44f30` | `sha256:903985f89446dd332a1a1080fd7d4ef5f29e9872da0247657d88c5f05f4db62e` |
| node-exporter-amd64 | `prom/node-exporter@sha256:da83fae85603c4e47e6c68369a7d746e2dda683dc35ea2e234b4f171e0d92798` | `sha256:5ff191eabbc68ae1f9a8fb3f5395258ddb6e2c6dc6a2e94facfe73dcb4206273` |
| grafana-amd64 | `grafana/grafana@sha256:1b59d2076a5e08e0e9929a1225f37869ee7719f4a11c004fbfcfa8b772858e89` | `grafana/grafana@sha256:1b59d2076a5e08e0e9929a1225f37869ee7719f4a11c004fbfcfa8b772858e89` |

Caddy and node-exporter binaries were rebuilt with the build stage cache disabled and matched byte-for-byte. Final Caddy binary SHA256 is `c54ede2c6e90dc8e274c15d18a7d52b7cd72805057bc97c2e5399105ba820778`; exporter is `a02c1e2a5deea399ac276c85cbeb35262e290f18a9a445448a40b90a836d8826`. Dependency/compiler caches were retained. Time-dependent signed APK updates mean whole runtime layers are not promised reproducible on future dates.

Kafka retains vendor4.3.2 JARs, launcher/configure scripts, CA entrypoint, user and storage guard. Replacing the JVM required removing incompatible CDS archives and using `-Xshare:off`; runtime heap budgets were preserved. No existing storage was reformatted.

## Comparable raw scans and all critical/high dispositions

The historical174-row result remains intact. Fresh Oct29 data rescanned the starting32 images and produced189 occurrences; comparison below uses that same complete database snapshot before and after. All24 application images remain at zero. All32 scans completed with OS and library analyzers, including complete Java/JAR metadata. A scanner exit0 means analysis completed, not security approval.

| Severity | Refreshed starting artifacts | Selected verification set |
|---|---:|---:|
| CRITICAL | 6 | 2 |
| HIGH | 53 | 29 |
| MEDIUM | 63 | 32 |
| LOW | 46 | 2 |
| UNKNOWN | 21 | 15 |
| Total | 189 | 80 |

OS DB updated `2026-10-28T19:18:03Z`, downloaded Oct29; complete Java DB updated `2026-10-27T01:08:30Z`, valid until Oct30. No ignorefile, vulnerability suppression, threshold change or analyzer removal was used. Node-exporter's vendor BusyBox runtime OS distribution is unrecognized: Go analysis completed, but an OS-package clean bill is not claimed.

The [59-row applicability matrix](../deploy/infrastructure-applicability-2026-10-29.json) covers every current CRITICAL/HIGH occurrence, including all original53. Of the original53,25 are removed by patched artifacts,26 retain exact-binary affected-symbol-absence evidence for PostgreSQL's gosu helper, and2 Grafana Tempo flags retain verified fixed-code/version-mapping evidence. Of the six added critical/high occurrences, three are fixed by Caddy/exporter patches, one PostgreSQL flag requires Windows and is absent on Linux, one Caddy v3 finding has no configured untrusted entrypoint, and one Grafana v4 finding remains open. The [Windows advisory](https://pkg.go.dev/vuln/GO-2026-6604) does not establish a Linux exploit path.

Raw remaining2CRITICAL/29HIGH are therefore27 PostgreSQL helper flags,2 Tempo mapping flags,1 conditional Caddy v3 finding and1 unresolved Grafana finding. They are all retained. Technical dispositions are for review; no blanket security-policy acceptance is invented.

[Before CSV](../deploy/infrastructure-findings-before-2026-10-29.csv), [after CSV](../deploy/infrastructure-findings-after-2026-10-29.csv), and [rejected Grafana13 CSV](../deploy/infrastructure-findings-rejected-grafana13-2026-10-29.csv) preserve the findings.

## Grafana investigation and actual failures

The supported12.4.14 dependency-only draft authenticates signed release commit `3c98af3ba0773ad12a8a54b1faec7dc62d8d6725` and the complete source tree; the annotated tag is unsigned. It changes only go-jose/v4 to4.1.6 and authenticated checksums, preserving vendor frontend/plugins. The initial build timed out after30minutes. Two subsequent linking attempts were canceled; no compiler error or OOM kill is claimed for those builds. A constrained linker measured about1.9GiB peak RSS and540MiB swap. The final draft uses2.4GiB Go soft memory and stripped production flags, which affect building only. **No image was produced; draft exact-image scanning, runtime, restart and rollback are NOT RUN.** A larger isolated build host is the next path to verify, not a promised successful build.

Official [Grafana13.3.0 source](https://github.com/grafana/grafana/blob/v13.3.0/go.mod) declares Go1.27.3/go-jose4.1.6. The complete official amd64 image `grafana/grafana@sha256:6e5d306338670f381a942f1c50d3c266bf71531e5ddd9ee4fc1a133dac5ce63d` was pulled and scanned: **181 raw findings:7CRITICAL,39HIGH,99MEDIUM,36UNKNOWN**. Its separately bundled plugins use older toolchains; a fixed main backend does not patch those binaries. These new flags remain unreviewed and the image was not selected.

In the corrected isolated runtime trial, the12.4.14 baseline passed SQLite health, dashboard/user/preferences checks, cookie login, anonymous API denial, packaged JavaScript delivery and an actual Prometheus `vector(7)` query through Grafana. The13.3 candidate was **OOMKilled=true, exit137 at the existing384MiB cloud service limit**. Candidate readiness failed; subsequent restart/rollback stages were NOT RUN. The synthetic baseline SQLite backup and owned volume remain. No real Grafana database was touched. Earlier fixture failures (candidate argument, tmpfs ownership and a Python import shadow) are preserved separately and are not classified as product failures.

## Executed verification

| Verification | Result and actual scope |
|---|---|
| Full Java21 Maven clean verify | PASS:226 unit +60 integration; zero failures/errors/skips |
| Unchanged OWASP12.2.2 gate | PASS:165 dependencies;3MEDIUM retained; threshold9 unchanged; no exceptions |
| Remaining Maven findings | JXPath1.3: CVE-2022-40159 and CVE-2022-40160, each6.5; Kafka client4.2.2: CVE-2026-41115,4.3 |
| Frontend fresh npm audits | PASS: npm audit and omit-dev both zero |
| Frontend lint/typecheck/138Vitest/build/10mockedPlaywright | Prior PASS retained; NOT RERUN this task because frontend source/dependencies were unaffected |
| Exact-image Trivy | PASS execution32/32;24 application images zero; infrastructure security approval BLOCKED |
| Caddy actual HTTPS/h2/local ACME | PASS29 checks: negative CA trust/no issuance, positive HTTP-01 issuance, wrong-root rejection, routes/auth/cookies, spoofed forwarding, internal blocks, SSE,413 and log redaction |
| Kafka vendor→candidate→vendor | PASS3 stages: cluster/node/directory identities, KRaft features, topic config/partitions, records, LZ4 batch CRCs and committed offsets3→9→12 preserved; refusal guards passed |
| node-exporter | PASS scoped metrics/textfile/read-only/PID; native Linux host root propagation and host NIC counters NOT RUN |
| Grafana13.3 vendor alternative | FAIL readiness/OOM at384MiB; image security not approved; restart/rollback NOT RUN |
| Terraform | PASS fmt/backend-disabled init/validate +17 mocked tests; no apply |
| Cloud/deployment/runtime safety policies | PASS144 tests (65cloud+50deploy+29runtime/guard), codec/persistence parser self-tests and production Compose validation |
| Log redaction | PASS final script plus clean affected-test log. Initial incremental test-class loading failed with cloud-offloaded generated files; clean recompile/test passed. |
| Monitoring configuration | PASS current pinned Prometheus base config8 rules/cloud config15 rules and dashboard checks; all-service live targets NOT RUN |
| Source secret scan and final configuration checks | PASS final144 policy tests, Compose validation, secret scan and diff check; no probable secrets or generated runtime artifacts added to Git paths |
| Redis/PostgreSQL | Unchanged exact artifacts; prior persistence/restore results retained; full rescans executed, no new runtime upgrade trial needed |
| Full actual commerce / all11 services / AWS | NOT RUN;4GiB Docker is insufficient for complete acceptance |

## Approved native codec scope

The user explicitly approved **fresh-only staging with none/gzip/Java-safe LZ4** on Oct29. The [scope record](../deploy/infrastructure-codec-scope-2026-10-29.json) binds that decision to the raw measurements. It does not authorize AWS provisioning.

Actual current Kafka4.2.2 libraries ran on arm64 and amd64 with read-only root, nonroot UID, existing heap settings and hardened noexec `/tmp`. None/gzip/LZ4 producer+consumer paths passed12 functional measurements. Snappy/Zstd producer+consumer paths failed8 measurements; native LZ4 diagnostics also failed. Reference broker seeds and persisted-batch codec/CRC checks passed. The unmodified raw matrix has status FAIL and exit2; this was not changed to PASS.

The permitted staging environment starts with new empty Kafka storage and OrderFlow's measured default-none producers. Do not import legacy records/volumes, enable Snappy/Zstd or connect producers outside this scope. There is no new application/broker codec allowlist. Revalidate before extending scope. No arbitrary legacy compressed-record compatibility is claimed, and `/tmp` protection remains intact.

## Git, deployment safety and AWS prerequisites

[Exact source review](../deploy/infrastructure-source-review-2026-10-29.json) lists every changed/new path, hashes, current Git status and comparison with the starting1226-file snapshot. Existing unrelated/user work is retained. All44 compatibility fixtures and23Flyway SQL files remain byte-identical. No application Java/TypeScript source, dependency version or AWS Terraform resource changed during this task. Fixture credentials, keys, logs, databases, caches and retained synthetic volumes remain outside Git; the Corretto `.pub` file is a public package-verification key.

Cloud Compose now requires four explicit immutable infrastructure references. [Release pin instructions](infrastructure-image-release-pins.md) describe release/SHA/account/region binding, packaging and pull-time digest/platform checks. Offline policy tests passed; real registry publication/pull checks are NOT RUN. The populated `deploy/infrastructure-images.json` is intentionally absent. Do not insert local config IDs into ECR URIs or silently fall back to vulnerable vendor images.

Before staging: finish and verify a supported Grafana artifact; review residual dispositions and derivative maintenance ownership; confirm the approved fresh Kafka scope; run native Linux exporter mount/NIC checks; freeze an approved source SHA and independently approved image manifests. The existing Mumbai `m7i.2xlarge`32GiB plan remains unchanged. A Linux build host with at least8GiB is a starting recommendation for the Grafana build, subject to measurement; it is not verified here. Full acceptance needs the planned adequately sized host, account/region `ap-south-1` confirmation, restricted tester CIDR, domain/TLS plan, budget, protected persistent secrets/keys, reviewed Terraform plan, backup/restore and rollback evidence. ECR publication, AWS provisioning and DNS each remain separate authorized steps. Full browser commerce, negative payment/refund/auth/SSE/outbox/DLT/monitoring/memory acceptance remains NOT RUN.

Raw reports, commands and failure logs: `/Users/roshan/.cache/orderflow-infra-final/2026-10-28/` (assessment continued Oct29). Portable [verification JSON](../deploy/infrastructure-verification-2026-10-29.json) records results and hashes. No failed check has been suppressed.

**Safety exception:** promtool validation containers used `--rm` while inheriting the vendor `/prometheus` VOLUME. Docker therefore automatically removed newly created anonymous scratch volumes at exit, contrary to the no-volume-deletion constraint. No existing business or named persistence-test volume was removed. The cache-only wrapper now overrides that path with tmpfs; future validation must do the same. This exception is retained in verification evidence.

**INFRASTRUCTURE SECURITY REMEDIATION INCOMPLETE**
