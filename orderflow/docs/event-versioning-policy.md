# Event versioning policy

Kafka topics ending in `.v1` carry immutable version-1 contracts. `metadata.eventType`, `metadata.eventVersion`, aggregate identity, timestamps, correlation/causation identifiers, and all existing field meanings are stable. Producers serialize UUIDs as strings, instants as ISO-8601 values, and money as JSON decimal numbers without converting through floating point.

Backward-compatible version-1 changes are limited to adding a genuinely optional field with safe default behavior or adding a new event type whose consumers explicitly tolerate it. Adding an enum value is compatible only when every consumer has a tested unknown-value behavior. Removing, renaming, narrowing, making a field required, changing units, changing precision, or changing semantics is breaking and requires `eventVersion = 2`, a new Java contract, consumer migration, and—when routing or retention differs—a versioned topic.

Consumers must validate the supported version before applying business effects, copy incoming collections defensively, ignore unknown additive JSON properties, and persist `eventId` in `processed_event` before acknowledging side effects. Unsupported versions go through bounded retry/DLT handling; they are never guessed. Duplicate delivery is expected and must remain harmless. Exactly-once delivery is not claimed.

`EventSerializationCompatibilityTest` round-trips every published event type and verifies property names, version metadata, UUID/Instant representation, decimal precision, defensive copies, and additive-field tolerance. Any intentional v1 contract change must update producer, consumer, fixtures, documentation, and this suite in one change.
