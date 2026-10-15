# Phase 10 Release Blocker Remediation Plan

> Execute inline using systematic debugging and verification-before-completion. The owner's explicit instruction to investigate, fix and verify authorizes execution; no planning approval, subagents, Git writes or cloud provisioning are needed.

**Goal:** Resolve compatible dependency issues, re-evaluate every remaining security finding and produce an honest release disposition.
**Architecture:** Retain Boot 3.5, Cloud 2025.0, existing authentication/payment boundaries and single-host deployment. Preserve strict vulnerability gates and distinguish local functional tests from complete deployed workflows.
**Tech stack:** Java 21, Maven, Next.js 16, ESLint 10, npm, Docker, Terraform and existing verification tools.
**Spec:** Owner's 2026-10-14 “Phase 10 Release Blocker Remediation” message.

## Constraints and review focus

- No branches, commits, pushes, AWS resources, Terraform apply or DNS mutations. Preserve unrelated `.vscode/settings.json` byte-for-byte.
- No CVE suppression, threshold change, scanner removal, incompatible forced dependency, Boot 4 upgrade or arbitrary JVM heap reduction.
- Preserve existing historical evidence; new reports must distinguish old results from freshly executed commands.
- Check actual vulnerable classes and transitive dependencies, not only source keyword absence or broad scanner CPE matches.
- A missing scan/update, failed test, stale metric or inadequate host cannot establish PASS.
- New minor/patch dependencies must pass affected and full tests with the actual Java 21 toolchain.
- Keep the frontend lint rule sets; an audit-suggested Next.js 14 downgrade is not a valid remediation.
- Current published baseline is `8540d58`; the earlier 37/119 snapshot is already published in 13 commits and must not be rewritten.

## Tasks

- [x] Inspect live Git state, prior security/readiness/evidence, runtime capacity and package manifests.
- [x] Recheck seven official Spring advisories, exact affected/fixed ranges, public Maven availability and Boot/Cloud compatibility; inspect runtime dependency tree and relevant classes/configuration.
- [x] Apply only justified compatible patches to `pom.xml`; keep Framework/Security unchanged if no accessible compatible patch exists. Run affected gateway/registry/auth tests and full Java 21 `clean verify`.
- [x] Reproduce npm full/production audits and lint chain, inspect stable vendor releases, apply a compatible genuine fix if available, otherwise document the single underlying advisory and four affected development packages without degrading lint coverage.
- [x] Run frontend clean installation, audit, lint, types, Vitest, production build and mocked Playwright; retain separately scoped results.
- [x] Run unchanged OWASP Dependency-Check using strict TLS and automatic data updates; collect all findings and seven target dispositions in fresh JSON/HTML evidence.
- [x] Validate Terraform fmt/init/validate/mocked tests, Compose policies/production guard, cloud tools/release tests, source secrets and monitoring configuration. Do not run a full stack on the insufficient host; record owner's lack of an existing larger host.
- [x] Write `docs/phase-10-remediation.md`, fresh non-secret `deploy/remediation-evidence.json`, update the security review/readiness pointer, audit changed paths and prepare a future logical commit plan. Conclude with the required readiness classification.

Checks are complete only after outputs are inspected. Dependency availability and workload reachability do not waive the unchanged CVSS 9 gate.

## Final outcome

Checkboxes record completed investigations/command attempts, not that all checks passed. Compatible Cloud2025.0.3 and the corrected CI validator pass functional checks. Maven264, frontend138, fixture-browser10 and affected97 tests pass. NVD refresh failed to complete in two bounded attempts; the last complete seven findings remain distinctly historical with current usage/availability rechecked. Four unpatched npm development findings remain; production audit is clean. Real workflows are NOT RUN because the owner has no larger host. Final status: NOT READY — CRITICAL BLOCKERS REMAIN. No Git/cloud/DNS mutations were performed beyond authorized source edits.
