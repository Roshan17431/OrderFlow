# ADR 0025: PostgreSQL scheduler leases

Plain `@Scheduled` runs on every replica. We atomically lease a named row in the same
PostgreSQL database as the owned work. Leases expire after crashes; every attempt is
recorded. Jobs still assume duplicates and remain idempotent. Redis was rejected as the
primary lock because the business transaction and lock would not share a consistency boundary.
