# Notification architecture

Notification-service consumes versioned payment and delivery events and derives trusted server-side templates. It never renders user-provided HTML. Each source event is recorded in `processed_event`, and each notification has a deterministic deduplication key, so Kafka redelivery cannot create duplicates.

In-app notifications are always persisted. Per-user preferences optionally add email and SMS `notification_delivery` rows. Destinations are validated, stored only in the notification database, masked in operational output, and never placed in logs or public delivery events. Deterministic email and SMS simulators cover success, temporary failure, and permanent failure without contacting an external provider.

The API supports paged reads, type/read filters, unread count, idempotent mark-read, read-all, preferences, and an authenticated SSE stream. Every operation derives ownership from the JWT subject.
