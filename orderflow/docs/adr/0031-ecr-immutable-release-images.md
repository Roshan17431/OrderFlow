# ADR 0031: ECR and immutable release images

Status: Accepted.

Define twelve project-specific ECR repositories, one per backend service and
frontend, with immutable release tags, push scanning and encryption. Build the
configured EC2 architecture and deploy manifests pinned to exact image digests.
Separate short-lived GitHub OIDC publishing credentials from deployment access.

Never deploy a floating `latest` tag. Lifecycle expires only untagged images;
tagged current/previous releases remain available for rollback. This avoids a
retention rule deleting a currently running release, at the cost of retained
storage growth. Safe manual pruning checks all referenced digests and requires
operator authorization; scanning findings still need triage before release.
