# OrderFlow container security remediation — October 28, 2026

**Decision: CONTAINER SECURITY REMEDIATION INCOMPLETE.** The unchanged Maven OWASP gate passes, all 24 rebuilt application images have zero reported Trivy findings, and complete Java-library analysis is now available. The infrastructure set still has **174 raw occurrences, including one CRITICAL and 52 HIGH**, with an exposed Caddy HTTP/2 path and additional unresolved vendor/runtime findings. Native codec limitations also remain. No AWS staging approval or full-commerce acceptance is claimed.

Assessment spans October 27–28; scan/build/test timestamps are retained in the [verification evidence](../deploy/container-remediation-verification-2026-10-28.json). This continues the [October 27 freeze](release-freeze-2026-10-27.md), preserving its historical results. Branch `master`, HEAD **`a1536d32cee31d6c1ea2ac7710ebca031bdeed2d`** is unchanged. All remediation changes are local and uncommitted. No image publication, Git commit/push, AWS account/resource operation, Terraform apply, DNS change or user-data deletion occurred.

## Applied changes and exact runtime versions

| Component | Previous | Verified replacement / disposition |
|---|---|---|
| Eleven backend builder/runtime images | Temurin 21.0.12.1+1 runtime | Official Amazon Corretto Alpine 3.24 index `sha256:d942d2e3e0f480c076ec10d03deac7444dc717bc25bb3e2bc5373676b7b6c406`; signed `amazon-corretto-21=21.0.13.0.11-r0` APK; actual JVM **Corretto 21.0.13.0.11 / OpenJDK 21.0.13+11-LTS** on all 22 final images |
| Java OS packages | Vulnerable Brotli 1.2.0 | Supported APK upgrade includes Brotli 1.2.1; exact final package versions remain in complete scan inventories |
| Maven distribution | 3.9.11 / Plexus Utils 3.6.0 | **3.9.16 / Plexus Utils 3.6.1**, addressing CVE-2025-67030; official checksum verified and wrapper distribution SHA-256 pinned |
| LZ4 Java | `at.yawk.lz4:lz4-java:1.11.2` | **1.11.4**, compatible patch for CVE-2026-106449 /106450 /106451 |
| Guava | `com.google.guava:guava:33.0.0-jre` | **33.7.2-jre**, compatible patch for CVE-2026-102554 |
| Snappy Java | `org.xerial.snappy:snappy-java:1.1.10.7` | **1.1.10.11**, includes fixes for CVE-2026-93451 /108106 and MEDIUM CVE-2026-116590; no Hadoop runtime is used |
| Frontend base | Node 22.23.4 with global npm tooling | **Node 22.23.4 Alpine 3.24**, index `sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914`; global npm/npx/Corepack/Yarn/pnpm tooling removed only from final stage |
| Framework / Java major | Boot 4.1.2, Cloud 2025.1.4, Java 21 | Unchanged; no business features or application Java/TypeScript source edited |

The documented [Temurin 21.0.13 release](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.13%2B9) was evaluated; available official container tags still resolved to the previous runtime during this assessment. The signed [Corretto 21.0.13.0.11 release](https://github.com/corretto/corretto-21/releases/tag/21.0.13.0.11) provides the compatible Java 21 security update. The pinned Corretto container index initially contains vendor build .10; its normal signed vendor APK repository supplies .11. APK signing-key/index verification and runtime checks are retained locally. No untrusted APK option or ad-hoc JRE repackaging is used. Both build and runtime stages upgrade through APK, consistent with [Corretto container maintenance guidance](https://aws.amazon.com/corretto/faqs/).

The .11 update removes the implicated roots in [CVE-2026-114820](https://www.oracle.com/security-alerts/alert-cve-2026-114820.html). Actual `keytool` checks on **every final backend image** verified both root fingerprints absent and two legitimate control roots present. Runtime layers differ because build-time package metadata and existing service-user setup differ; identity was checked individually rather than inferred from identical layer hashes. Existing users, application JAR paths, health probes, ports and JVM settings are preserved. A supported full JDK is retained in the final image, increasing disk footprint; no smaller custom JRE is asserted equivalent.

Compatible library release evidence: [LZ4 1.11.4](https://github.com/yawkat/lz4-java/releases/tag/v1.11.4), [Guava 33.7.2](https://github.com/google/guava/releases/tag/v33.7.2), [Snappy 1.1.10.11](https://github.com/xerial/snappy-java/releases/tag/v1.1.10.11). Dependency overrides are central in the reactor; Maven verification and image-library inventories confirm the versions actually packaged. No scanners, severity thresholds, exclusions or vulnerability suppressions were changed.

Frontend final images keep standalone production modules/static assets, a nonroot UID 1001, Node-based health checks and supported base shell utilities. Tooling was deleted, not relocated to evade scanning. `npm ci` and build tools remain available in build stages. Both final runtimes served HTTP and static assets successfully with the global tools absent.

## Scan coverage and comparable before/after

The original October 27 **1,377** occurrences were incomplete: completing JAR analysis adds **92**, discovering the PostgreSQL `gosu` Go binary adds **61**, and the refreshed vulnerability database adds **47**. Thus **1,377 →1,530 →1,577** is a coverage/database change, not a regression caused by remediation. The fair comparison scans the exact old and new artifacts with the same complete analyzers and database snapshot.

| Full comparable 32-image set | CRITICAL | HIGH | MEDIUM | LOW | UNKNOWN | Total | Unique advisory IDs |
|---|---:|---:|---:|---:|---:|---:|---:|
| Old artifacts, refreshed full coverage | 28 | 587 | 729 | 205 | 28 | **1,577** | **454** |
| Final artifacts, same coverage/data | 1 | 52 | 63 | 49 | 9 | **174** | **98** |

All **24 application images** report **zero** findings under this snapshot; remaining 174 occurrences are in infrastructure. This does not erase the three separate OWASP MEDIUM findings below. Occurrence comparison: **1,404** old occurrences no longer reported, **173** retained, **one** new image-specific occurrence. Advisory/package/image-pair comparison, ignoring package versions/path changes: **1,123 removed, 172 retained, one new**. Globally, **356 advisory IDs** disappear and **zero new global advisory IDs** appear. These measures differ because shared binaries/packages duplicate occurrences.

The new image-specific pair is node-exporter Go stdlib **CVE-2026-46600**, previously present elsewhere in the baseline. Exact-binary analysis found the advisory's affected DNS symbols absent. The raw finding remains; this is not a newly demonstrated exploit or a suppression.

Trivy **0.76.1** completed full OS and available library analysis for old 32 +candidate eight +final application 24 images. All executions succeeded, including nested JAR identification in all 22 final backend images and bundled Kafka broker JARs. The previous Java-metadata timeout is resolved: the complete official ECR Java DB archive (990,329,871 bytes) was acquired and checksum-verified with bounded retries. Java DB updated `2026-10-27T01:08:30Z`; OS DB updated `2026-10-27T19:17:44.708952708Z`, downloaded `2026-10-28T00:49:22Z`, next update `2026-10-28T19:17:44Z`. All severities/unfixed findings are retained; no ignore rules or vulnerability filters were added. Scanner exit 0 means completed analysis, not security approval. Scanners did not upload project/image data.

Portable evidence: [all before findings](../deploy/container-findings-before-2026-10-28.csv), [all after findings](../deploy/container-findings-after-2026-10-28.csv), [exact image inventory](../deploy/container-image-inventory-2026-10-28.json), [all 53 CRITICAL/HIGH dispositions plus manual CA evidence](../deploy/container-applicability-2026-10-28.json). Raw reports, executable commands, database metadata, checksums and full logs remain under `/Users/roshan/.cache/orderflow-container-remediation/2026-10-27/`; none is a hidden finding suppression.

## Exact infrastructure references

Only cloud/staging Compose references were updated, after actual candidate compatibility probes. `database-bootstrap` shares PostgreSQL's candidate. Classic local-development Compose still uses its existing development infrastructure references/listeners; those are not represented as remediated or frozen by this report. Infrastructure scanning/runtime coverage is **amd64**, the proposed Mumbai host architecture. ARM64 manifests were inspected, but infrastructure ARM64 runtime/scans are not claimed.

| Service / new tag | Previous registry index | New registry index | Tested/scanned amd64 child manifest |
|---|---|---|---|
| postgres `16.15-alpine` | `sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea` | `sha256:0cefaabd882e42bcb2a2f3dc56d3ce4770560ba62fd7f197f1a2e008fd5731ab` | `sha256:cbf42f0edc946c0bc1b8eeed62403734e1f25dc67a3491455d07a92fdb558b28` |
| redis `7.4.12-alpine` | `sha256:858f009f9709ce576febc734aa78b8f6d624b82571f9ddb6bda4377c833b3499` | `sha256:338fbfc036dc5517ac0b04d6de21521085935336e565f223406180e9d84166e2` | `sha256:1cf73a2f04808e55a8bd2f5706c2aa0ebb0f71938f93deb7af79f48311cd5cb9` |
| caddy `2.11.7-alpine` | `sha256:d8542f48d34a9cf4e4c11a478865229840e87e4c96ea3f439101f31a5d35f75f` | `sha256:eb4d35b7452d1ab6171fbdbe8d9275b2301dad6dad92115d91e19fb9b24d510f` | `sha256:04913b1da43339a40ad1ea87827d70a576ac5aa6cf674c6e24fdca675887533a` |
| prometheus `v3.15.1` | `sha256:63805ebb8d2b3920190daf1cb14a60871b16fd38bed42b857a3182bc621f4996` | `sha256:fa3302279710be2f21a8301ec17a8b4f2301fd829bd113839b3b6b02b6f191a2` | `sha256:d445d1806b38f85b5c394a1b87f94113fde0473b45245922c2e8b1bd41fa68ae` |
| grafana `12.4.14` | `sha256:6ac590e7cabc2fbe8d7b8fc1ce9c9f0582177b334e0df9c927ebd9670469440f` | `sha256:ae509d9ae8c1021ca32ec370a9ca975d40165eda9fccd1e1102d317ff8787cd7` | `sha256:1b59d2076a5e08e0e9929a1225f37869ee7719f4a11c004fbfcfa8b772858e89` |
| node-exporter `v1.12.1` | `sha256:d00a542e409ee618a4edc67da14dd48c5da66726bbd5537ab2af9c1dfc442c8a` | `sha256:1b4e4438faca4dd7e001dd445d161a4a2091b0fededa84093b3a8dfeae1f1be0` | `sha256:da83fae85603c4e47e6c68369a7d746e2dda683dc35ea2e234b4f171e0d92798` |
| blackbox-exporter `v0.29.1` | `sha256:92e05d5fe0df01d3980518dc42f07b778a8997048b57392ebc7f7391ebd7bb06` | `sha256:ea1c7aa3ef3f307cd03e85f34abb604478ccb90b567fc690da0cd9776da76b13` | `sha256:66eae352a79e6f677aeddd8c25a9bdb017586dc5fbd230f22329213d891f94bd` |
| kafka `4.3.2` | `sha256:4ceccc577f03f51f6af8dbfda55194d0d892f4fa7913ffbded567ce3895622ed` | `sha256:852a0d49fc4e656b022e7f23785411802c9a088c78a7c5e39f33969de9c227a7` | `sha256:8f325fc00143fed78eabe00b033a86747078bb06edc21345d06651f77ae44f30` |

## Exact artifact comparison and executed smoke scope

Application digests below are **local immutable image configuration IDs**, not registry manifests; all 24 application `RepoDigests` are empty and ECR digest status is **NOT PUBLISHED**. Infrastructure rows use exact amd64 child-manifest references. The inventory additionally records previous/new config IDs, index/child digests, platforms and source hashes. A later authorized publication must build from an approved committed SHA, record ECR manifest digests and scan those exact artifacts.

“Removed pairs” is advisory/package/image pairs no longer reported, not a count of distinct exploits. “Residual” retains the raw count before applicability interpretation.

| Image / architecture | Old config ID or child reference | New config ID or child reference | Before C/H | Residual C/H | Removed pairs | New pairs | Smoke / disposition |
|---|---|---|---:|---:|---:|---:|---|
| kafka-amd64 | `apache/kafka@sha256:5862db4a63a6dd7d46fd14771b10a1b39e069c2c47f17d8e4640f960720a0ead` | `apache/kafka@sha256:8f325fc00143fed78eabe00b033a86747078bb06edc21345d06651f77ae44f30` | 6/91 | 0/2 | 279 | 0 | Six broker persistence stages +three refusal cases PASS; two native-library flags and manual old-JRE gap remain |
| service-registry-arm64 | `sha256:86e2a7a2dec0306b583093e81aa1508afae06d1653bd461881de697b4a9ea49d` | `sha256:4b2ac4def2fefabb422ed84ed19d117466d31f46e9bc0318bc602d7160a60b12` | 0/2 | 0/0 | 2 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| api-gateway-arm64 | `sha256:2d86153b09d3aad6918634d78208006b5e9550d0c5bc263db350df422eff9008` | `sha256:435e13fe502b19ee63d2ab1af3baa766027054f9c4fa3c6c81776f35ffb1ea98` | 0/1 | 0/0 | 1 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| auth-service-arm64 | `sha256:11982d6a587b1683845a81bc0b3cba0ccd032a54d8d4b6527166e3254ec67e26` | `sha256:68cb0a9313ce0d5af5ee5862215902ee4cb303482a055930a5766dca2778ca3f` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| product-service-arm64 | `sha256:4b7aab4e1de3569f90eaeb8f46cf6b586ef7198b9723d8e3878c5801e9e13148` | `sha256:2c7306caeb35d4f7cb505e9f818cdbf7878104a530db639d801debaa7a743499` | 0/1 | 0/0 | 1 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| cart-service-arm64 | `sha256:0e5f83ea2fb5f7a06c887ca1d6dfd784a3ca8a9c4f2a6d513ea65f0370fc2bf8` | `sha256:2e5d573ed8703d78f8e5096aa8ff43caeec03ffc84620c3640c5ba33af7c62be` | 0/1 | 0/0 | 1 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| inventory-service-arm64 | `sha256:f024651c095086696a23a53f008c187c0f20f73cac21784f4c54cc5fcb262cb1` | `sha256:fc4fb132887c3dd461bc7ba9d4ad28362959b5348c2d0fe05d8d5a274d4c616a` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| order-service-arm64 | `sha256:0e6c7ff97709109c1ca46866cfe968712740ecb0fc0fc2d2840c6147ea922924` | `sha256:5026980cfd93d6a9274f8c3bb5f7c8454797835fe7c6a34707863b8d55e1e40d` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| payment-service-arm64 | `sha256:723b241f940a7b799635d9679c1c1704881d78af3e7ca0bc03b270e899f98bae` | `sha256:7cadcfe69ee2e2276dc2e1d1583d13c5b43d659afe528e69ebcf669a783704f6` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| delivery-service-arm64 | `sha256:5ca2a93c3a99dc0a0c9b94596f1b0f89346a5eba6b55a6565d116f6fc8b32407` | `sha256:a70f7ae9cf8b65d692f65932f70f033d25fd88b47f27e955c5d76e4e67473cc2` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| notification-service-arm64 | `sha256:bca1c3f40184de77fbe0d1407e27d8e05bfa2b1be46b6972bc0c307c1928d5db` | `sha256:38e78b98dac54cea028b702bdefb6d754d898a3d932df73de3539351cb9094c3` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| operations-service-arm64 | `sha256:6dc33fdbbc2c92ef738e1f40d3b264fafe8ce8c1734b7ff8b49eebb3043e7f71` | `sha256:d6b28d7ef1cbfbd471da7ac3e8cf30ff8586e76ad70ecfaf39f5114dddecb4a1` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| service-registry-amd64 | `sha256:65ea7ab3161a58fdcaea222ad3a428eb2f4579b3fd1be0cc0871b5f5f33eaa68` | `sha256:0d646920c02ded52c3628bda472059f6253330a68df66a440306adfead01035a` | 0/2 | 0/0 | 2 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| api-gateway-amd64 | `sha256:8b78a787ccd9551ae064fb8a40022d120a7f30d7f4f968d6410d58049e0fe30e` | `sha256:3999c78fec9495b1fe4900369b161516156fe7bbbf5099f036246c7d775dfaa3` | 0/1 | 0/0 | 1 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| auth-service-amd64 | `sha256:93036b4b65c173126d373e722d38247551d687ae2db6eaf09a076060ff41cb09` | `sha256:eee21c962d83bcd457ec446371e0e8b16e7639bd6b324cdb84fbe894c6debd4f` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| product-service-amd64 | `sha256:f2a186df14515689da2ffd911ae480fb4c10c5def323525bf218eedd68436978` | `sha256:907a0f9c7e08a3c4feaa44d427f09f5717ac88ca91d8a1baad36ae6ca7d4a260` | 0/1 | 0/0 | 1 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| cart-service-amd64 | `sha256:b9441b6fe66182a0a3123fcfb5ec1a3052124b6759a253fcf0d4d208c0dacf46` | `sha256:6c49a0384fe7046dd93c8e6ba29f97725613696e1286134acd28f8e547f2582d` | 0/1 | 0/0 | 1 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| inventory-service-amd64 | `sha256:ed81c781c6f586c1905ab33bd573b668be2b3f6e4414247e14750ddf7bfd26a8` | `sha256:69601dec6de4d5f89654de97ae4380f106a0bdc9f3c1387438b99229b4638219` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| order-service-amd64 | `sha256:e03035690b0bc8fed692cd325a6e325242cbacaff97ae6f94e955aa7dec8c9f5` | `sha256:89d55ddbae960efcd8bf352e8dcfc8f6d181e402af6312e73431006667321131` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| payment-service-amd64 | `sha256:7fcbfb90ac5dfa3abc87cdde8b862af5a196a52382633bbb504286fcdec34d49` | `sha256:c9986f846b762aeeeed1696e5143a8d14962e2a0ea9c8823151755a80282967e` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| delivery-service-amd64 | `sha256:58c3f315ca2679fe0f045c4c44beb8f9cce3660b132e1e5b818dce3685961714` | `sha256:a04d5d7193b60d9721b466033238364770fd52de613b460f26ccfb31afea9fe6` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| notification-service-amd64 | `sha256:c3fd0477234326fd7dbf226d69242526cf116eee7aa3af80346e0bc6823134f2` | `sha256:fd5597e7b16ccd6b3c228cee398f1da59863d3c6b6d4b90637309ea732af403c` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| operations-service-amd64 | `sha256:96a0839fd38d0ce03880cf408641d8c56831dcc14907d134a4724f095abfed32` | `sha256:e4d22e71cd2836683e8cbdfcf804734e73b2c4159a8a000ab66783ffd7d327ef` | 0/4 | 0/0 | 7 | 0 | Readiness UP, JVM metrics, OOM false; zero findings |
| frontend-arm64 | `sha256:dd264cd8cc2992c3c980b3ea911ab9d47d3bb79ab891faffb5ca47681a1f5e29` | `sha256:0eae0a7a44091e81e3505dceb23c5e4212dcb451afb25b15aae099a9d8ccbb91` | 0/11 | 0/0 | 21 | 0 | HTTP/static/tool removal PASS; zero findings |
| frontend-amd64 | `sha256:e85b417dcd3771ccb45ccca105e68c45857cae1eea584a7e2008ef26c9ba1bce` | `sha256:beca3a85d09c24d74b1357a499e4ae6afca88f455bb6aa6ce0a1d3f1452d7872` | 0/11 | 0/0 | 21 | 0 | HTTP/static/tool removal PASS; zero findings |
| blackbox-exporter-amd64 | `prom/blackbox-exporter@sha256:c78a587054acf7ac35514a4a3ea577686e52630288f28f005b1ea754857b6151` | `prom/blackbox-exporter@sha256:66eae352a79e6f677aeddd8c25a9bdb017586dc5fbd230f22329213d891f94bd` | 2/50 | 0/0 | 113 | 0 | Positive and redirect-negative HTTP probes PASS; one non-C/H residual flag |
| caddy-amd64 | `caddy@sha256:173b26306d711395accaeba8b67afcaad2a085ccb1e6bf26010bfe8c095a5229` | `caddy@sha256:04913b1da43339a40ad1ea87827d70a576ac5aa6cf674c6e24fdca675887533a` | 0/8 | 0/8 | 3 | 0 | Routing/SSE/redaction PASS; configured H2 and trust-validation gaps remain |
| postgres-amd64 | `postgres@sha256:1a66d744c1b459e13b05a8fca341da84cb63383e99ce262210efee5a319d4551` | `postgres@sha256:cbf42f0edc946c0bc1b8eeed62403734e1f25dc67a3491455d07a92fdb558b28` | 1/25 | 1/25 | 3 | 0 | Eight DB schema/Flyway/sentinel old→new→old PASS; all 26 C/H gosu symbols absent |
| grafana-amd64 | `grafana/grafana@sha256:11c2e6c7993917ea89292b942e3428f73907b2809d554154bfb8b4ceb2301cc8` | `grafana/grafana@sha256:1b59d2076a5e08e0e9929a1225f37869ee7719f4a11c004fbfcfa8b772858e89` | 14/171 | 0/2 | 355 | 0 | SQLite old→new→restart→old PASS; two Tempo version-mapping flags, fixes included |
| node-exporter-amd64 | `prom/node-exporter@sha256:863b62ff9f392b6f472e22b4b670ad1cabb66b3042502ebd4b2cd192733e1bce` | `prom/node-exporter@sha256:da83fae85603c4e47e6c68369a7d746e2dda683dc35ea2e234b4f171e0d92798` | 1/46 | 0/15 | 75 | 1 | Metrics/textfile/read-only/PID PASS; 15 H flags reviewed, two unresolved parser paths |
| prometheus-amd64 | `prom/prometheus@sha256:8672a850efe2f9874702406c8318704edb363587f8c2ca88586b4c8fdb5cea24` | `prom/prometheus@sha256:d445d1806b38f85b5c394a1b87f94113fde0473b45245922c2e8b1bd41fa68ae` | 4/106 | 0/0 | 122 | 0 | TSDB/config/15 rules old→new→restart→old PASS; two non-C/H residual flags |
| redis-amd64 | `redis@sha256:ca0acbb137c1dc3339c8b147a58fd6f42775d4599327b50e7b116c23de501af2` | `redis@sha256:1cf73a2f04808e55a8bd2f5706c2aa0ebb0f71938f93deb7af79f48311cd5cb9` | 0/2 | 0/0 | 23 | 0 | AOF old→new→old PASS; no residual findings |

## Remaining CRITICAL/HIGH findings and release blockers

All **53** raw C/H occurrences have a recorded official advisory, package/version/fix, prerequisites and exact-binary/configuration disposition. The matrix preserves scanner severity even where vendor applicability is narrower. Binary symbol analysis is limited: symbol presence is not a proven exploitable call path; absence applies only to the exact inspected binary/advisory symbols. It does not grant a permanent blanket exemption after a rebuild or configuration change.

| Material unresolved item | Installed → reported fix | OrderFlow prerequisites / disposition | Required next remediation |
|---|---|---|---|
| Caddy **CVE-2026-78669**, two HIGH occurrences | `golang.org/x/net v0.59.0 →0.60.0`; Go `1.26.8 →1.26.9 /1.27.2` | Actual affected HTTP/2 symbols and configured `protocols h1 h2` are present; published HTTPS can reach the path. Exploit success was not attempted. | Supported vendor rebuild with both relevant fixes, exact-digest scan and proxy regression test; remains a release blocker. [Official Go advisory](https://pkg.go.dev/vuln/GO-2026-6611) |
| Caddy **CVE-2026-111943**, HIGH | Go `1.26.8 →1.26.10 /1.27.3` | `crypto/x509.Certificate.Verify` is present; ACME uses outbound certificate validation. Image CA bundle also retains an implicated root; forged-chain/interception success is not claimed. | Vendor Go/trust-store rebuild and validation; restricting inbound tester access does not patch outbound trust. [Official Go advisory](https://pkg.go.dev/vuln/GO-2026-6809) |
| node-exporter **CVE-2026-33818** and **CVE-2026-56860**, two HIGH occurrences | Go `1.26.5 →1.25.13 /1.26.6 /1.27.0-rc.3` as reported; use a stable supported fixed branch | ASN.1 /relative-URL parser symbols are present; actual attacker-controlled input paths remain unresolved. | Patched vendor exporter rebuild, or conclusive reviewed reachability evidence; private metrics alone is not a patch. [ASN.1 advisory](https://pkg.go.dev/vuln/GO-2026-5972), [URL advisory](https://pkg.go.dev/vuln/GO-2026-6218) |
| Kafka **CVE-2026-111497**, HIGH | `brotli-libs 1.2.0-r1 →1.2.1-r0` | Native library present; malformed Brotli caller path is unproven. Kafka's normal message codecs do not include Brotli; native/font processing was not conclusively excluded. | Supported vendor patched image; keep unresolved until patched or reachability conclusively reviewed. [Alpine advisory](https://security.alpinelinux.org/vuln/CVE-2026-111497) |
| Kafka **CVE-2026-77214**, HIGH | `libexpat 2.8.5-r0 →2.9.0-r0` | Requires an embedding caller to pass an unchecked length to `XML_ParseBuffer`; installed library confirmed, that caller/input path unproven. | Supported vendor patched image or reviewed caller evidence. [Alpine advisory](https://security.alpinelinux.org/vuln/CVE-2026-77214) |
| Kafka **CVE-2026-114820**, supplemental manual finding outside Trivy totals | Bundled Temurin **21.0.12.1+1**, before current Java 21 security/CA patches | Exact exported Kafka `cacerts` retains both implicated root fingerprints. Configured broker listeners are plaintext, so an active broker TLS exploit is not asserted; the vendor runtime is still behind. | Vendor image with current patched Java 21 and CA store, preserve metadata format/identity and repeat broker probes. [Vendor CA alert](https://www.oracle.com/security-alerts/alert-cve-2026-114820.html) |

The remaining disposition groups explain all 53 raw rows without suppressing them:

| Review group | Occurrences | Meaning |
|---|---:|---|
| Affected Go symbols absent | 31 | PostgreSQL gosu: one CRITICAL +25 HIGH; node-exporter: five HIGH. Exact-binary inspection, with positive analysis controls; raw flags retained. PostgreSQL's gosu 1.19 /Go 1.24.6 is unchanged between old/new images. |
| Caddy unused native helper/library paths | 3 | Native Brotli/curl-related flags; no configured helper invocation and no linkage into the static proxy established. Required health check uses wget. |
| Caddy configured HTTP/2 path | 2 | CVE-2026-78669 remains applicable to the configured path; blocker above. |
| Caddy certificate-validation patch gap | 1 | CVE-2026-111943 remains conditional and unresolved. |
| Caddy missing file-server or ECH prerequisites | 2 | CVE-2026-78667 /97031: no `file_server`/ServeFile routes and no ECH provisioning in current config. |
| Grafana Tempo fix commits already included | 2 | CVE-2026-21728 /28377: bundled pseudo-version commit `525d1bab07e0` contains both official fixes; ancestry and fixed source inspected. Scanner semver mapping still flags the pseudo-version. |
| node-exporter TLS/HTTP2 prerequisites absent | 5 | Reviewed exporter-toolkit startup has no `--web.config`; TLS/H2 disabled. Reassess if enabled. |
| node-exporter parser input path unresolved | 2 | CVE-2026-33818 /56860 retained as unresolved, not exempted. |
| node-exporter hostname authorization /HTML-template /XML prerequisites not identified | 3 | CVE-2026-39821 /56858 /56859: static landing-page inputs, Linux deployment and disabled remote Supervisord collector; exact source/config notes in matrix. |
| Kafka native library caller unproven | 2 | Brotli/Expat items above; no blanket exemption. |
| **Total** | **53** | No raw findings discarded. |

No compatible vendor artifact examined eliminated every applicable/unresolved infrastructure risk. Replacing embedded Go libraries inside vendor binaries is not a supported dependency override. A separately reviewed maintained infrastructure build pipeline, or an explicit policy decision on documented prerequisites, would require additional scope and evidence. HTTP/1-only Caddy mitigation was not applied; it would not resolve the CA/trust gap. Monitoring was not removed to improve counts.

## Verification outcomes

| Check | Result | Actual scope |
|---|---|---|
| `./mvnw -B -ntp clean verify`, Java 21.0.13+11, Maven 3.9.16 | **PASS** | **226 unit +60 integration**, zero failures/errors/skips; final three dependency overrides included. Full XML copied before later subset runs. |
| Fresh unchanged OWASP 12.2.2 aggregate gate | **PASS** | Threshold **9** unchanged; 165 dependency entries; no suppressions or analysis exceptions; NVD checked `2026-10-28T01:11:04Z`. Seven original Spring CVEs absent. |
| `npm ci`, lint, typecheck, full test, production build | **PASS** | 138 Vitest tests in 15 files; zero failures. Complete checks October 27; final source is unchanged apart from Dockerfile. |
| `npm audit`, `npm audit --omit=dev` | **PASS** | Both freshly repeated October 28: **zero** findings. Former four lint-chain findings absent. Global image npm tooling was a separate source of image findings. |
| Existing Playwright fixture suite | **PASS** | Fresh October 28 **10 passed**, one worker; mocked APIs. No real commerce credit. Generated `next-env.d.ts` drift restored to exact baseline bytes. |
| Application image builds | **PASS** | Eleven Java +one frontend, each amd64/arm64: **24 successful final builds**. |
| Backend image startup/readiness/metrics | **PASS** | **22** isolated sequential startups, actual dependency containers, readiness UP and Prometheus JVM metrics; OOMKilled false. Individual images, not the whole stack. |
| Actual JVM /APK /trust-store identity | **PASS** | All **22** final backend images separately checked; current Corretto .11, bad roots absent and valid controls present. |
| Frontend runtime HTTP/static/health/tooling absence | **PASS** | Actual amd64 and arm64 runtimes, required production assets and nonroot execution. |
| LZ4 Java-safe round trip | **PASS** | Both final architecture samples under read-only root and unchanged cloud noexec `/tmp`; actual factory `LZ4Factory:JavaSafe`. Library-level proof, not an all-service Kafka load test. |
| Native LZ4 and native Snappy under cloud constraints | **FAIL** | Both architectures raise `UnsatisfiedLinkError`; `/tmp` disallows loading extracted code. An exec-tmpfs diagnostic also exposed missing glibc loader for ARM LZ4. No native codec pass is claimed. |
| Full OS +available JAR/Go/JS scans | **PASS — execution** | All 32 final artifacts, complete metadata; **security approval still blocked** by residuals/manual gap. |
| Cloud /deployment /runtime+guard tests | **PASS** | 65 +37 +27 =**129** tests. Runtime count includes 13 real shell/file guard tests; vendor process boundaries mocked there, complemented by real broker trials. Final 27+37 suites/config checks repeated after all pins. |
| Compose /production-config validation | **PASS** | Effective production-like Compose, required images, private storage/keys, public-port policies and guard binding. |
| Terraform backend-disabled init, fmt, validate, mocked plans | **PASS** | **17** offline mocked tests; no authenticated account plan/apply. |
| Caddy and monitoring configuration/runtime probes | **PASS** | Candidate proxy handlers/SSE/413/redaction; both Prometheus configs and all **15** rules; Grafana and exporters as detailed below. Public ACME/full service targets NOT RUN. |
| Source secrets /log redaction /Git whitespace | **PASS** | Fresh source pattern scan and affected gateway/auth/payment log tests, no probable secrets. Pattern scan is not proof against every secret format. |
| Compatibility and source integrity | **PASS** | All **44** compatibility fixture hashes and **23** historical Flyway SQL files unchanged; all 24 built-image source hashes match current inputs; no application Java source changed. |
| systemd syntax | **PASS — prior unchanged evidence** | October 27 actual Ubuntu `systemd-analyze verify`; no systemd source changes in remediation. Host boot/shutdown still NOT RUN. |
| Full actual commerce, negatives/refunds, service SSE, outbox/DLT health | **NOT RUN** | Requires all actual services through Caddy; fixture/static metrics checks do not establish these outcomes. |
| Host OS/SSM, real EBS restart, live backup recovery, AWS A→B→A release | **NOT RUN** | No AWS host provisioned or operated. |

The seven original Spring CVEs remain absent: **CVE-2026-47884, CVE-2026-47890, CVE-2026-47891, CVE-2026-47892, CVE-2026-59313, CVE-2026-59283 and CVE-2026-59270**. OWASP retains **CVE-2022-40159 /40160** in Commons JXPath 1.3 (MEDIUM 6.5 each) and **CVE-2026-41115** in Kafka clients 4.2.2 (MEDIUM 4.3). The unchanged gate permits those findings; it is not a zero-vulnerability assertion. Its profile bytes match HEAD (SHA-256 `1e48fc68f0cded07dac3343fa3813fe8197d3db8d92a3ed5856cbfd699234a14`). Dependency entry count changed from 166 to 165 with the compatible dependency graph, not an analyzer exclusion.

Native codec disposition: repository configuration contains no producer compression override; the [Kafka 4.2 producer default is `none`](https://kafka.apache.org/42/configuration/producer-configs/#compression.type). Therefore these explicit native probe failures do not establish failure of the currently configured producer path, but they do prevent claiming Snappy/native-LZ4 compatibility or safe replay of arbitrary pre-existing compressed records. Before staging, confirm actual topic codec history and permitted inputs; if Snappy is required, provide a supported runtime/native-library solution and test it without making all of `/tmp` executable. No glibc shim, filesystem permission relaxation, codec configuration change or record reset was applied merely to turn this diagnostic green. Broker-side persisted LZ4 probes below are separate and passed.

## Infrastructure persistence, storage guard and recovery scope

All probes used uniquely named isolated test resources and synthetic data; no existing developer/production volumes were accessed or deleted. Test containers/networks were removed by their owning harness; owned data volumes were retained for audit. No global Docker prune, Redis flush or Kafka storage reset ran.

| Component | Actual executed compatibility /persistence evidence |
|---|---|
| PostgreSQL | Exact old/new vendor builds are both **PostgreSQL 16.15**; this is a base-image rebuild, not a destructive major DB migration. All eight real schema fixtures, synthetic Flyway history and SQL sentinels survived old→new→old. Separate final app startups ran actual Flyway with unchanged checksums on persistent DBs. |
| Redis | **7.4.11→7.4.12→7.4.11**, AOF record retained; no flush/reinitialization. |
| Kafka existing-volume cycle | **3.9.1→4.3.2→3.9.1**, actual persisted LZ4 batches and sentinels retained, no feature finalization. |
| Kafka fresh-volume cycle | Explicit approved synthetic empty volume only: **4.3.2→3.9.1→4.3.2**, initialized at **metadata.version 3.9-IV0**, **kraft.version 0**. LZ4 records, cluster/node/directory identity and finalized-feature values retained at all stages. |
| Kafka refusal cases | Wrong cluster ID, nonempty storage missing metadata and unapproved empty initialization all exit **78**; byte manifests unchanged. |
| Caddy | Existing isolated proxy harness on new exact candidate: routing/internal-path blocks, spoofed forwarding protection, immediate SSE, body-size 413 and log redaction. Public TLS/ACME and exploit attempts NOT RUN. |
| Grafana | **12.1.0→12.4.14→restart→12.1.0** synthetic SQLite dashboard/user/preferences/datasource, saved backup copy and integrity checks retained. This does not promise arbitrary future DB migration rollback compatibility. |
| Prometheus | **3.5.0→3.15.1→restart→3.5.0**, retained TSDB sentinel, actual healthy/ready endpoints and evaluated rule-health checks; default/cloud static config and all 15 rules validated. Synthetic target, not full application target availability. |
| node-exporter | Metrics, textfile collector, read-only and PID settings exercised. Docker Desktop could not establish real Linux host-root `rslave` propagation; host integration remains NOT RUN. |
| blackbox-exporter | Health/config, actual positive HTTP probe success=1 and redirect-negative success=0; target readiness bounded before assertions. |
| Encrypted backup /isolated restore | Candidate PostgreSQL 16.15, all eight owner roles/schemas/checksums/record hashes verified; AES-256-CBC/PBKDF2-SHA256/200,000 iterations; original repository evidence preserved. Live AWS recovery and all-service restore startup NOT RUN. |
| JWT /DB /Redis restart identity | Sequential arm64→amd64 actual auth restart preserves key bytes, pre-restart JWT acceptance, DB user, Redis record and all eight Flyway-history hashes. Direct HTTP probe used a test-only insecure-cookie override; production secure-cookie setting remains unchanged. |

Kafka 4.3.2 is selected from supported vendor releases, while preserving the existing rollback-compatible storage features under the [official upgrade guidance](https://kafka.apache.org/43/getting-started/upgrade/). New `infrastructure/kafka/safe-bootstrap.sh` rejects conflicting/malformed identity and storage settings, preserves the vendor CA entrypoint and existing data volume, and defaults **`KAFKA_INITIALIZE_EMPTY=false`**. A first authorized installation must explicitly enable initialization only for a verified empty volume, then restore false. Existing data must never be formatted to bypass a mismatch. The release archive validator requires this regular nonempty guard when Compose references it; legacy rollback archives remain accepted without this new requirement.

Final guard SHA-256: **`8e129c281535b771d0ab6e563fc88e12c8448933336c783bf3afd01e7485064e`**. Six real broker stages/three negative cases passed, but the evidence explicitly records `oneShotCleanRun=false`. Earlier harness failures and the valid-leading-hyphen cluster-ID bug were retained; the latter was fixed using `--cluster-id=<id>` and verified RED→GREEN. No previous failed attempt was relabeled successful.

Other retained failures: initial OWASP execution hit DNS/RetireJS/NVD transport failures after the paused machine; bounded fresh retries and the final unchanged gate passed. Initial Prometheus rule checks ran before evaluation, and blackbox probes ran before the synthetic target was ready; retries waited for actual health/readiness without dropping assertions. The backup fixture caught PostgreSQL's temporary Unix-socket initialization server; its readiness check now requires final TCP readiness (`pg_isready -h 127.0.0.1`). Native probe stdin/layer-identity harness assumptions were corrected, while actual native loading failures remain FAIL. Full source/logs retain the original failures and final outcomes.

## Resources, staging prerequisites and rollback

Local host has **8 GiB physical RAM**, Docker allocation **4,106,604,544 bytes (~3.83 GiB)**. No full 11-service stack was repeatedly attempted and no JVM heap was arbitrarily reduced. Sequential startup used existing cloud budgets: registry 320 MiB; five Kafka-facing services 1 GiB each; remaining backend services 640 MiB each. Example readiness RSS: ARM auth 340 MiB, registry 216.3 MiB; emulated amd64 auth 576.7 MiB, registry 282.1 MiB, delivery 625.9 MiB. These are snapshots at readiness, **not peak or full-commerce memory measurements**; amd64 emulation is not native EC2 capacity evidence.

Existing Mumbai proposal remains **m7i.2xlarge, 8 vCPU /32 GiB, amd64**. Compose configured caps total about **12.5 GiB**; at least 16 GiB available to the full verification environment is the minimum planning floor, and the reviewed 32-GiB host provides headroom. Actual startup peaks, concurrency, CPU, Kafka/outbox/DLT stability, restart recovery and memory pressure still require real acceptance. The smaller local allocation cannot supply that evidence.

Before AWS staging can be approved:

1. Resolve the exposed Caddy H2 and trust-validation gaps, Kafka vendor Java/CA/native-library gaps, and node-exporter unresolved HIGH paths, or obtain an explicit reviewed policy disposition with adequate evidence. Preserve raw findings and repeat exact-digest scans/probes after any artifact change.
2. Resolve/explicitly constrain native Snappy replay requirements and test affected actual Kafka client paths; do not infer all codec compatibility from broker LZ4 or Java-safe library tests.
3. Review/commit the final source only when authorized, record an immutable release SHA, then separately authorize ECR publication; ECR digests and scan results currently do not exist. A package-index update can change future rebuild contents despite a pinned base; release manifests must identify actual final artifacts.
4. Complete the existing [AWS approval worksheet](release-freeze-2026-10-27.md) with account ownership, **ap-south-1**, operator SSO/IAM/OIDC checks, owned domain/ACME contact, real tester IPv4 CIDRs, approved budget/on-hours/retained charges, encrypted remote state and protected SSM secrets/JWT keys. Explicitly select the restricted Mumbai template; public default HTTPS ingress is not the staging selection. No new current cost claim is made in this container task; refresh regional pricing before spend approval.
5. Obtain and review a real authenticated Terraform plan (no apply by this task), retained EBS/S3/EIP/ECR behavior, host OS/SSM patch state, only Caddy public ingress and monitoring/private services. Never place populated env, keys, credentials, state or binary plans in Git.
6. On a separately approved sufficient host, execute the prepared [AWS acceptance runbook](aws-staging-acceptance.md): registration/login→cart/checkout→reservation→signed simulated payment→PAID/commitment→assignment→pickup/finalization→delivered, plus negative/refund/authorization/SSE/notifications/reporting/outbox/DLT cases, memory peaks and public TLS.
7. Exercise data-preserving A→B→A deployment, graceful reboot/stop-start and encrypted recovery on isolated copies. The local vendor old→new→old probes do not authorize restoring old business records or prove AWS operational rollback.

## Final Git and artifact review

Working tree: **25 modified tracked files, 9 untracked individual files**, intentionally retained for review; no staged paths, no commits/pushes. Starting HEAD and branch are unchanged. No unrelated user work was discarded. All application Java/TypeScript source, historical Flyway SQL, compatibility fixture bytes and frontend lockfile remain unchanged.

| Changed paths | Scope |
|---|---|
| `README.md` | Container/dependency/policy/verification documentation change |
| `orderflow/.mvn/wrapper/maven-wrapper.properties` | Container/dependency/policy/verification documentation change |
| `orderflow/api-gateway/Dockerfile` | Container/dependency/policy/verification documentation change |
| `orderflow/auth-service/Dockerfile` | Container/dependency/policy/verification documentation change |
| `orderflow/cart-service/Dockerfile` | Container/dependency/policy/verification documentation change |
| `orderflow/delivery-service/Dockerfile` | Container/dependency/policy/verification documentation change |
| `orderflow/docker-compose.cloud.yml` | Container/dependency/policy/verification documentation change |
| `orderflow/docs/aws-staging-readiness-2026-10-27.md` | Container/dependency/policy/verification documentation change |
| `orderflow/docs/release-freeze-2026-10-27.md` | Container/dependency/policy/verification documentation change |
| `orderflow/frontend/Dockerfile` | Container/dependency/policy/verification documentation change |
| `orderflow/infrastructure/cloud/runtime.env.example` | Container/dependency/policy/verification documentation change |
| `orderflow/infrastructure/cloud/staging-mumbai.env.example` | Container/dependency/policy/verification documentation change |
| `orderflow/infrastructure/cloud/tests/prepare_full_stack.py` | Container/dependency/policy/verification documentation change |
| `orderflow/infrastructure/cloud/tests/test_runtime.py` | Container/dependency/policy/verification documentation change |
| `orderflow/inventory-service/Dockerfile` | Container/dependency/policy/verification documentation change |
| `orderflow/notification-service/Dockerfile` | Container/dependency/policy/verification documentation change |
| `orderflow/operations-service/Dockerfile` | Container/dependency/policy/verification documentation change |
| `orderflow/order-service/Dockerfile` | Container/dependency/policy/verification documentation change |
| `orderflow/payment-service/Dockerfile` | Container/dependency/policy/verification documentation change |
| `orderflow/pom.xml` | Container/dependency/policy/verification documentation change |
| `orderflow/product-service/Dockerfile` | Container/dependency/policy/verification documentation change |
| `orderflow/scripts/backup/test-isolated-restore.py` | Container/dependency/policy/verification documentation change |
| `orderflow/scripts/deploy/cloud.py` | Container/dependency/policy/verification documentation change |
| `orderflow/scripts/deploy/tests/test_cloud.py` | Container/dependency/policy/verification documentation change |
| `orderflow/service-registry/Dockerfile` | Container/dependency/policy/verification documentation change |
| `orderflow/deploy/container-applicability-2026-10-28.json` | New reviewed source/test/evidence |
| `orderflow/deploy/container-findings-after-2026-10-28.csv` | New reviewed source/test/evidence |
| `orderflow/deploy/container-findings-before-2026-10-28.csv` | New reviewed source/test/evidence |
| `orderflow/deploy/container-image-inventory-2026-10-28.json` | New reviewed source/test/evidence |
| `orderflow/deploy/container-remediation-verification-2026-10-28.json` | New reviewed source/test/evidence |
| `orderflow/docs/container-security-remediation-2026-10-28.md` | New reviewed source/test/evidence |
| `orderflow/docs/superpowers/plans/2026-10-27-container-security.md` | New reviewed source/test/evidence |
| `orderflow/infrastructure/cloud/tests/test_kafka_storage_guard.py` | New reviewed source/test/evidence |
| `orderflow/infrastructure/kafka/safe-bootstrap.sh` | New reviewed source/test/evidence |

Markdown, the two complete findings CSVs and three JSON inventories/results are intentionally generated review evidence. Docker images, full scanner logs/databases, JARs/build outputs, Node modules/cache, Terraform provider cache, browser output, populated test env files and private synthetic probe keys remain ignored or outside the repository. High-confidence secret scans and manual path review found no probable credentials proposed for versioning. The release tree is intentionally dirty because the user prohibited commits; it must not be represented as a newly frozen SHA.

An independent final source review found no actionable source defects in the container/storage guard changes; later final probes add the explicitly reported native-codec failure, not a hidden passing claim. The review does not substitute for executed tests or resolve vendor vulnerabilities. User-facing status pointers now reference this follow-up without overwriting historical freeze outcomes.

**CONTAINER SECURITY REMEDIATION INCOMPLETE**
