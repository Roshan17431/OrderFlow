# Payment webhook security

The exact `POST /api/payments/webhooks/simulator` route is public because
providers do not possess customer JWTs. Public routing does not mean trusted
input: payment-service authenticates the provider message itself.

```mermaid
sequenceDiagram
    participant S as Simulator
    participant V as Signature verifier
    participant D as Webhook ledger
    participant P as Payment workflow
    S->>V: timestamp, event ID, signature, exact bytes
    V->>V: tolerance + HMAC-SHA256 + constant-time compare
    V->>D: SHA-256 payload hash
    alt same event ID and same hash
        D-->>S: 200 duplicate
    else same ID and different hash
        D-->>S: 409 replay conflict
    else new event
        D->>P: transactional monotonic update + outbox
        P-->>S: 200
    end
```

The signing input is `timestamp + "." + rawRequestBody`. Timestamps default to
a five-minute tolerance. Signature verification happens before JSON parsing.
Only the payload hash is stored; complete raw bodies, signatures, and secrets
are neither stored nor logged. Missing, malformed, stale, or invalid headers
receive a safe standard error.
