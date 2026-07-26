# ADR 0003: RSA JWT signing

Status: accepted

OrderFlow uses RS256 instead of a shared HMAC secret. Only auth-service receives
private signing material; gateways and future resource services receive
cacheable public JWK data. A compromised verifier therefore cannot mint tokens,
and key distribution does not copy a signing secret across services.

All decoders pin RS256, issuer, timestamps, and required claims. Local ephemeral
RSA-2048 keys are explicit and warned; production requires mounted PEM keys.
Automated overlapping key rotation is deferred, so production operators must
coordinate `kid`, JWK publication, and token TTL during rotation.
