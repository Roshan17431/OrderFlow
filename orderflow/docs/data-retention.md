# Data retention

Defaults retain processed events and completed idempotency records for 90 days,
published outbox rows and completed job history for 30 days. Cleanup selects at most
1,000 eligible rows per table and never deletes FAILED outbox, active idempotency or
business records. Retention must exceed Kafka replay requirements and be reviewed for
legal, audit and customer-data obligations before production use.
