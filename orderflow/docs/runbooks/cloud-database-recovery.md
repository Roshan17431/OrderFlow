# Cloud database recovery runbook

Database replacement is an emergency action requiring explicit approval. The repository intentionally provides only isolated restore automation, with no live restore command.

1. Record the incident, affected service owners, current release/schema digests, approved recovery point and expected record loss. Preserve storage and backups.
2. Agree a maintenance window and isolate application writes/schedulers/event processing without deleting containers' data or Kafka logs.
3. Fetch encrypted backups and checksum metadata from the private recovery destination using scoped IAM. Retrieve the matching protected encryption key.
4. Run `verify-backup.sh`, then `restore-to-test.sh --isolated-test` as documented in [backup and restore](../cloud-backup-restore.md). Verify all eight owner schemas, SQL connectivity, Flyway checksums/history and representative record/count consistency.
5. Measure the exercise duration and inspect application startup/migration validation in a separate approved recovery stack. The script's SQL proof does not establish application startup or cross-service consistency.
6. Ask the owner to approve a concrete live replacement plan, exact target databases and accepted data loss. Protect a fresh backup/snapshot of current data first.
7. An authorized database administrator performs the reviewed live cutover. Maintain service database ownership and rebuild roles from protected configuration; do not grant shared cross-service access.
8. Reconcile outbox/processed events/Kafka offsets and order/inventory/payment/delivery state before resuming traffic. Do not blindly replay or rewind events.
9. Verify actual customer flows, sessions, CSRF, SSE, reporting, backlog/DLT and public TLS. Resume writes only after owner acceptance and record measured RPO/RTO.

Never point a restore test at a live database, pipe an old dump into the running source container, or delete a volume to make a restore succeed.
