# ADR 0004: Opaque refresh-token rotation

Status: accepted

Refresh tokens are opaque because clients need no embedded authority and server
state is required for logout, rotation, reuse detection, and session inventory.
Only SHA-256 hashes are stored, limiting credential disclosure if Redis state is
read.

Every successful refresh atomically rotates the credential. A second use of the
old value signals possible theft and revokes the family. A Lua script prevents
concurrent requests from producing two successful replacements. Redis keys and
indexes have the refresh lifetime as TTL.

This design makes Redis part of the protected-request security boundary.
Availability failures fail closed rather than silently authorizing. Public
health and credential-establishment routes remain diagnosable.
