# ADR 0029: Operational data retention

Processed events and idempotency results must outlive realistic Kafka replay and client retry
windows. Only old completed data and PUBLISHED outbox rows are eligible. Indexed, bounded
deletes reduce locks; FAILED outbox rows remain for investigation. Production periods require
business, compliance and recovery review.
