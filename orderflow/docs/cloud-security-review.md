# Phase 10 dependency security review

Reviewed on 2026-10-13. The owner chose to retain Spring Boot 3.5 and document remaining findings instead of migrating to Boot 4 or configuring commercial Spring repositories. The CVSS 9 dependency gate remains enabled; no suppression file, threshold increase or error bypass was added.

## Executed assessment

The default anonymous NVD API update failed after retries and produced no usable assessment. A second, bounded run consumed the [official NVD JSON 2.0 bulk feeds](https://nvd.nist.gov/vuln/data-feeds) in a separate temporary cache. OWASP Dependency-Check 12.2.2 completed analysis and failed the existing threshold. The initial report contained 20 distinct CVEs at scanner scores 9 or above across Spring, Tomcat, Netty, FreeMarker and Apache HTTP Client. The completed scan after compatible updates contains seven distinct findings at that threshold; see [machine-readable evidence](../deploy/local-security-evidence.json).

The Maven security profile now uses the official bulk-feed URL, with automatic updates, certificate validation and scanner-error failure retained. This avoids depending on anonymous paginated API retrieval. See [Dependency-Check configuration](https://jeremylong.github.io/DependencyCheck/dependency-check-maven/configuration.html). Cache data and full generated reports remain outside source control.

## Compatible dependency updates

| Component | Previous | Updated pin | Evidence |
| --- | --- | --- | --- |
| Tomcat |10.1.55 |10.1.61 | [Apache security advisories](https://tomcat.apache.org/security-10.html), [release changelog](https://tomcat.apache.org/tomcat-10.1-doc/changelog.html) |
| Netty BOM |4.1.135.Final |4.1.139.Final | [Netty security advisories](https://github.com/netty/netty/security/advisories), published Maven Central4.1 artifacts |
| FreeMarker |2.3.34 |2.3.35 | [Apache advisory](https://lists.apache.org/thread/hrd7o2ylwkkswdyhyzllgqt0f80kyd5y) |
| Apache HTTP Client5 |5.5.2 |5.6.5 | [Apache advisory](https://lists.apache.org/thread/bhf7g2zwpom2ohvwjjjlonc93br2s8vq) |
| Apache HTTP Core5 |5.3.6 |5.4.4 | [HTTP Client5.6.5 published parent POM](https://repo.maven.apache.org/maven2/org/apache/httpcomponents/client5/httpclient5-parent/5.6.5/httpclient5-parent-5.6.5.pom) requires the matching Core5.4.4 line |

These are explicit compatible overrides under Boot 3.5.16. The patched source passed the complete 264-test Java build; all twelve application images built for both Linux AMD64 and ARM64. Final build, tests, image and scanner results are recorded in [readiness](phase-10-readiness.md); a version edit alone is not verification.

## Remaining Spring findings

Spring Framework 6.2.19 and Spring Security 6.5.11 remain on the specified public Boot 3.5 line. Their published fixes 6.2.20 and 6.5.12 require commercial repository access according to the vendor advisories. Those versions were absent from Maven Central metadata when checked. No enterprise credentials or license were assumed.

The scanner's broad library matches and high NVD scores are distinct from vendor severity and application reachability. The following preconditions were reviewed against application source/configuration; absence of an observed feature is not a patched-library claim or authorization to bypass CI.

| Advisory | Vendor precondition | Current application evidence |
| --- | --- | --- |
| [CVE-2026-59270](https://spring.io/security/cve-2026-59270/) | Embedded UnboundID LDAP listener reachable by an attacker | No embedded LDAP configuration or `UnboundIdContainer` usage observed; no LDAP port published |
| [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) | Implicit view resolution through `XsltView` and broad MVC mapping | No `XsltView` registration observed; business APIs use annotated JSON controllers and frontend rendering is Next.js |
| [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) | Attacker-controlled view fragments streamed to another user over SSE | Notification SSE streams owned structured notification data; no fragment rendering observed |
| [CVE-2026-59313](https://spring.io/security/cve-2026-59313/) | Functional MVC SSE with unstructured attacker-controlled messages | Notification transport uses `SseEmitter` and JSON; no `ServerResponse.sse()`/functional MVC route observed |
| [CVE-2026-47891](https://spring.io/security/cve-2026-47891/) | WebFlux XML decoding through Aalto | No Aalto parser configuration observed in application source; public business request contracts use JSON |
| [CVE-2026-47892](https://spring.io/security/cve-2026-47892/) | WebFlux functional endpoints hosted through `DispatcherServlet` | Gateway runs reactive Netty; business servlet controllers use annotations, with no functional WebFlux routing observed |
| [CVE-2026-59283](https://spring.io/security/cve-2026-59283/) | `SimpleEvaluationContext` with expression compilation enabled | No such context or explicit compiler enablement observed; authorization expressions are application-owned constants |

Future feature/configuration changes can alter these preconditions. Recheck before enabling LDAP, XML/XSLT, functional SSE/view fragments or expression compilation. Caddy's private-service blocking, body bounds and ownership checks remain defense layers; they do not fix vulnerable dependency versions.

The release pipeline still blocks on the raw dependency gate. Clearing it requires verified patched Spring dependencies through authorized repository access, an explicitly scoped supported-stack migration, or a separately reviewed and approved policy decision with concrete applicability evidence. The current instruction to retain Boot3.5 and document findings did not authorize a scanner exception.
