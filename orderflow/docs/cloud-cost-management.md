# Cloud cost management

No billable AWS resources have been created by the Phase 10 local preparation.
The default Terraform values are a concrete proposal awaiting account, region
and spending approval. They are not a verified monthly quote or a hard cap.

## Proposed recurring resources

| Item | Default proposal | Cost driver |
| --- | --- | --- |
| Compute | 1 Ubuntu 24.04 Linux `t3.xlarge`, 4 vCPU/16 GiB, `us-east-1` | On-demand running hours |
| Root disk | 30 GiB encrypted gp3 | Allocated GiB-month, retained while host exists |
| Data disk | 100 GiB encrypted gp3, retained independently | Allocated GiB-month even if EC2 stopped/deleted |
| Public address | 1 public IPv4; optional EIP instead of ephemeral address | Address-hours; idle EIPs also charged |
| Images | 12 immutable ECR repositories | Actual stored GB-month and applicable transfer/scanning |
| Backups/artifacts | Private versioned S3 bucket | Current and noncurrent bytes, PUT/GET/LIST requests |
| State | Separate approved S3 backend | State versions/requests; native lock objects |
| Data transfer | Browsers and image/backup traffic | Actual outbound transfer and eligible account-wide allowances |
| DNS | Optional existing Route 53 zone/record or external provider | Hosted zone, queries, domain renewal separately |
| Monitoring | Optional 2 CloudWatch alarms, 14-day logs; private Prometheus/Grafana | Alarms, custom metrics, ingested/stored logs, SNS delivery |
| Secrets | Operator-managed SSM SecureStrings | Parameter tier, API usage and optional CMK charges |

The VPC/public-subnet/Internet Gateway design uses no NAT gateway, load balancer,
managed database or cross-AZ service tier. The total still depends on account
pricing, actual usage, region and optional services. Do not assume Free Tier,
promotional credits or tax treatment. The `monthly_budget_usd=150` default is
only an operator-editable notification threshold; it does not imply the proposal
fits under that amount.

## Official price sources and estimate worksheet

Recheck rates immediately before approval. Official sources inspected for this
preparation on 2026-10-12:

- [EC2 On-Demand pricing](https://aws.amazon.com/ec2/pricing/on-demand/) — select Linux/shared tenancy, region and exact instance SKU.
- [EBS pricing](https://aws.amazon.com/ebs/pricing/) — gp3 capacity and provisioned throughput/IOPS beyond baseline; snapshots separately.
- [VPC public IPv4 pricing](https://aws.amazon.com/vpc/pricing/) — in-use and idle addresses are charged; EIP is not a free static address.
- [ECR pricing](https://aws.amazon.com/ecr/pricing/) — private image storage and applicable data transfer/scanning.
- [S3 pricing](https://aws.amazon.com/s3/pricing/) — include noncurrent versions and request counts.
- [CloudWatch pricing](https://aws.amazon.com/cloudwatch/pricing/) and [SNS pricing](https://aws.amazon.com/sns/pricing/) — optional metrics/logging/alarm delivery.
- [Route 53 pricing](https://aws.amazon.com/route53/pricing/), [Systems Manager pricing](https://aws.amazon.com/systems-manager/pricing/) and [KMS pricing](https://aws.amazon.com/kms/pricing/) — selected optional features.
- [AWS Pricing Calculator](https://calculator.aws/) — save the exact region/usage assumptions and dated estimate before authorizing resources.

For a 730-hour planning month, use:

`730 × EC2 hourly rate + 130 × gp3 GiB-month rate + 730 × IPv4 hourly rate + ECR stored GB × rate + S3 current/noncurrent storage + requests + transfer + selected DNS/monitoring/KMS fees + taxes`.

This is a worksheet, not a verified total. Record the pricing date, selected
region, official SKU/rate, monthly running hours, retained image GB, measured
backup size, noncurrent version growth, expected traffic and all optional
features. Keep unverified lines marked **NOT VERIFIED**; do not fill them from
memory or third-party estimates. Parent deployment readiness report may include
independently retrieved official regional rates when available.

## Illustrative planning subtotal

The following uses published AWS reference examples inspected on 2026-10-12,
**not a live verified Linux/Ubuntu SKU quote**. Recheck the selected region in
AWS Pricing Calculator or the authenticated Price List API before approval.

| Assumption | Published/reference rate | 730-hour/full-month illustration |
| --- | --- | --- |
| t3.xlarge | $0.1664/hour in AWS's us-east-1 [instance-selection reference table](https://docs.aws.amazon.com/prescriptive-guidance/latest/optimize-costs-microsoft-workloads/right-size-selection.html) (a Windows-workload guide; actual Linux SKU not independently queried) | $121.47 |
| 130 GiB gp3 total | $0.08/GB-month from the [EBS pricing example](https://aws.amazon.com/ebs/pricing/); selected-region rate still needs confirmation | $10.40 |
| One public IPv4 | $0.005/hour from [VPC pricing](https://aws.amazon.com/vpc/pricing/) | $3.65 |
| ECR: assumed 15 GB retained images | $0.10/GB-month from [ECR pricing example](https://aws.amazon.com/ecr/pricing/); image storage is an unmeasured assumption | $1.50 |
| **Illustrative subtotal** | Excludes additional services below | **$137.02/month** |

S3/state/backup versions and requests, snapshots, optional KMS/alarms/logs/SNS,
DNS/domain renewal, transfer and taxes remain **NOT VERIFIED** and are additional.
No approved spending budget exists. This subtotal is neither a spending cap nor
a complete recurring quote; the workload and retained image sizes have not been
measured. Resources actually created: **0**.

## Capacity and spend controls

Eleven Java services plus Next.js/PostgreSQL/Redis/Kafka/Caddy need realistic
memory reserves; optional Prometheus/Grafana adds usage. The 16 GiB class is an
evaluation starting point. Measure RSS, memory/GC and response latency before
selecting a smaller VM. JVM heap percentages exclude native overhead. Set bounded
container/JVM limits and retain OS/cache reserve; OOM restarts can undermine
outbox processing and readiness.

T3 is configured with `standard` credits to avoid unlimited-mode CPU surcharges.
Under sustained demand it can exhaust credits and throttle; measure credits and
latency, then evaluate a non-burstable alternative with fresh explicit approval.
ARM targets require matching image builds and capacity comparison, not a blind
instance-type substitution.

Optional AWS Budgets sends 80% actual/100% forecasted account-wide cost alerts.
It is not a hard spending cap and can lag usage. Confirm authorized recipients
and monitor account-wide billing. CloudWatch alarms/log ingestion and external
SNS delivery remain opt-in. Logs have bounded retention; secret-bearing log
streams must not be enabled.

ECR lifecycle expires only untagged build debris after 14 days. Release-tagged
images are retained to protect rollback; manually prune only after checking all
active/previous release digests and explicit authorization. Image storage grows
until safely pruned. S3 `backups/` expires current objects after the configured
30 days and noncurrent versions after another 90 days; storage therefore
outlives visible current objects. `releases/` artifacts are retained without
automatic expiry. Review retention before approving the first apply.

Stopping EC2 saves compute hours, but EBS, retained snapshots, ECR/S3, domains,
EIPs and some monitoring continue to cost money. A stopped host with ephemeral
IPv4 may get a new address on restart, affecting DNS/certificates/OAuth.
Do not use automatic `terraform destroy` or Docker volume deletion as a spend
control. Inventory/reconcile resources and obtain explicit deletion approval
while protecting data and keys.
