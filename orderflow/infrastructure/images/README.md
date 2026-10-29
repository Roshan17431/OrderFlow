# Maintained infrastructure image candidates

These local recipes address infrastructure vendor-image patch gaps. They are
OrderFlow-maintained derivatives, not official upstream prebuilt releases.
No recipe authorizes publication or AWS operations. Keep the Mumbai resource plan
unchanged. A candidate must pass exact-image scans and runtime/storage regressions
before deployment configuration adopts its immutable identity.

## Caddy candidate: provenance recorded before adoption

- Upstream release **v2.11.7**, signed annotated tag object
  `87c43dc4f54861ec7e54302cfb2ad3e34508b6dc`, commit
  `72dd0fb067f6d7826c7f79907670ba4a713bfe37`; GitHub reports valid tag signature.
  The Go module proxy origin independently resolves that exact tagged commit.
- The standard upstream entry point imports all standard modules and tzdata.
  No opaque binary, optional third-party plugin or source patch is added.
  [Upstream build instructions](https://caddyserver.com/docs/build) document this
  module-based build and overlaying a rebuilt executable onto the vendor image.
- Builder: official Go **1.27.3**, immutable multiarchitecture index
  `sha256:d200d2dd76b03a99b52651fd1325aff7dcd35422c35c01bac56571b0507f3deb`.
  `GOTOOLCHAIN=local` forbids an unreviewed toolchain download. `go.sum` and normal
  `sum.golang.org` verification authenticate module contents; `go mod verify` and
  `-mod=readonly` reject dependency drift. Compatible **x/net v0.62.0** includes the
  HTTP/2 fix and the later HTML-parser security patch.
- Runtime: exact vendor Caddy2.11.7 Alpine index pinned in the Dockerfile, retaining
  entry point, paths, standard modules and runtime behavior. Normal signed Alpine
  package updates refresh native packages and CA roots. Updates make future builds
  time-dependent; the final image config/manifest and package inventory must be
  recorded for each approved release. Do not treat the base index as the final digest.
- The candidate also pins compatible go-jose/v4 **4.1.6** for the newly published
CVE-2026-116587, retaining its vendor4096-bit RSA JWK bound. The linked transitive
v3 has no fix for this HIGH (v3 receives critical-only updates); it is not claimed
absent. The exact proxy must keep admin disabled and PKI/ACME-server/JWT parsing
entry points unconfigured, with an explicit regression check. Reassess any such
configuration change.

The candidate must retain HTTP/2 and pass certificate positive/negative trust,
  local HTTPS/ACME, proxy/SSE/auth/forwarding/limits/redaction tests. Disabling h2
  is not a certificate-validation remediation.

## Patch responsibility and upgrade path

The repository owner is responsible for these derivatives: monitor upstream Go,
Caddy, Kafka, exporter, Alpine and Java advisories; update pinned inputs and lockfiles;
rebuild, record provenance and binary reproducibility; rescan all severities and run
the affected compatibility tests. Never silently reuse an old custom binary after
updating a version label. Return to a supported official image when its exact
artifact includes the required fixes and passes the same checks.

Publication is a separate approval step. No custom registry manifest exists until
publication; local config IDs must not be labeled ECR manifest digests. No new paid
repository or changed AWS resource plan is implied by these recipes.

## Kafka candidate: provenance recorded before adoption

The official Apache Kafka4.3.2 image remains pinned at index
`sha256:852a0d49fc4e656b022e7f23785411802c9a088c78a7c5e39f33969de9c227a7`.
The candidate uses the same broker JARs, vendor configure/launch/default scripts,
CA entrypoint, appuser and storage guard. It installs signed vendor Corretto
`21.0.13.0.11-r0`, Alpine Brotli `1.2.1-r0` and Expat `2.9.0-r0`. The public
Corretto APK key SHA256 is
`6cfdf08be09f32ca298e2d5bd4a359ee2b275765c09b56d514624bf831eafb91`;
both current APKINDEX signatures were independently verified before preparation.
No unsigned packages or untrusted APK flag is used.

The old Temurin runtime is removed from the effective filesystem and its original
JAVA_HOME becomes a symlink to the signed Corretto package-owned runtime. This is
a normal runtime replacement; old bytes remain in immutable parent layers, not
as an alternative active runtime. Existing JVM-specific CDS archives cannot be
reused after a JDK change. The documented Java21 `-Xshare:off` disables CDS, old
archives are removed, and vendor launcher bytes remain unchanged. Heap settings
are preserved; startup/memory/persistence must be measured on the actual candidate.
This is an OrderFlow maintenance responsibility, not an Apache-endorsed new image.
Future updates must repeat CA/package/version checks and old/new/old cluster,
feature, topic, record and committed-group-offset trials. Never format existing
storage or finalize features to make a compatibility trial pass.

## node-exporter candidate: provenance recorded before adoption

Upstream **v1.12.1**, commit `6044da783597cc3b57aef7580ddcdcff58a4ee99`,
source archive SHA256
`cfec7478aa9bfd011f29df084584b7c7b68dfef85a24d28e9d6d5f88224f83c3`.
The signed annotated tag, maintainer RSA/SHA512 signature (fingerprint
`A058935287E75D832E814E46C646B23C9E3245F1`), raw commit and complete archive Git tree
were verified. Public verification metadata is included here; use maintained Git/GPG tooling
and the pinned maintainer fingerprint to recheck signatures. No custom crypto
verifier or private key is added to the repository.

Build only official source logic with fixed Go1.27.3 and compatible explicit
x/net0.62.0, x/crypto0.58.0 and x/text0.43.0 patches, with locked module checksums.
The existing vendor runtime/index and nobody user, wget healthcheck, entrypoint
and collectors remain. There is no supported apk/apt package manager in that
runtime; its unrecognized OS scan coverage must remain explicit. A fixed Go build
contains the official compromised-intermediate denylist even if the system root
bundle still includes an eMudhra root; root presence alone does not undo that
specific fix. [Go security incident response](https://go.dev/blog/emudhra-incident).

Metrics/textfile/collector probes are required. Docker Desktop cannot establish
all native-host mount propagation or host-network collector correctness. Keep
those limits explicit; private monitoring ports and pid:host alone prove neither
security inapplicability nor host NIC completeness. Do not change the Mumbai host
network plan or remove monitoring to obtain a clean scan.

## Grafana draft: NOT BUILT OR APPROVED

Fresh data adds CVE-2026-116587 to the vendor12.4.14 Go backend. Persisted SSO
settings can override disabled environment defaults, so configuration alone is
not treated as conclusive remediation. The maintained candidate uses exact
Grafana12.4.14 source commit `3c98af3ba0773ad12a8a54b1faec7dc62d8d6725`;
GitHub verifies its commit signature. The annotated release tag is unsigned.
All21,753 archive blobs match that commit's complete Git tree; the locally pinned
archive SHA256 is `f3649301d9b20d046b8d30fdd350df8c7fb9a8ad7c56e9bf3811008d2ae3a18f`.
It is a locally verified checksum, not a vendor-published checksum.

The minimal patch changes only root go.mod's go-jose/v4 4.1.4→4.1.6 and adds its
two authenticated SumDB sums, preserving all existing sums and workspace modules.
The fixed stable Go1.27.3 builder uses upstream `make build-go` with OSS tags,
CGO disabled as in the vendor executable, readonly locks and bounded Go compilation.
Committed themes/Wire/embed sources are retained. No frontend build or application
feature change is involved. Only the affected combined grafana executable replaces
its counterpart in the exact vendor runtime; frontend, plugins, other binaries,
entrypoint, UID and persistence paths remain. All must still be scanned/tested.

This is an OrderFlow-maintained build, not a Grafana Labs release. Future maintainers
must monitor vendor fixes, retire this recipe when a verified official artifact
contains the patch, and repeat local-admin/assets/Prometheus and SQLite old/new/old
checks. Do not increase the vendor4096-bit JWK RSA limit to evade the fix. Existing
real SSO/data must never be reset to make a test pass. Build/runtime/scan success
must be established before this candidate is approved.

The draft did not produce an image: the cold build exceeded 30 minutes; two
subsequent link attempts were canceled. The first constrained linker reached
about1.9GiB RSS and540MiB swap without an OOM kill. The final draft provides a
2.4GiB Go soft budget and stripped production link flags; these are build settings,
not application runtime heap reductions. A larger isolated build environment is
needed to establish this artifact, exact-image scan and persistence regressions.
Do not configure or publish this unverified candidate.

Official Grafana13.3.0 declares Go1.27.3/go-jose4.1.6 in its backend. Its exact
amd64 image `sha256:6e5d306338670f381a942f1c50d3c266bf71531e5ddd9ee4fc1a133dac5ce63d`
was evaluated as a vendor alternative, not adopted. A full same-DB scan found181
raw flags (7CRITICAL/39HIGH), largely in separately bundled datasource plugins
built with older toolchains. The fixed main backend does not patch those binaries.
Their applicability and a major upgrade require review; do not use the backend's
go.mod as proof that the whole vendor image is remediated. Keep the selected
12.4.14 HIGH finding open until a complete supported artifact is verified.
