# Outbox backlog

## Symptoms

Outbox pending/failed gauges or oldest-event age rise, Kafka lag grows, or downstream state stops progressing.

## Investigation

Check producer service readiness, Kafka broker/topic health, error-rate logs by correlation ID, pending counts by status/age, and publisher retry metrics. Do not print payloads or edit rows. Likely causes are Kafka outage, incompatible serialization, credentials, exhausted retries, or an undersized bounded publisher.

## Recovery and verification

Restore Kafka/configuration or deploy the compatibility fix, leave original outbox rows intact, and let bounded polling resume. Verify the oldest age and pending count fall, rows become `PUBLISHED`, consumer lag drains, DLT stays stable, and duplicate delivery causes no duplicate effects. Escalate for missing rows, corrupt payloads, or payment/order disagreement.
