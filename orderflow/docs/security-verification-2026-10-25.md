# OrderFlow release-security verification — 2026-10-25

Baseline: `75d6b163883a5321a124ce2b81d0e14e3f3cd3a8`. Checkout initially clean. During the security assessment, dependency manifests and this report/evidence were uncommitted; no features, branches, commits, pushes, AWS operations or DNS changes were added.

**Publication update:** After reviewing these results, the owner authorized splitting and publishing the changes to `main`. The publication consists of three logical commits: backend dependency patches, frontend dependency patches, and this verification report/evidence. `main` is created from the existing `master` history; the assessment baseline and results below remain unchanged. Publication does not waive the security gate or authorize deployment.

**SECURITY GATE STILL BLOCKED.** The refreshed OWASP assessment completed and exited **1 because the unchanged CVSS >=9 gate detects the same seven distinct Spring CVEs**. This is a completed current assessment, rather than the historical feed timeouts. Frontend full and production audits now pass with zero findings.

## Dependency changes

| Dependency / management | Before | After | Reason |
| --- | --- | --- | --- |
| Jackson BOM | 2.21.4 | 2.21.8 | Same-line patch; aligns Jackson modules and fixes CVE-2026-54515. |
| Log4j BOM | 2.24.3 | 2.25.5 | Stable 2.x security release; aligns API and bridges, including CVE-2026-49844. |
| PostgreSQL JDBC | 42.7.11 | 42.7.14 | Same-line patch; fixes CVE-2026-54291 and includes later vendor fixes. |
| Apache HttpClient 4 | 4.5.3 | 4.5.14 | Same-line patch; fixes CVE-2020-13956 in Eureka's separate 4.x transport. |
| Next.js | 16.4.0 | 16.4.3 | Stable patch addressing the three current Next audit advisories. |
| @next/eslint-plugin-next | 16.4.1 | 16.4.3 | Matching stable tooling patch. |
| fast-uri (through AJV) | 3.1.8 | 3.1.9 | Compatible with AJV's existing ^3.0.1 range; removes four URI advisory matches. |

Boot **3.5.16**, Framework **6.2.19**, Security **6.5.11**, Cloud **2025.0.3**, Gateway **4.3.5** and Reactor Netty **1.2.18** remain. Maven Central metadata confirms these are the latest publicly available versions of their respective compatible lines. Boot's BOM supplies Framework/Security; no partial Framework or incompatible Security override was made. Spring's [compatibility table](https://spring.io/projects/spring-cloud/) maps Cloud 2025.0.x to Boot 3.5.x and marks that Cloud train end-of-life. Framework 6.2.20 and Security 6.5.12 artifact POM requests both returned HTTP404 from Central. No user Maven settings or enterprise repository entitlement is configured.

The Jackson and Log4j versions use the parent BOM properties, rather than independently mixing modules. HttpClient 4 is managed separately from the existing patched HttpClient 5. Existing Tomcat, Netty, FreeMarker and HttpClient 5/Core pins remain. The npm lockfile also records npm's generated optional WASM dependency relocation/resolution; no application code or lint rules changed.

Vendor references: [Jackson advisory](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-5jmj-h7xm-6q6v), [Log4j security](https://logging.apache.org/security.html#CVE-2026-49844), [pgJDBC security](https://jdbc.postgresql.org/security/), [HttpClient 4.5.14](https://hc.apache.org/httpcomponents-client-4.5.x/current/httpclient/project-info.html).

## Seven threshold findings

The scanner broadly associates the six Framework CVEs with `spring-core-6.2.19.jar`; the feature-specific modules are distinguished below. CVE-2026-59270 matches both Security core and OAuth2 resource-server 6.5.11; that is one distinct CVE. Scores below are the actual scanner's CVSS3 values, which differ from the Spring advisory severity labels.

| Official advisory | Scanner CVSS3 | Affected feature / current dependency line | Affected compatible range | Compatible fixed release | Reviewed application prerequisites / disposition |
| --- | --- | --- | --- | --- | --- |
| [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) | 9.8 | spring-webmvc / XsltView; Framework 6.2.19 | 6.2.0–6.2.19 | 6.2.20, enterprise only | No XSLT view/resolver or implicit XSLT rendering configured. Unpatched; scanner finding retained. |
| [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) | 9.8 | MVC/WebFlux SSE view fragments; Framework 6.2.19 | 6.2.0–6.2.19 | 6.2.20, enterprise only | No FragmentsRendering use; notifications emit owned structured JSON. Unpatched; scanner finding retained. |
| [CVE-2026-47891](https://spring.io/security/cve-2026-47891/) | 9.8 | spring-web / Jaxb2XmlDecoder; Framework 6.2.19 | 6.2.0–6.2.19 | 6.2.20, enterprise only | Required Aalto XML processor absent from runtime trees. Unpatched; scanner finding retained. |
| [CVE-2026-47892](https://spring.io/security/cve-2026-47892/) | 9.8 | WebFlux functional endpoints through DispatcherServlet; Framework 6.2.19 | 6.2.0–6.2.19 | 6.2.20, enterprise only | No functional RouterFunction endpoints; gateway uses reactive Netty. Unpatched; scanner finding retained. |
| [CVE-2026-59313](https://spring.io/security/cve-2026-59313/) | 9.8 | MVC functional ServerResponse.sse(); Framework 6.2.19 | 6.2.0–6.2.19 | 6.2.20, enterprise only | Notifications use SseEmitter with structured JSON, not functional text SSE. Unpatched; scanner finding retained. |
| [CVE-2026-59283](https://spring.io/security/cve-2026-59283/) | 9.1 | spring-expression / compiled SimpleEvaluationContext; Framework 6.2.19 | 6.2.0–6.2.19 | 6.2.20, enterprise only | No application SimpleEvaluationContext or compiler-enabling configuration found. Unpatched; scanner finding retained. |
| [CVE-2026-59270](https://spring.io/security/cve-2026-59270/) | 9.1 | spring-security-ldap / UnboundIdContainer; Security 6.5.11 | 6.5.0–6.5.11 | 6.5.12, enterprise only | LDAP module, UnboundID dependency and embedded LDAP configuration absent. Unpatched; scanner finding retained. |

All seven official vendor advisories were retrieved again on 2026-10-25. Prerequisite checks use the current application sources and freshly resolved runtime dependency trees. These observations reduce the known feature exposure; they neither patch the libraries nor authorize a suppression. There are no new suppressions, threshold changes, scanner removals, disabled automatic updates, TLS bypasses or Boot 4 migrations.

Compatible vendor enterprise patches remain the available remediation path, requiring valid entitlement and full-set regression testing. Future compatible public patches can be reassessed if published. No commercial entitlement was assumed or purchased, and no support-policy exception was applied.

## Frontend findings

The initial full and production audits each exited1: **two vulnerable package entries** (Next critical, fast-uri moderate), representing seven advisories:

- Next <=16.4.0: [GHSA-2464-fhr4-w9xw](https://github.com/vercel/next.js/security/advisories/GHSA-2464-fhr4-w9xw) (image optimization RCE), [CVE-2026-94482 / GHSA-p3m5-59mp-jv5r](https://github.com/vercel/next.js/security/advisories/GHSA-p3m5-59mp-jv5r) (Server Actions DoS), and [GHSA-673q-mjm6-r5xr](https://github.com/vercel/next.js/security/advisories/GHSA-673q-mjm6-r5xr) (`use cache` typed-array RCE). Vendor first fixed version16.4.1; installed16.4.3. This application has no `next/image`, `use server` or `use cache` application usage found; it is still patched. Installed sharp is0.35.5. The two RCE GHSA API records do not assign a Next CVE identifier; none is invented here.
- fast-uri3.1.8: [CVE-2026-110306](https://github.com/advisories/GHSA-w74x-v6f9-j3xg), [CVE-2026-110095](https://github.com/advisories/GHSA-xhq7-xmqj-769h), [CVE-2026-110180](https://github.com/advisories/GHSA-wjr9-j795-jxcr), [CVE-2026-110725](https://github.com/advisories/GHSA-333x-w4r6-6crp). Fixed on this line in3.1.9; upgraded through the existing transitive range without overrides or a4.x migration. It enters through @hookform/resolvers → AJV; no application imports of fast-uri were found.

The former four high development package entries were all derived from [CVE-2026-93687 / GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm). GitHub withdrew it on **2026-10-23T12:53:02Z** as not practically exploitable. Both fresh baseline audits omitted it before any dependency changes. Braces remains3.0.3; this is an upstream advisory withdrawal, not a local suppression or a claimed braces patch.

After the compatible changes, actual **`npm audit` and `npm audit --omit=dev` both exit0 with zero info/low/moderate/high/critical findings**. No forced lint downgrade or audit bypass was used.

## Fresh scanner and test execution

A prior NVD database was copied to an isolated run directory solely as an update seed. An explicit update-only run forced checking (`nvdValidForHours=0`), retained strict TLS, downloaded the official annual/modified feeds, refreshed CISA and completed successfully in246.66seconds. The final aggregate keeps automatic updates enabled and correctly reuses the data refreshed minutes earlier. The JSON report records NVD cache/API last checked **2026-10-25T12:47:04Z**, last modified **2026-10-25T08:00:09-04**.

Commands run from a clean source copy outside the cloud-synced Documents folder:

```sh
./mvnw -B -ntp clean verify
./mvnw -B -ntp -DskipTests dependency:tree -Dscope=runtime -DoutputFile=target/security-runtime-tree.txt
./mvnw -B -ntp -N -Psecurity-scan org.owasp:dependency-check-maven:12.2.2:update-only -DdataDirectory=<isolated-data> -DnvdValidForHours=0
./mvnw -B -ntp -DskipTests verify -Psecurity-scan -DdataDirectory=<isolated-data>
npm ci
npm audit
npm audit --omit=dev
npm run lint
npm run test
npm run build
npm run typecheck
npm run test:e2e
```

| Check | Actual final result |
| --- | --- |
| Java21 full clean verify after backend updates | **PASS264** tests;0 failures/errors/skips;88 XML reports;190.55seconds. |
| Baseline Java21 clean verify | PASS264;0 failures/errors/skips;362.51seconds, including initial test-image downloads. |
| Runtime dependency trees / aggregate SBOM | Regenerated; patched versions resolved. |
| NVD forced data refresh | **PASS**, exit0. |
| Final OWASP Dependency-Check12.2.2 aggregate | **FAIL**, exit1, completed JSON/HTML;7 distinct CVEs at CVSS>=9. |
| npm ci after final lockfile | **PASS**, exit0. |
| Full / production npm audits after updates | **PASS / PASS**, both exit0, zero findings. |
| Frontend lint / typecheck / production build | **PASS / PASS / PASS**. |
| Vitest | **PASS138**,15 test files. |
| Playwright fixtures | **PASS10**; mocked APIs, not a complete real-backend commerce journey. |
| Source probable-secret scan | **PASS**, no probable committed production secrets. |
| Git diff whitespace check | PASS. |
| Full real-stack browser/API acceptance | **NOT RUN in this security task**; historical resource blocker is not cleared by these tests. |
| AWS / DNS / remote CI / new release image publication | **NOT RUN**. |

The first aggregate emitted a missing.NET runtime analyzer warning while still completing the vulnerability gate. Installed an isolated official Microsoft.NET8.0.32 runtime outside Git and reran with all existing analyzer settings. The final Assembly Analyzer completed and the final report has no analysis exceptions. The scanner warns that Sonatype OSS Index requires an unavailable authentication token and is not evaluated; NVD/CISA and existing available analyzers completed. No analyzer was disabled or failure hidden. Reactor support artifacts were installed locally before the final aggregate to resolve its internal-module dependencies.

All **1,150 tracked source/configuration files** in the tested copy match the final checkout byte-for-byte (including updated manifests). No stale generated `.next` cache was reused. Playwright regenerated a Next type import in the isolated copy; it was restored to match the checkout and typecheck passed again. Existing developer caches/data were preserved. Java tests used temporary Testcontainers resources rather than starting the full application stack.

## Remaining findings below the release threshold

Initial scan: **34 unique CVEs on13 dependency entries**. Final scan: **28 unique CVEs on9 dependency entries**, including7 critical,10 high,10 medium and1 low as labeled by this scanner. The four compatible backend changes removed six unique findings: CVE-2026-54515, CVE-2026-49844, CVE-2026-34479, CVE-2026-34477, CVE-2026-54291 and CVE-2020-13956. Broad Log4j family matches are distinguished from actual API/Core/bridge prerequisites in the vendor advisory; no suppression was needed.

The following21 distinct CVEs remain below the unchanged threshold. They are not silently discarded or described as patched. Spring portfolio vendor pages were checked, and compatible fixed lines remain enterprise-only. Commons JXPath's two NVD records are explicitly **DISPUTED**, and the vendor security page identifies no corresponding validated security fix; no speculative JXPath update or suppression was applied. An available library release alone is not evidence that those disputed CVEs were fixed.

| CVE | Scanner dependency | CVSS3 | Disposition |
| --- | --- | --- | --- |
| [CVE-2022-40159](https://nvd.nist.gov/vuln/detail/CVE-2022-40159) | commons-jxpath-1.3.jar | 6.5 | Vendor-disputed record; no validated security fix applied. |
| [CVE-2022-40160](https://nvd.nist.gov/vuln/detail/CVE-2022-40160) | commons-jxpath-1.3.jar | 6.5 | Vendor-disputed record; no validated security fix applied. |
| [CVE-2026-41707](https://spring.io/security/cve-2026-41707/) | spring-security-oauth2-resource-server-6.5.11.jar | 7.4 | 6.5.12, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-47834](https://spring.io/security/cve-2026-47834/) | spring-data-jpa-3.5.13.jar | 6.5 | 3.5.14, enterprise only; application sort allowlists remain in place. |
| [CVE-2026-47841](https://spring.io/security/cve-2026-47841/) | spring-security-oauth2-resource-server-6.5.11.jar | 7.4 | 6.5.12, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-47842](https://spring.io/security/cve-2026-47842/) | spring-security-oauth2-resource-server-6.5.11.jar | 6.5 | 6.5.12, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-47874](https://spring.io/security/cve-2026-47874/) | reactor-netty-core-1.2.18.jar | 5.3 | 1.2.19, enterprise only. |
| [CVE-2026-47879](https://spring.io/security/cve-2026-47879/) | spring-cloud-gateway-server-4.3.5.jar | 8.7 | 4.3.6, enterprise only; JsonToGrpc filter not configured. |
| [CVE-2026-47883](https://spring.io/security/cve-2026-47883/) | spring-core-6.2.19.jar | 6.1 | 6.2.20, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-47885](https://spring.io/security/cve-2026-47885/) | spring-core-6.2.19.jar | 7.5 | 6.2.20, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-47886](https://spring.io/security/cve-2026-47886/) | spring-core-6.2.19.jar | 7.5 | 6.2.20, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-47887](https://spring.io/security/cve-2026-47887/) | spring-core-6.2.19.jar | 6.1 | 6.2.20, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-47888](https://spring.io/security/cve-2026-47888/) | spring-core-6.2.19.jar | 7.5 | 6.2.20, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-47889](https://spring.io/security/cve-2026-47889/) | spring-core-6.2.19.jar | 7.5 | 6.2.20, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-47893](https://spring.io/security/cve-2026-47893/) | spring-core-6.2.19.jar | 7.5 | 6.2.20, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-59276](https://spring.io/security/cve-2026-59276/) | spring-security-oauth2-resource-server-6.5.11.jar | 5.9 | 6.5.12, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-59280](https://spring.io/security/cve-2026-59280/) | spring-core-6.2.19.jar | 4.3 | 6.2.20, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-59281](https://spring.io/security/cve-2026-59281/) | spring-core-6.2.19.jar | 6.1 | 6.2.20, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-59282](https://spring.io/security/cve-2026-59282/) | spring-core-6.2.19.jar | 7.5 | 6.2.20, enterprise only; no blanket application non-applicability claim. |
| [CVE-2026-59284](https://spring.io/security/cve-2026-59284/) | spring-cloud-context-4.3.3.jar | 7.6 | 4.3.4, enterprise only; writable env endpoint not exposed in current configuration. |
| [CVE-2026-59314](https://spring.io/security/cve-2026-59314/) | spring-core-6.2.19.jar | 3.7 | 6.2.20, enterprise only; no blanket application non-applicability claim. |

Actual exploitability of the additional broad family matches is not inferred from the version-only scanner result. The writable env endpoint is outside the configured Actuator exposure allowlist; no JsonToGrpc filter is configured. These controls do not remove the findings or replace the outstanding vendor patches.

## Evidence and remaining release work

Machine-readable summary: [security verification evidence](../deploy/security-verification-2026-10-25.json). It includes executed commands/exit codes, exact remaining CVEs, data timestamps, raw report hashes and local log paths. Raw JSON/HTML reports and hashed source manifests are preserved under `/Users/roshan/.cache/orderflow-security/2026-10-25/`; they are generated evidence rather than committed repository data.

Security release blockers: the seven CVSS>=9 Spring findings, other recorded unpatched portfolio dependencies and unavailable OSS Index coverage. Obtain valid compatible enterprise patches or reassess a future compatible public release, then rerun the unchanged gate and regressions. Separately, full real-stack acceptance still needs an adequately provisioned environment. No AWS resources or DNS actions were attempted. At the end of the assessment, the three dependency files and this report/evidence were uncommitted at HEAD75d6b16. The subsequent authorized publication is recorded above.

**SECURITY GATE STILL BLOCKED**
