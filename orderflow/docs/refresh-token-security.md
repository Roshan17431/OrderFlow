# Refresh-token security

Refresh tokens are unstructured, base64url values generated from at least 256
bits of `SecureRandom` entropy. The raw value exists only at the client and in
request memory. Redis keys and metadata contain its SHA-256 hash, never the raw
credential.

Each Redis hash records session/family/user IDs, issued/expiry times, rotation
and revocation state, replacement hash, hashed user-agent/IP values, and token
version. Token, family, and user indexes have bounded TTLs.

Refresh executes a Redis Lua script. It checks and marks the old session, creates
one replacement, updates family/user indexes, and applies TTLs atomically.
Concurrent use cannot create two successful replacements. Presentation of an
already rotated token marks every live family member revoked and emits a reuse
audit event.

Logout revokes the presented refresh session and stores the access `jti` only
for its remaining lifetime. Logout-all revokes user-indexed sessions, increments
the PostgreSQL token version, publishes that version to Redis, and blacklists the
calling access token. Gateway and auth service reject revoked or stale tokens
and fail closed when Redis cannot verify state.

The cookie is HttpOnly, scoped to `/api/auth`, and uses configurable SameSite and
Secure attributes. Local HTTP uses `Secure=false`; production HTTPS must use
`Secure=true`. See [ADR 0004](adr/0004-opaque-refresh-token-rotation.md) and
[ADR 0005](adr/0005-cookie-refresh-and-csrf.md).
