# ADR 0027: Event-driven operations read model

Operations-service consumes Kafka into its own eventually consistent model instead of
fan-out querying services. Transactional duplicate-event protection makes redelivery safe.
This preserves database ownership while allowing operational reporting during partial outages.
