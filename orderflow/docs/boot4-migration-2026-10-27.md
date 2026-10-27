# OrderFlow Spring Boot 4 migration and security verification

Assessment: 2026-10-27. Baseline/current HEAD: `4197fbe1095663f0dbc32d2705bf95b202fb2702`, branch `master`. The initial working tree was clean. All changes remain uncommitted. No Git branches, commits, pushes, AWS resources, public deployments, DNS updates, live-data changes or destructive migrations were performed.

**MIGRATION IMPLEMENTED — FURTHER VERIFICATION REQUIRED**

The migration and unchanged CVSS 9 dependency gate pass. Full real-backend acceptance through Caddy with all 11 services is **NOT RUN** because this host has 8 GiB physical RAM and Docker reports approximately 4 GiB. This report does not approve AWS deployment or establish a live demo URL.

## Actual dependency matrix

Versions come from executed Maven runtime trees, packaged JARs and test classpaths. The imported Data BOM is recorded alongside its resolved libraries. Supporting versions use the [Boot 4.1.2 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom) and [Cloud 2025.1.4 BOM](https://repo.maven.apache.org/maven2/org/springframework/cloud/spring-cloud-dependencies/2025.1.4/spring-cloud-dependencies-2025.1.4.pom). The [Cloud compatibility table](https://spring.io/projects/spring-cloud/) supports this train.

| Component | Resolved version |
| --- | --- |
| Java | 21.0.12.1 (executed; release 21) |
| Maven Wrapper | 3.9.11 |
| Spring Boot | 4.1.2 |
| Spring Cloud BOM | 2025.1.4 |
| Spring Framework | 7.0.10 |
| Spring Security | 7.1.2 |
| Gateway Server WebFlux | 5.0.4 |
| Cloud Netflix | 5.0.3 |
| Eureka core | 2.0.6 |
| Spring Kafka | 4.1.2 |
| Kafka client | 4.2.2 |
| Kafka broker | 3.9.1 (unchanged) |
| Spring Data BOM | 2026.0.2 |
| Spring Data JPA / Redis | 4.1.2 / 4.1.2 |
| Hibernate | 7.4.13.Final |
| Flyway | 12.4.0 |
| PostgreSQL JDBC | 42.7.14 |
| Jackson application JSON | 3.1.7 |
| Jackson 2 vendor compatibility | 2.21.8 |
| Testcontainers | 2.0.5 (executed test classpaths) |
| JUnit Jupiter | 6.0.3 (executed test classpaths) |
| Spring Session | 4.1.1 |
| Lettuce | 7.5.2.RELEASE |
| Micrometer | 1.17.2 |
| Resilience4j | 2.3.0 |
| Tomcat | 11.0.27 |
| Netty | 4.2.19.Final |
| Reactor Netty | 1.3.8 |
| Kotlin runtime BOM / stdlib | 2.4.21 (compatible security override) |

All Boot runtime artifacts resolve to 4.1.2, Framework artifacts to 7.0.10 and Security artifacts to 7.1.2. Runtime trees contain no Framework 6 or Security 6 mixture. Cloud still manages an artifact named `resilience4j-spring-boot3:2.3.0`; its name does not introduce a Boot 3 runtime. The configured retry/circuit-breaker paths passed integration checks. No unsupported Resilience4j replacement was forced.

Old Tomcat 10, Netty 4.1, Testcontainers 1 and Jackson 2-as-application-mapper pins were removed. Three deliberate security overrides remain: Jackson 2.21.8 for vendor libraries, HttpClient 4.5.14 for the separate Eureka transport family, and the aligned Kotlin 2.4.21 BOM. Application JSON and the catalog serializer use Jackson 3. The Jackson 2 bridge is still a transitive Cloud/Eureka requirement; remove the override/bridge when the supported Cloud/Eureka graph stops requiring it, rerun both-direction fixtures, then confirm no runtime Jackson 2 artifacts remain. It is not the selected HTTP mapper.

## Migration changes

The root and all 14 module POMs were reviewed and changed. Shared modules: `platform-contracts`, `event-contracts`, `scheduler-support`. Services: `service-registry`, `api-gateway`, `auth-service`, `product-service`, `cart-service`, `inventory-service`, `order-service`, `payment-service`, `delivery-service`, `notification-service`, `operations-service`.

- Adopted Boot 4 MVC/WebFlux, OAuth2/security, Redis session, Kafka, Flyway and modular test starters.
- Migrated application JSON imports to `tools.jackson`; retained `com.fasterxml.jackson.annotation` annotations. Builder configuration replaces mutable mapper APIs. `spring.jackson.use-jackson2-defaults=true` preserves prior mapping defaults; existing stricter order/payment settings remain.
- Moved health and reactive error APIs to their supported Boot 4 packages. Replaced MockBean with MockitoBean, relocated MVC/WebFlux/metrics test annotations, and migrated Testcontainers PostgreSQL APIs/artifact names.
- Product catalog values retain the v1 namespace/type metadata. The Jackson 3 polymorphic validator permits catalog DTOs and the required collection/decimal types, rejects unapproved types, and uses immediate Redis cache writes to preserve synchronous behavior. No cache/cart/session flush or key migration was introduced.
- Jackson 3 exceptions are unchecked. Existing regression tests caught a cart downstream error becoming 500 instead of 502; the client now catches JacksonException alongside I/O failures. Webhook parsing catches the new exception type and preserves signature-before-parse behavior and safe provider-neutral errors.
- Spring 7 canonicalizes the 413 enum; tests assert numeric 413 and additionally preserve the public `PAYLOAD_TOO_LARGE` code and `Payload Too Large` error text.
- No business state machine, event identifier/version, topic, consumer group, message key, outbox ordering SQL, historic migration or frontend API/UI source was changed.

## Seven original Spring findings

The prior scan reported Framework 6.2.19 / Security 6.5.11 through broad component CPE matches. Relevant previous/target-line affected ranges are below; linked vendor advisories give all historical branches. All seven were absent from the executed final report with the patched runtime versions.

| CVE / official advisory | Component/condition | Relevant affected ranges | First target-line OSS fix | Installed | Final scan |
| --- | --- | --- | --- | --- | --- |
| [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) | XsltView path handling | Framework 6.2.0–6.2.19; 7.0.0–7.0.8 | 7.0.9 OSS | 7.0.10 | Absent |
| [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) | SSE view fragments | Framework 6.2.0–6.2.19; 7.0.0–7.0.8 | 7.0.9 OSS | 7.0.10 | Absent |
| [CVE-2026-47891](https://spring.io/security/cve-2026-47891/) | Aalto/JAXB memory limit | Framework 6.2.0–6.2.19; 7.0.0–7.0.8 | 7.0.9 OSS | 7.0.10 | Absent |
| [CVE-2026-47892](https://spring.io/security/cve-2026-47892/) | WebFlux functional header matching | Framework 6.2.0–6.2.19; 7.0.0–7.0.8 | 7.0.9 OSS | 7.0.10 | Absent |
| [CVE-2026-59313](https://spring.io/security/cve-2026-59313/) | Functional MVC plain-text SSE | Framework 6.2.0–6.2.19; 7.0.0–7.0.8 | 7.0.9 OSS | 7.0.10 | Absent |
| [CVE-2026-59283](https://spring.io/security/cve-2026-59283/) | Compiled SimpleEvaluationContext | Framework 6.2.0–6.2.19; 7.0.0–7.0.8 | 7.0.9 OSS | 7.0.10 | Absent |
| [CVE-2026-59270](https://spring.io/security/cve-2026-59270/) | Embedded UnboundID LDAP | Security 6.5.0–6.5.11; 7.1.0 | 7.1.1 OSS | 7.1.2 | Absent |

The prior usage review did not find qualifying XSLT view rendering, SSE view fragments/plain-text functional SSE, Aalto input parsing, compiled SimpleEvaluationContext or embedded LDAP. The gateway uses the reactive runtime rather than functional endpoints on DispatcherServlet. These prerequisite findings were not used to suppress anything; dependency upgrades resolve the version findings.

## Fresh security gate and remaining findings

Dependency-Check 12.2.2 refreshed the complete existing NVD cache using its configured official bulk feed and then assessed **166 dependency entries**. The final aggregate exited **0**, with no analysis exceptions. CVSS threshold remains **9**; scanning, update verification and TLS remain enabled. No suppression was added. Raw HTML/JSON reports are in `target/dependency-check/`; structured retained results are in [verification JSON](../deploy/boot4-migration-verification-2026-10-27.json).

The first Boot 4 scan failed on Kotlin stdlib 2.3.21 / CVE-2026-53914 (NVD 9.8). The [JetBrains/CNA advisory](https://nvd.nist.gov/vuln/detail/CVE-2026-53914) concerns unsafe build-cache metadata deserialization before 2.4.20. OrderFlow has no Kotlin compiler/build-cache path, but the unchanged gate still failed. The released stable [Kotlin 2.4.21](https://github.com/JetBrains/kotlin/releases/tag/v2.4.21) BOM now aligns stdlib/jdk7/jdk8. The full reactor and restored-schema suites were rerun after that change; the fresh gate passes. No compiler, Gradle toolchain or Spring major override was introduced.

Three medium findings remain visible, with no suppression:

| Dependency | CVE | CVSS | Disposition |
| --- | --- | --- | --- |
| Commons JXPath 1.3 | CVE-2022-40159 | 6.5 | Existing disputed record; retain managed dependency and report it. |
| Commons JXPath 1.3 | CVE-2022-40160 | 6.5 | Existing disputed record; retain managed dependency and report it. |
| Kafka clients 4.2.2 | CVE-2026-41115 | 4.3 | Broker ACL/documentation issue; see vendor disposition below. |

The JXPath [CNA record](https://nvd.nist.gov/vuln/detail/CVE-2022-40160) states the maintainers consider the original reports invalid. Apache lists 1.4.0, but its [security report](https://commons.apache.org/proper/commons-jxpath/security.html) does not identify a validated patch for these exact disputed findings. No arbitrary version pin or claimed fix was added. OrderFlow source does not accept/evaluate user JXPath expressions.

For [CVE-2026-41115](https://kafka.apache.org/community/cve-list/), Apache explains that the implemented GROUP DESCRIBE permission is correct and the documentation was corrected; its recommendation is to review group ACLs. The scanner associates the broad Kafka CPE with client 4.2.2. OrderFlow's broker remains 3.9.1, outside the reported 4.0–4.3 broker range, and consumers retain the classic protocol. No unrelated client/broker upgrade or suppression was used to hide this finding. Future broker/ACL changes must follow the vendor's permission model.

Both `npm audit` and `npm audit --omit=dev` report **0 vulnerabilities**. The earlier four lint-chain findings are absent. No lint downgrade or frontend dependency change was necessary.

## Executed verification

| Check | Result |
| --- | --- |
| Boot 3 baseline after four new fixture checks | 218 unit +50 integration; 0 failed/skipped |
| Actual-root Java 21 `./mvnw -B -ntp clean verify`, final Kotlin version | **226 unit +60 integration; 0 failures/errors/skips** |
| Restored Boot 3 schemas/data, final Kotlin version | **40 passed**, 8 suites, 0 failures/errors/skips; reruns, not additional unique tests |
| Separate old-runtime database readback | 7 Hibernate 7 writes read by Hibernate 6; operations numeric-schema check passed |
| Security 6 reads Security 7 JWT/session fixtures | Passed in separate preserved legacy-runtime processes |
| Fresh OWASP gate | PASS, 166 dependency entries, 3 medium findings retained |
| Maven runtime dependency trees / packaged versions | PASS; no Framework/Security 6 mixture |
| npm ci / lint / typecheck / production build | PASS |
| Vitest | **138 passed** |
| Playwright API fixtures | **10 passed** |
| Full / production npm audits | **0 / 0 vulnerabilities** |
| Dockerfile builds | **24 passed**, 12 applications × linux/arm64 and linux/amd64 |
| Isolated image smoke | Registry readiness + Prometheus and frontend HTTP passed |
| Compose base / production / cloud policies | PASS |
| Terraform init without backend / validate / mocked tests | PASS / PASS / **14 passed** |
| Cloud scripts / deployment scripts / effective cloud config | **65 / 36 / 12 tests passed** |
| Caddy handlers | PASS: routing, internal blocks, forwarded-header trust, correlation, SSE flushing, body limits, redaction |
| Monitoring static configuration / promtool | PASS; live targets NOT RUN |
| Source secret scan / log redaction | PASS / PASS |
| All-service real browser/API journey through Caddy | **NOT RUN — resource blocker** |
| Real external OAuth provider callback | NOT RUN |
| AWS staging / public HTTPS / live DNS | NOT RUN; not authorized |

Fresh Maven suite counts by module:

| Module | Unit | Integration |
| --- | ---: | ---: |
| api-gateway | 33 | 0 |
| auth-service | 39 | 11 |
| cart-service | 24 | 7 |
| delivery-service | 12 | 5 |
| event-contracts | 7 | 0 |
| inventory-service | 10 | 7 |
| notification-service | 12 | 2 |
| operations-service | 4 | 2 |
| order-service | 22 | 9 |
| payment-service | 26 | 12 |
| platform-contracts | 9 | 0 |
| product-service | 25 | 3 |
| scheduler-support | 2 | 2 |
| service-registry | 1 | 0 |

The service integration suites use real PostgreSQL, Redis and Kafka where designed, with downstream service stubs/event injection at existing boundaries. They preserve checks for login/refresh/revocation/roles, ownership/caching/cart concurrency, inventory reservation/commit/pickup, payment/webhook/refund idempotency and negative paths, delivery cancellation/concurrency, notification/SSE behavior, operations reports, DLTs and outbox recovery. They are not a simultaneous cross-service acceptance run and do not establish exactly-once delivery.

Initial verification failures were investigated rather than bypassed: removed APIs/starter boundaries, duplicate YAML Jackson blocks, test mappers needing production-compatible defaults, and unchecked JSON exceptions. An initially over-strong byte-identical Java session assertion was replaced by field-level assertions only after Security 6 actually read the Security 7 rewrite; Java collection serialization order is not the session contract. Ignored stale Next-generated type caches were preserved and regenerated. A first frontend Docker invocation used the wrong context; the corrected `frontend` context passed on both architectures. Final results above supersede those failed attempts, whose logs remain retained.

## Data compatibility and rollback limits

[Fixture inventory and hashes](boot4-compatibility-fixtures.md) documents actual old-format producers. All 25 event schemas and monetary/timestamp/null/nested fields are represented. Old and new event/ApiError/product/cart codecs are tested in both directions. Old typed catalog bytes are read by Jackson 3 and new typed bytes by Jackson 2. A real Redis test loads an old durable cart hash and verifies its new mutation is rollback-readable. Refresh-token string hashes, revocation state, RSA keys, issuer, kid, roles and cookie/security rules remain unchanged.

All **23 historical Flyway SQL files are byte-identical** to the baseline. Eight isolated restores retain old Flyway 11 history/checksums. Flyway 12 validates them without repair/new migration and Hibernate 7 uses `ddl-auto=validate`; representative existing business rows are loaded before test cleanup. A separate temporary Postgres instance verifies Hibernate 7 updates to user/category/inventory/order/payment/delivery/notification records are read by the original Hibernate 6.6.53 runtime; the operations SQL numeric schema is checked separately. No live database was accessed.

These are representative checks, not certification of arbitrary unseen production data, every possible serialized session attribute or a mixed-version rolling cluster. Actual external OAuth redirects/PKCE and a full image rollback under live workload still need staging acceptance.

Before promotion, retain the previous immutable image digests, signing keys/configuration and backup for disaster recovery. Stop new work, drain outboxes/in-flight workflows, then roll applications back coherently if required. Preserve current databases and Redis state; do not automatically restore an old backup during code rollback. A Boot 3 rollback reintroduces its known Spring CVEs and cannot be treated as a security-approved release. No rollout or rollback was performed here.

## Resources and AWS prerequisites

The current machine cannot support the full stack reliably. Use an isolated Linux/Docker verification host with at least **16 GiB available to Docker** (the existing cloud-style caps total roughly 12.5 GiB before headroom), preferably a host with 24 GiB or more physical RAM. Measure peaks/restarts during the full run; retain existing service heap settings until that evidence justifies tuning. No repeated all-service OOM attempt or heap reduction was used.

Before AWS deployment approval: run the complete real browser/API journey through Caddy with all services, actual DB/Redis/Kafka, signed payment callbacks, financial/inventory invariants, delivery, notifications/SSE, operations, outbox/DLT and restart/recovery checks. Verify live scrape targets/alerts and load/resource use, real OAuth callbacks and rollback. Complete the existing infrastructure/secrets/domain/TLS/cost approval checklist and target-image vulnerability policy. Container builds are not a scan of every base-image OS package or broker image. Publish immutable images only after separate authorization. Terraform apply, DNS and cloud deployment remain unexecuted.

## Commands and evidence

Maven root: `/Users/roshan/Documents/OrderFlow/orderflow`; frontend root: its `frontend/` directory. Java 21 was explicitly selected for Maven. Full logs, XML copies, old/new runtime compatibility probes and image IDs are retained under `/Users/roshan/.cache/orderflow-boot4/2026-10-27/`.

Core commands executed (full arguments/exit codes/timings in verification JSON):

```sh
./mvnw -B -ntp clean verify
./mvnw -B -ntp dependency:tree -Dscope=runtime -DoutputFile=target/boot4-runtime-tree.txt
./mvnw -B -ntp -N -Psecurity-scan org.owasp:dependency-check-maven:12.2.2:update-only -DdataDirectory=<evidence-cache> -DnvdValidForHours=0
./mvnw -B -ntp -Psecurity-scan org.owasp:dependency-check-maven:12.2.2:aggregate -DdataDirectory=<evidence-cache>
./mvnw -B -ntp -pl auth-service,product-service,inventory-service,order-service,payment-service,delivery-service,notification-service,operations-service -am failsafe:integration-test failsafe:verify -Dorderflow.verifyBoot3Schema=true -Dit.test=AuthenticationFlowIT,ProductCatalogIT,InventoryFlowIT,OrderFlowIT,PaymentFlowIT,DeliveryFlowIT,NotificationFlowIT,OperationsFlowIT -Dfailsafe.failIfNoSpecifiedTests=false
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
npm audit --json
npm audit --omit=dev --json
npm run test:e2e
docker compose config --quiet
bash scripts/validate-production-config.sh
bash scripts/validate-monitoring.sh
bash scripts/security-scan.sh
bash scripts/verify-log-redaction.sh
terraform init -backend=false -input=false -lockfile=readonly
terraform validate -no-color
terraform test -no-color
```

The dependency scan used the original profile's aggregate goal directly across the whole reactor after installing the shared modules, avoiding a redundant lifecycle/test run. It assessed all 166 resolved dependency entries; it was not a root-only empty scan. The standard release profile remains unchanged.

The fresh-context independent review found no Critical or Important source defects. Version-comment and generated-declaration housekeeping was corrected. Work remained in the existing checkout and verification evidence was retained because commits were prohibited. No deferred review findings remain.

## Changed files

The list below records the working tree, including fixture files. Historical assessment documents retain their dated findings with a link to this current report. No original user changes were discarded.

- `README.md`
- `orderflow/api-gateway/pom.xml`
- `orderflow/api-gateway/src/main/java/com/orderflow/gateway/error/GatewayErrorWebExceptionHandler.java`
- `orderflow/api-gateway/src/main/java/com/orderflow/gateway/security/GatewayAuthenticationEntryPoint.java`
- `orderflow/api-gateway/src/main/java/com/orderflow/gateway/security/GatewaySecurityErrorWriter.java`
- `orderflow/api-gateway/src/main/resources/application.yml`
- `orderflow/api-gateway/src/test/java/com/orderflow/gateway/ApiGatewayApplicationTest.java`
- `orderflow/api-gateway/src/test/java/com/orderflow/gateway/ratelimit/GatewayRateLimitWebFilterTest.java`
- `orderflow/api-gateway/src/test/java/com/orderflow/gateway/security/GatewayRevocationWebFilterTest.java`
- `orderflow/api-gateway/src/test/java/com/orderflow/gateway/security/GatewaySecurityWebTest.java`
- `orderflow/auth-service/pom.xml`
- `orderflow/auth-service/src/main/java/com/orderflow/auth/config/KafkaHealthIndicator.java`
- `orderflow/auth-service/src/main/java/com/orderflow/auth/security/ApiSecurityErrorWriter.java`
- `orderflow/auth-service/src/main/resources/application.yml`
- `orderflow/auth-service/src/test/java/com/orderflow/auth/AuthenticationFlowIT.java`
- `orderflow/auth-service/src/test/java/com/orderflow/auth/InfrastructureIT.java`
- `orderflow/auth-service/src/test/java/com/orderflow/auth/error/GlobalExceptionHandlerTest.java`
- `orderflow/auth-service/src/test/java/com/orderflow/auth/platform/PlatformControllerTest.java`
- `orderflow/auth-service/src/test/java/com/orderflow/auth/security/jwt/Boot3JwtCompatibilityTest.java`
- `orderflow/auth-service/src/test/java/com/orderflow/auth/security/oauth2/Boot3OAuthSessionCompatibilityTest.java`
- `orderflow/auth-service/src/test/resources/compat/boot3-schema.sql`
- `orderflow/auth-service/src/test/resources/compat/boot3/access-token.jwt`
- `orderflow/auth-service/src/test/resources/compat/boot3/oauth2-authorization-request.base64`
- `orderflow/auth-service/src/test/resources/compat/boot3/public-jwk.json`
- `orderflow/auth-service/src/test/resources/compat/boot4/access-token.jwt`
- `orderflow/auth-service/src/test/resources/compat/boot4/oauth2-authorization-request.base64`
- `orderflow/auth-service/src/test/resources/compat/boot4/public-jwk.json`
- `orderflow/cart-service/pom.xml`
- `orderflow/cart-service/src/main/java/com/orderflow/cart/client/ProductClient.java`
- `orderflow/cart-service/src/main/java/com/orderflow/cart/persistence/CartRedisRepository.java`
- `orderflow/cart-service/src/main/java/com/orderflow/cart/security/ApiSecurityErrorWriter.java`
- `orderflow/cart-service/src/main/resources/application.yml`
- `orderflow/cart-service/src/test/java/com/orderflow/cart/CartFlowIT.java`
- `orderflow/cart-service/src/test/java/com/orderflow/cart/CartStateUnavailableIT.java`
- `orderflow/cart-service/src/test/java/com/orderflow/cart/dto/ProductResolutionContractTest.java`
- `orderflow/cart-service/src/test/java/com/orderflow/cart/persistence/StoredCartItemBoot3CompatibilityTest.java`
- `orderflow/cart-service/src/test/java/com/orderflow/cart/security/CartSecurityMvcTest.java`
- `orderflow/cart-service/src/test/resources/compat/boot3/StoredCartItem.json`
- `orderflow/delivery-service/pom.xml`
- `orderflow/delivery-service/src/main/java/com/orderflow/delivery/config/KafkaConfiguration.java`
- `orderflow/delivery-service/src/main/java/com/orderflow/delivery/messaging/OrderEventListener.java`
- `orderflow/delivery-service/src/main/java/com/orderflow/delivery/outbox/OutboxStore.java`
- `orderflow/delivery-service/src/main/java/com/orderflow/delivery/security/ApiSecurityErrorWriter.java`
- `orderflow/delivery-service/src/main/resources/application.yml`
- `orderflow/delivery-service/src/test/java/com/orderflow/delivery/DeliveryFlowIT.java`
- `orderflow/delivery-service/src/test/java/com/orderflow/delivery/security/DeliverySecurityMvcTest.java`
- `orderflow/delivery-service/src/test/java/com/orderflow/delivery/service/AgentAddressPrivacyTest.java`
- `orderflow/delivery-service/src/test/resources/compat/boot3-schema.sql`
- `orderflow/deploy/boot4-migration-verification-2026-10-27.json`
- `orderflow/docs/boot4-compatibility-fixtures.md`
- `orderflow/docs/boot4-migration-2026-10-27.md`
- `orderflow/docs/cloud-release-checklist.md`
- `orderflow/docs/cloud-security-review.md`
- `orderflow/docs/phase-10-readiness.md`
- `orderflow/docs/superpowers/plans/2026-10-27-spring-boot4.md`
- `orderflow/event-contracts/pom.xml`
- `orderflow/event-contracts/src/test/java/com/orderflow/events/EventSerializationCompatibilityTest.java`
- `orderflow/event-contracts/src/test/resources/compat/boot3/DELIVERY_ASSIGNED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/DELIVERY_CANCELLED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/DELIVERY_CANCEL_REQUESTED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/DELIVERY_DELIVERED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/DELIVERY_FAILED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/DELIVERY_OUT_FOR_DELIVERY.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/DELIVERY_PICKED_UP.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/DELIVERY_REQUESTED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/INVENTORY_COMMITTED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/INVENTORY_COMMIT_REQUESTED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/INVENTORY_RELEASED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/INVENTORY_RESERVATION_FAILED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/INVENTORY_RESERVED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/NOTIFICATION_DELIVERED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/NOTIFICATION_DISPATCH_REQUESTED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/NOTIFICATION_FAILED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/ORDER_CANCEL_REQUESTED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/ORDER_CREATED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/PAYMENT_CANCELLED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/PAYMENT_CANCEL_REQUESTED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/PAYMENT_FAILED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/PAYMENT_REFUNDED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/PAYMENT_REFUND_FAILED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/PAYMENT_REQUESTED.json`
- `orderflow/event-contracts/src/test/resources/compat/boot3/PAYMENT_SUCCEEDED.json`
- `orderflow/inventory-service/pom.xml`
- `orderflow/inventory-service/src/main/java/com/orderflow/inventory/config/KafkaConfiguration.java`
- `orderflow/inventory-service/src/main/java/com/orderflow/inventory/messaging/DeliveryEventListener.java`
- `orderflow/inventory-service/src/main/java/com/orderflow/inventory/messaging/OrderEventListener.java`
- `orderflow/inventory-service/src/main/java/com/orderflow/inventory/outbox/OutboxStore.java`
- `orderflow/inventory-service/src/main/java/com/orderflow/inventory/security/ApiSecurityErrorWriter.java`
- `orderflow/inventory-service/src/main/resources/application.yml`
- `orderflow/inventory-service/src/test/java/com/orderflow/inventory/InventoryFlowIT.java`
- `orderflow/inventory-service/src/test/java/com/orderflow/inventory/client/ProductManagementAccessContractTest.java`
- `orderflow/inventory-service/src/test/java/com/orderflow/inventory/security/InventorySecurityMvcTest.java`
- `orderflow/inventory-service/src/test/resources/compat/boot3-schema.sql`
- `orderflow/notification-service/pom.xml`
- `orderflow/notification-service/src/main/java/com/orderflow/notification/config/KafkaConfiguration.java`
- `orderflow/notification-service/src/main/java/com/orderflow/notification/messaging/DispatchEventListener.java`
- `orderflow/notification-service/src/main/java/com/orderflow/notification/messaging/DomainEventListener.java`
- `orderflow/notification-service/src/main/java/com/orderflow/notification/outbox/OutboxStore.java`
- `orderflow/notification-service/src/main/java/com/orderflow/notification/security/ApiSecurityErrorWriter.java`
- `orderflow/notification-service/src/main/resources/application.yml`
- `orderflow/notification-service/src/test/java/com/orderflow/notification/NotificationFlowIT.java`
- `orderflow/notification-service/src/test/java/com/orderflow/notification/security/NotificationSecurityMvcTest.java`
- `orderflow/notification-service/src/test/resources/compat/boot3-schema.sql`
- `orderflow/operations-service/pom.xml`
- `orderflow/operations-service/src/main/java/com/orderflow/operations/config/KafkaConfiguration.java`
- `orderflow/operations-service/src/main/java/com/orderflow/operations/messaging/OperationalEventListener.java`
- `orderflow/operations-service/src/main/java/com/orderflow/operations/security/ApiSecurityErrorWriter.java`
- `orderflow/operations-service/src/main/resources/application.yml`
- `orderflow/operations-service/src/test/java/com/orderflow/operations/OperationsFlowIT.java`
- `orderflow/operations-service/src/test/java/com/orderflow/operations/security/OperationsSecurityMvcTest.java`
- `orderflow/operations-service/src/test/resources/compat/boot3-schema.sql`
- `orderflow/order-service/pom.xml`
- `orderflow/order-service/src/main/java/com/orderflow/order/config/KafkaConfiguration.java`
- `orderflow/order-service/src/main/java/com/orderflow/order/messaging/DeliveryEventListener.java`
- `orderflow/order-service/src/main/java/com/orderflow/order/messaging/InventoryEventListener.java`
- `orderflow/order-service/src/main/java/com/orderflow/order/messaging/PaymentEventListener.java`
- `orderflow/order-service/src/main/java/com/orderflow/order/outbox/OutboxStore.java`
- `orderflow/order-service/src/main/java/com/orderflow/order/security/ApiSecurityErrorWriter.java`
- `orderflow/order-service/src/main/resources/application.yml`
- `orderflow/order-service/src/test/java/com/orderflow/order/OrderFlowIT.java`
- `orderflow/order-service/src/test/java/com/orderflow/order/client/CartContractCompatibilityTest.java`
- `orderflow/order-service/src/test/java/com/orderflow/order/messaging/DeliveryEventListenerTest.java`
- `orderflow/order-service/src/test/java/com/orderflow/order/outbox/OutboxOrderingIT.java`
- `orderflow/order-service/src/test/java/com/orderflow/order/security/OrderSecurityMvcTest.java`
- `orderflow/order-service/src/test/resources/compat/boot3-schema.sql`
- `orderflow/payment-service/pom.xml`
- `orderflow/payment-service/src/main/java/com/orderflow/payment/config/KafkaConfiguration.java`
- `orderflow/payment-service/src/main/java/com/orderflow/payment/demo/DemoCompletionWorker.java`
- `orderflow/payment-service/src/main/java/com/orderflow/payment/messaging/OrderEventListener.java`
- `orderflow/payment-service/src/main/java/com/orderflow/payment/outbox/OutboxStore.java`
- `orderflow/payment-service/src/main/java/com/orderflow/payment/provider/SimulatorPaymentProvider.java`
- `orderflow/payment-service/src/main/java/com/orderflow/payment/security/ApiSecurityErrorWriter.java`
- `orderflow/payment-service/src/main/resources/application.yml`
- `orderflow/payment-service/src/test/java/com/orderflow/payment/PaymentFlowIT.java`
- `orderflow/payment-service/src/test/java/com/orderflow/payment/demo/DemoCompletionWorkerTest.java`
- `orderflow/payment-service/src/test/java/com/orderflow/payment/provider/SimulatorPaymentProviderTest.java`
- `orderflow/payment-service/src/test/java/com/orderflow/payment/security/PaymentSecurityMvcTest.java`
- `orderflow/payment-service/src/test/java/com/orderflow/payment/webhook/SimulatorWebhookVerifierTest.java`
- `orderflow/payment-service/src/test/resources/compat/boot3-schema.sql`
- `orderflow/platform-contracts/pom.xml`
- `orderflow/platform-contracts/src/test/java/com/orderflow/platform/error/ApiErrorBoot3CompatibilityTest.java`
- `orderflow/platform-contracts/src/test/java/com/orderflow/platform/error/ApiErrorTest.java`
- `orderflow/platform-contracts/src/test/resources/compat/boot3/ApiError.json`
- `orderflow/pom.xml`
- `orderflow/product-service/pom.xml`
- `orderflow/product-service/src/main/java/com/orderflow/product/cache/CatalogCacheConfiguration.java`
- `orderflow/product-service/src/main/java/com/orderflow/product/security/ApiSecurityErrorWriter.java`
- `orderflow/product-service/src/main/resources/application.yml`
- `orderflow/product-service/src/test/java/com/orderflow/product/ProductCatalogIT.java`
- `orderflow/product-service/src/test/java/com/orderflow/product/cache/CatalogSerializationCompatibilityTest.java`
- `orderflow/product-service/src/test/java/com/orderflow/product/dto/ProductDetailResponseBoot3CompatibilityTest.java`
- `orderflow/product-service/src/test/java/com/orderflow/product/security/ProductSecurityMvcTest.java`
- `orderflow/product-service/src/test/resources/compat/boot3-schema.sql`
- `orderflow/product-service/src/test/resources/compat/boot3/ProductDetailResponse.json`
- `orderflow/product-service/src/test/resources/compat/boot3/catalog-categories.json`
- `orderflow/product-service/src/test/resources/compat/boot3/catalog-product.json`
- `orderflow/scheduler-support/pom.xml`
- `orderflow/scheduler-support/src/test/java/com/orderflow/scheduler/ConservativeRetentionIT.java`
- `orderflow/scheduler-support/src/test/java/com/orderflow/scheduler/PostgresJobSchedulerIT.java`
- `orderflow/service-registry/pom.xml`

**MIGRATION IMPLEMENTED — FURTHER VERIFICATION REQUIRED**
