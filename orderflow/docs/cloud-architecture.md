# Cloud architecture

OrderFlow's default cloud target is a single-server portfolio demo on AWS.
The proposed region is `us-east-1`; no account or live hostname is assumed.
The public path is browser → owned DNS hostname → Caddy HTTPS on EC2 →
Next.js or API Gateway → Eureka-discovered services. Services access PostgreSQL,
Redis and Kafka only through private Docker networks on the same host.

| Boundary | Intended configuration |
| --- | --- |
| Public endpoint | Only TCP 80 and 443; Caddy redirects HTTP and maintains ACME certificates |
| Compute | Ubuntu 24.04, x86_64 `t3.xlarge` (4 vCPU/16 GiB evaluation target); images `linux/amd64` |
| Administration | SSM Session Manager/Run Command over outbound HTTPS; no public SSH |
| Application | 11 Java services + standalone Next.js + Caddy; digest-pinned releases |
| Data | PostgreSQL, Redis AOF, single KRaft Kafka; persistent encrypted gp3 EBS |
| Persistent config | Entire `/opt/orderflow` on retained EBS, including JWT PEMs, runtime config and releases |
| Images | 12 project-specific ECR repositories, immutable release tags, push scanning |
| Secrets | Operator-created SSM SecureStrings; scoped instance role retrieval; no values in Terraform |
| Backups | Logical database archives in private encrypted/versioned S3 `backups/`; no release-triggered restore |
| Release transport | Nonsecret S3 `releases/` artifacts; separate GitHub publisher/deployer OIDC roles |
| Metrics | Private Prometheus/Grafana; optional CloudWatch CPU/status alarms and bounded logs |
| DNS | External provider or authorized opt-in Route 53 record; optional stable EIP |

Security group ingress does not expose API Gateway, frontend, database, cache,
broker, registry, Actuator or operational dashboards directly. Container port
binding and reverse-proxy routing enforce the application boundary too: a closed
AWS port alone does not justify an unsafe Compose mapping. No public Docker API
or Caddy administration endpoint is configured. Grafana administration is reached
through authorized SSM port forwarding; application ADMIN and Grafana admin
credentials are separate.

A real deployment requires authorization of account, region, instance/disk sizes,
public IPv4 costs, backup/state storage and optional integrations; valid DNS;
operator secrets; reviewed GitHub environments; and full local release gates.
Terraform defines these resources without provisioning them during verification.
See [setup](aws-setup.md), [configuration](cloud-configuration.md),
[costs](cloud-cost-management.md), [secrets](secrets-management.md),
[HTTPS](https-and-domain.md) and [backups](cloud-backup-restore.md).

## Capacity and operational limits

A 16 GiB host is a starting evaluation, not a measured production guarantee.
Eleven JVMs need bounded heap plus metaspace/direct/native allocations; Kafka,
PostgreSQL, Redis, Next.js, containers and the OS need their own reserve.
Monitor RSS, disk, JVM pressure and latency under a modest demo workload before
resizing. T3 uses `standard` CPU credits to avoid unlimited credit surcharges;
credit exhaustion can throttle sustained load. A non-burstable or ARM instance
requires a fresh capacity/cost review and matching image architecture.

The EC2 host, PostgreSQL, Redis and single Kafka broker are each single points
of failure. Docker Compose does not provide rolling deployment or high
availability; planned releases can have short downtime. EC2 status alarms cannot
guarantee application health. EBS retention does not protect from corruption,
accidental SQL writes, host compromise or availability-zone failure. Backup
checksums, isolated restore exercises, protected external signing-key copies and
measured recovery procedures remain necessary. No tested RPO/RTO or production
capacity is claimed until actually measured.

For higher reliability, separately assess managed PostgreSQL/Redis, replicated
Kafka, multiple application hosts and a load balancer. This changes costs and
operational scope; those resources are not silently created by this phase.
Payments remain simulated and no real money is charged; email/SMS defaults are
simulated. The demo still enforces authorization, CSRF, revocation, rate limits,
private data services and webhook verification.
