# Real-time notifications

`GET /api/notifications/stream` creates an authenticated `text/event-stream` connection scoped to the JWT subject. After the database transaction commits, notification-service submits fan-out to a bounded Spring Async executor. The executor size and queue capacity are configuration properties; it does not use an unbounded common pool.

The SSE registry is partitioned by user ID. Completion, timeout, and send failure remove dead emitters. A send failure is isolated from event consumption and cannot undo the stored notification. Clients must reconnect and use `GET /api/notifications` as the durable source of truth; SSE only reduces delivery latency.
