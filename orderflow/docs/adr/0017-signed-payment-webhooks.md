# ADR 0017: Signed payment webhooks

## Status

Accepted

## Decision

Provider webhooks do not use customer JWTs. The exact simulator endpoint is
publicly routable but authenticates messages with HMAC-SHA256 over the supplied
timestamp and exact raw body. It validates a bounded timestamp tolerance and
compares signatures in constant time before parsing JSON.

The service stores provider/event ID and a SHA-256 payload hash, not the body.
Same ID/same hash is an idempotent 200. Same ID/different hash is a replay
conflict. Business update, webhook ledger, and outbox insertion are one
transaction.

## Consequences

Gateway JWT bypass is limited to one route. Secrets, signatures, and raw
payloads must never be logged. Clock synchronization and secret rotation are
operational requirements for a real provider.
