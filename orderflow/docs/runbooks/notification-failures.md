# Notification failures

## Symptoms

Notification failure/retry counts rise, dispatches remain pending, SSE delivery stops, or provider latency/errors increase.

## Investigation

Check provider health, bounded async executor saturation, reconciliation scheduler lease, attempt code/count, Kafka lag, and sanitized destination metadata. Never expose full email/phone destinations, message secrets, or customer addresses in logs/tickets.

## Recovery and verification

Restore provider configuration/capacity and run normal reconciliation. Verify a single logical notification per source event/channel, bounded retry counts, terminal success/failure, recovered executor queue, and SSE registry health. Escalate sustained queue saturation, prohibited-data leakage, or duplicate customer delivery.
