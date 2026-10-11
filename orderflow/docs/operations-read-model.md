# Operations read model

Operations consumers use independent groups per business topic. `processed_event`
and metric/incident updates commit together, so Kafka redelivery cannot double-count.
Dimensions are limited to currency, provider, category and channel; identifiers and
PII are never metric dimensions. The model is eventually consistent and observational.
