# ADR 0038: Digest releases and schema-aware application rollback

Status: Accepted for prepared cloud deployment.

Each release includes all twelve ECR digests, full source SHA, target platform and append-only SQL hashes with an explicit backward compatibility decision. A host filesystem lock serializes updates. Only fully verified releases replace healthy metadata; the candidate schema is recorded before application startup so partially applied migrations remain visible after failure.

Rollback reuses an older source/configuration artifact and digest manifest, checks the newest attempted schema for unchanged existing SQL and reviewed compatibility, takes a fresh backup and reruns readiness/customer verification. It never restores a database or deletes volumes/business records. Database recovery remains a separately authorized emergency procedure.

Consequences: single-host updates may cause downtime. Unknown/destructive migration compatibility blocks image rollback and requires a forward fix. SQL restore proofs and local mocks do not establish deployed customer verification.
