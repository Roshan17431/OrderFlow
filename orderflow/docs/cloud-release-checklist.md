# Cloud release checklist

A check is PASS only after its exact command has run successfully; otherwise record FAIL or NOT RUN with a reason. Local validation never establishes a live deployment.

- Complete Java 21 `./mvnw -B -ntp clean verify`, frontend clean install/lint/types/tests/build and fixture browser gates.
- Validate all twelve image builds, security scan/log redaction/dependencies/SBOM, monitoring, both cloud Compose files, Caddy syntax/proxy tests, Terraform fmt/init/validate and workflow actionlint.
- Run `python3 -m unittest discover -s scripts/deploy/tests -v` and the isolated backup/restore exercise.
- Review immutable twelve-image manifest, full source Git SHA, approved platform, matching account/region/environment, unchanged existing migration hashes and explicit backward compatibility. No secrets in images/artifacts/manifests.
- Confirm approved cost/budget and AWS resources; SSH closed, SSM scoped, encryption enabled, internal services private, HTTPS/domain configured, GitHub environments protected and role subjects exact.
- Provision protected runtime.env, stable JWT PEMs and backup key through SSM; auth UID/GID 10001 can read only its mounted keys. No generation on restart.
- Confirm current encrypted backups/checksums and a recent isolated restore proof; agree quiescence and rollback compatibility.
- Manually publish through the protected publication environment; record the actual workflow result, image digests and artifact run ID.
- Manually dispatch and approve deployment; record SSM command ID, terminal status/exit code and release health metadata.
- Verify actual public HTTPS redirect/certificate, Eureka/readiness, secure auth cookies/CSRF/refresh, customer/vendor/agent/admin journeys, signed payment simulator, SSE, negative authorization, backlog/DLT and monitoring.
- Run deployed Playwright only against the explicitly authorized demo/staging target. Record fixture, local real-backend and deployed results separately.
- Record proposed/actual resources, costs, limitations, missing prerequisites and final status. No live URL claim until the actual domain has been verified.

Current local preparation does not execute AWS publication, SSM, DNS, live TLS or GitHub Actions. These remain NOT RUN pending access and approval.

The executed dependency gate currently fails on seven Spring findings. The owner retained Spring Boot 3.5; no scanner exception was approved or added. Resolve this gate before publication. See [security review](cloud-security-review.md) and the exact [readiness results](phase-10-readiness.md).
