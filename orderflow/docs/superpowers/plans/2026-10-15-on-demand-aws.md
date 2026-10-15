# On-demand AWS Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. The user explicitly requests implementation now; no branches, commits, pushes, AWS writes, paid resources or DNS changes.

**Goal:** Safely operate the existing dedicated demo host for bounded sessions while retaining its data.
**Architecture:** Reuse Phase 10 release manifests, secret fetching, Compose, health checks and backups. Add guarded AWS CLI helpers, boot service and an independent systemd countdown. Persist a resume pointer only after successful release verification. Docker must wait for the retained EBS mount.
**Tech Stack:** Python standard library, Bash, Ubuntu 24.04 systemd, Terraform AWS provider, existing Docker Compose.
**Spec:** User attachment `e9af828f-baa6-4ca1-b9b8-09b5bfd62b1c/Pasted text.txt`.

## Global constraints
- Boot 3.5, unchanged vulnerability threshold, no suppressions.
- Local implementation and tests only; no actual start, stop, poweroff, apply, DNS or cloud deployment.
- Default eight hours; explicit extension; no daily startup schedule.
- No signing-key regeneration, data reset, volume removal or unrelated edits.

## Review focus
- Wrong account/instance/tags or shutdown behavior must fail before mutation.
- Repeated starts and timer restarts must not extend the deadline.
- Failed releases or unmounted EBS must never initialize a fresh data directory.
- Secret/SSM output must not appear in operational logs.
- Timer must remain independent of application failure; short disposable timer test must never call poweroff.

### Task 1: Operational controls and timer
- [x] Add failing tests for identity guards, confirmations, idempotent start, bounded waits, timer expiry/extension and graceful-stop failure.
- [x] Implement `scripts/cloud/on_demand.py`, host controller, start/stop wrappers and systemd units.
- [x] Run tests with fake AWS/host command boundaries; exercise a real short elapsed countdown against a harmless recorder and validate systemd syntax in an isolated Ubuntu container.

### Task 2: Integrate existing infrastructure and releases
- [x] Add Terraform tests for explicit STOP behavior, dedicated tags, bounded runtime, boot assets and read-only host verification IAM.
- [x] Integrate bootstrap asset installation, Docker mount dependency and healthy-release resume pointer.
- [x] Validate Terraform, bootstrap/Compose/release safety tests and unit syntax.

### Task 3: Approval and verification preparation
- [x] Recheck official dependency advisories and registry patch availability; rerun audits and feasible full local checks.
- [x] Write costs using current official proposed-region rates, operations guide, restricted staging/persistence plan and exact approval proposal.
- [x] Record actual PASS/FAIL/NOT RUN evidence and final Git diff. No Git or AWS writes.

## Execution record

- Base HEAD:a1b8d09, clean initial tree. User requested implementation in the existing checkout with no Git/cloud writes; no branch/worktree/commit/push created.
- Ruling: execute the supplied detailed scope directly; separate approval remains mandatory for billable AWS/DNS actions.
- Ruling: native macOS has no systemd boot environment; elapsed timer logic uses a harmless recorder and actual Ubuntu unit syntax validation is separate. Real boot/shutdown/persistence remain NOT RUN.
- RED→GREEN: explicit target/confirmation guards, unchanged running-session deadline, bounded polling, timer extension, stale expiry race, boot-ready state, stale readiness and concurrent deployment selection.
- Independent review found and verified fixes for timer-extension race, Docker automatic replay of failed releases, deployment/readiness lock races and macOS temp-path canonicalization in a test. Final review reports no important findings; on-demand23/deployment36 tests pass.
- Final evidence/report preserve security failures and all live NOT RUN statuses. No AWS CLI/credentials configured; no AWS resources, apply, DNS or remote Actions execution.
