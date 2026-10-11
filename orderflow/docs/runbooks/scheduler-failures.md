# Scheduler failures

## Symptoms

Scheduled-job last-success age or failure count rises; expiration, reconciliation, assignment, retention, or report catch-up stops.

## Investigation

Inspect `scheduled_job_lock`/run history read-only, database time, instance IDs, lock duration, last error code, and service readiness. Determine whether a healthy instance owns an unexpired lease before restarting anything.

## Recovery and verification

Fix database/configuration, start one healthy instance, let an abandoned lease expire, then allow bounded catch-up. Verify one winner under concurrent instances, skipped/locked observations for others, no overlapping effects, successful last-run metadata, and falling backlog. Escalate clock skew, repeated lease theft, duplicate payments/assignments, or destructive retention uncertainty.
