# Notification reliability

Business-event consumption, the in-app notification, optional external-delivery rows, processed-event marker, and dispatch outbox messages share one PostgreSQL transaction. A provider outage therefore cannot roll back or lose the user's persistent notification.

The outbox publisher claims bounded batches with `FOR UPDATE SKIP LOCKED`, recovers stale claims, applies bounded exponential retry, and exposes failed rows and backlog metrics. Dispatch is consumed from `orderflow.notification-events.v1`; malformed or exhausted messages reach `orderflow.notification-events.v1.DLT` rather than disappearing.

Temporary simulator failures are retried up to the configured bound. A permanent failure transitions the delivery to `FAILED`. Result events are written through the same outbox. Correlation IDs are propagated from the source event and causation IDs point to the event that caused the derived action.
