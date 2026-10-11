# ADR 0026: Service-owned reconciliation

Each service reconciles its own database and reuses its domain transactions. A central
orchestrator would require cross-service knowledge and failure coupling. Operations-service
therefore observes Kafka and never modifies or directly reads business databases.
