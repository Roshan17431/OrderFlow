# Cloud disaster recovery

The single EC2 host is a portfolio deployment with no high availability. An instance/AZ/storage outage can stop all application services. Preserve the incident's release manifests, SSM invocation IDs, schema-release metadata and encrypted backups before changing anything.

Recover service-only failures using bounded health checks and a restart of the affected Compose service. Reverting images requires the rollback compatibility gate; never restore a database as part of image rollback. Use [cloud rollback](runbooks/cloud-rollback.md).

For host failure, inspect EC2/SSM status and encrypted EBS attachment state. Reuse preserved data only after checking storage integrity and filesystem ownership. Reconstruct approved infrastructure from reviewed Terraform state/configuration, retrieve the exact immutable release and persistent SSM key files, and run the deployment preflight. Provisioning replacement resources and DNS changes require explicit authorization and budget review.

For database damage, isolate writes and Kafka/scheduler processing through an approved maintenance window. Copy encrypted backups and their checksums into a separate recovery environment. Run the isolated restore proof first, identify the desired recovery point and document expected lost records. Obtain explicit authorization before any live database replacement. Follow [database recovery](runbooks/cloud-database-recovery.md).

Service databases, Kafka offsets/outbox records, Redis sessions/carts and inventory/payment/delivery state must be reconciled together. Restoring older PostgreSQL data while replaying newer Kafka records can create inconsistent business state; do not casually rewind consumer offsets or delete broker logs. JWT signing keys must remain stable; key loss or deliberate rotation changes token/session recovery behavior. Redis recovery can require users to sign in again and can lose carts.

After recovery, verify database migrations and ownership, Eureka registrations, readiness, public TLS, auth refresh/CSRF, catalog, cart/order/payment/delivery, SSE, operations backlog and DLT metrics. Use authorized synthetic accounts and explain any data loss. Record measured incident duration and restore time only after an actual exercise. Current proposed daily backup RPO is up to 24 hours plus failed backup delay; RTO is not measured and no availability guarantee is made.
