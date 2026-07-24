# ADR 0001: Maven monorepo

## Status

Accepted for Phase 1.

## Context

OrderFlow begins with several independently executable services plus a small
shared contract. Phase 1 changes frequently touch contracts, applications,
container definitions, tests, and documentation together. The team needs one
reproducible Java 21 build with aligned Spring versions.

## Decision

Use a Maven multi-module monorepo. A parent/aggregator imports the Spring Cloud
BOM, inherits Spring Boot dependency management, configures Java and test
lifecycle policy, and registers only the Phase 1 modules.

## Benefits

- One `./mvnw clean verify` validates compatible versions and contracts.
- Atomic changes can update a contract and every consumer.
- CI, Compose, scripts, and documentation live beside the applications.
- `-pl <module> -am` still supports focused builds and Docker layers.

## Drawbacks

- The reactor grows as services are added.
- Shared repository access and CI can couple otherwise independent teams.
- A careless shared-module change can increase the blast radius.

## Consequences

Modules retain independent executable artifacts and may later gain selective CI.
Shared code is limited to stable transport contracts, not business logic.
Repository extraction remains possible if team ownership or release cadence
requires it.
