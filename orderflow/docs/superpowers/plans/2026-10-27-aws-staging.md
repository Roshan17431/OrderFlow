# AWS Mumbai Staging Preparation Plan

> **For agentic workers:** Execute these already authorized preparation tasks in this session. No deployment or Git publication is authorized.

**Goal:** Prepare a restricted first real-backend acceptance run in ap-south-1, with explicit evidence and approval inputs.

**Architecture:** Retain the existing single-host Compose deployment, encrypted retained data volume, SSM administration, Caddy, immutable image digests and dedicated cloud-demo on-demand safety controls. Use a 32 GiB x86 host for acceptance and restrict application HTTPS to approved operator IPv4 CIDRs.

**Tech Stack:** Existing Boot 4.1.2 / Cloud 2025.1.4, Docker Compose, Terraform, Caddy, Python, Playwright.

**Spec:** User request, “OrderFlow — Post-Migration AWS Staging Readiness”, 2026-10-27.

## Global Constraints

- Do not add business features or repeat the migration.
- Do not commit, push, publish images, apply Terraform, launch EC2, create paid resources, change DNS or deploy publicly.
- Report PASS, FAIL or NOT RUN; recorded migration evidence is not a newly executed check.
- Do not start the full stack in the local 4 GiB Docker VM.

## Review Focus

- Restricted HTTPS ingress must not accidentally block certificate HTTP challenges or expose internal ports.
- Invalid CIDRs and wrong account/region/architecture must fail before deployment.
- Restart/rollback must retain database, Redis AOF, Kafka logs, signing keys and unrelated business records.
- Browser fixtures must use actual services and keep credentials out of artifacts.
- Costs continue for retained disks, images, backups and Elastic IP while compute is stopped.

## Tasks

- [x] Record the starting 106 tracked / 54 untracked migration files and inspect their scope; preserve all changes.
- [x] Audit secrets/generated files and inspect deployment, persistence and acceptance tooling.
- [x] Add tested restricted HTTPS ingress and a Mumbai approval template, preserving existing production controls.
- [x] Retrieve current official Mumbai prices and enumerate exact proposed resources and residual charges.
- [x] Verify isolated persistence/restart behavior on arm64 and amd64 auth; label full application/host restart NOT RUN.
- [x] Prepare browser/API negative scenarios, monitoring, memory and data-preserving rollback procedure.
- [x] Run Terraform mocks/validation, Compose policies, deploy/on-demand automation, Caddy, monitoring and security checks.
- [x] Write the readiness report, exact approval checklist and structured results. No Git commits/pushes or AWS mutations.

## Completion evidence

- 17 Terraform mocked plans; 65 cloud +36 deploy +12 runtime policy tests.
- Fresh frontend: 138 Vitest, 10 fixture browser tests, lint/typecheck/build, both npm audits zero findings. Modified real-browser assertions statically verified; real execution NOT RUN.
- Two isolated four-service restart probes (arm64 and amd64 auth); eight-database backup/restore; Ubuntu systemd, Caddy handler integration, application/cloud monitoring syntax and secrets/redaction passed.
- All 160 starting migration file hashes unchanged; all 24 retained application image identities/platforms inspected.
- Actual AWS plan, public TLS/ingress, real commerce/negative cases, memory peaks and live rollback remain NOT RUN. OS/host vulnerability checks are explicit future gates.
- Readiness report and runbook contain the exact approvals and limitations; classification READY FOR AWS STAGING APPROVAL.
