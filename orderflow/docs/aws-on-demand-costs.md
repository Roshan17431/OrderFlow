# AWS on-demand cost proposal — 2026-10-15

No AWS account or region has been approved and no resources have been provisioned. Account, owned domain, recipient, approved budget and actual bill are **UNKNOWN**. This worksheet evaluates the existing Terraform default **us-east-1**, Ubuntu Linux x86_64, t3.xlarge (4 vCPU/16 GiB), for40 or80 running hours/month. It is an estimate, not an AWS bill or a fixed-price promise. Reprice the exact approved region before apply; do not reuse these rates for Mumbai or another region.

## Rates and assumptions

| Item | Planning quantity / rate | Treatment |
|---|---|---|
| EC2 t3.xlarge Linux, us-east-1 | $0.1670/running hour | Current [AWS T3 table](https://aws.amazon.com/ec2/instance-types/t3/) checked2026-10-15. Its footnote identifies Linux/Unix Northern Virginia; older project worksheet used$0.1664. |
| EBS gp3 | 30 GiB root +100 GiB retained data; planning input$0.08/GB-month = $10.40/month | [AWS EBS pricing](https://aws.amazon.com/ebs/pricing/) currently illustrates$0.08. Its static example does not independently identify the region; confirm the exact us-east-1 gp3 SKU in AWS Pricing Calculator before approval. No additional provisioned IOPS/throughput assumed. |
| Stable Elastic IP | one ×$0.005/hour ×730hours = $3.65/month | [AWS public IPv4 pricing](https://aws.amazon.com/vpc/pricing/) charges both used and idle addresses. Retained EIP remains billed while EC2 is stopped. |
| Private ECR | assumed15 GB ×$0.10/GB-month = $1.50/month | [AWS ECR pricing](https://aws.amazon.com/ecr/pricing/). Actual stored image layers/retained releases determine cost;15GB is unmeasured. Twelve repositories do not imply twelve separate minimum storage fees. |
| DNS | optional allocation$0.50/month for one hosted zone | [Route53 pricing](https://aws.amazon.com/route53/pricing/). Existing Terraform reuses an approved zone; it does not create/buy a domain. Existing external DNS might add no incremental charge. Query and domain renewal charges are separate. |
| S3 backups and release archives | UNKNOWN GB, versions, requests | [S3 pricing](https://aws.amazon.com/s3/pricing/). Estimate after actual backup size is known; version retention can outlive current-object expiration. No zero-cost assumption. |
| EBS snapshots | none created by current plan | Optional snapshots add stored incrementalGB and request/feature costs under [EBS pricing](https://aws.amazon.com/ebs/pricing/); require a separate approved retention plan. |
| CloudWatch / SNS | optional; disabled by default | [CloudWatch pricing](https://aws.amazon.com/cloudwatch/pricing/). Alarm count, custom metrics, ingestion/retention and delivery matter. Prometheus/Grafana run on the host; they do not make AWS monitoring free. |
| Data transfer | UNKNOWN traffic | Internet egress, cross-region and cross-AZ transfers can cost extra. Same-region ECR→EC2 transfer pricing differs from Internet delivery. No NAT gateway, ALB, RDS or paid interface endpoints are in this single-host proposal. |
| CPU credits | Standard mode explicitly selected in Terraform | No Unlimited surplus-credit billing in this mode. Exhausted credits throttle CPU to baseline; t3.xlarge earns96credits/hour (40%/vCPU baseline). Measure startup/steady-state `CPUCreditBalance` and performance. |

If Unlimited mode is later approved, AWS currently lists Linux T3 surplus credits at$0.05/vCPU-hour. Additional billed surplus depends on earned credits and utilization; do not treat the base hourly rate as the complete price. No automatic instance resizing is implemented or authorized.

## Monthly scenarios

The following **conditional subtotal** uses the EBS planning input above,15GB ECR, a retained EIP for730hours and$0.50 DNS allocation. Fixed subtotal while stopped = **$16.05/month**. Without the DNS allocation it is$15.55. These are not complete account totals.

| Use | Compute | Fixed subtotal | Conditional subtotal before usage extras/tax |
|---|---:|---:|---:|
| Always stopped | $0.00 | $16.05 | $16.05 |
| 5days ×8hours =40hours | $6.68 | $16.05 | **$22.73** |
| 10days ×8hours =80hours | $13.36 | $16.05 | **$29.41** |

Add S3 storage/requests/versioned archives, any snapshots, DNS queries/renewal, monitoring, egress, optional customer-managed KMS charges and tax. Month length changes the IPv4 amount. Actual startup, health polling and graceful shutdown consume additional time. Starting and stopping have no separate operation fee; EC2 Linux billing has a60second minimum per start, then per-second running charges. Stopped EBS/EIP/ECR/S3 resources continue charging. See [EC2 lifecycle billing](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-lifecycle.html).

A dynamic auto-assigned public IPv4 avoids retaining that address while stopped, but changes on stop/start. Updating an owned DNS record each session needs explicit authority, bounded propagation checks and TLS validation. The simplest proposed public-demo option is one stable EIP with an existing owned hostname. **EIP allocation and DNS changes remain disabled until approved.**

## Cost protection and bill review

The host countdown defaults to8hours from each boot. It is independent of application success and does not schedule daily starts. It is an operational guard, not an AWS spending cap: failed bootstrap, lost IAM/network access needed to verify STOP, a broken host timer or manual tampering can prevent shutdown. Monitor the timer and verify the instance actually reaches `stopped` after every session. Never terminate an instance or remove volumes as a routine cost control.

Proposed actual-spend alerts are$20/$40/$60 plus forecast100% of an explicitly chosen monthly budget. The existing Terraform budget supports `budget_actual_thresholds_usd=[20,40,60]`; all alerts remain disabled without an approved budget and real email. AWS Budgets normally alerts, not guarantees a hard cap, and notifications can be delayed. The example monthly budget150 in the existing tfvars template is not approval; select the actual amount before enabling it. No automated budget action, daily startup schedule or recurring cloud job was added.

After authorized provisioning:

1. Open AWS Billing→Bills; confirm account, month and region. Expand EC2 compute, EC2-Other/EBS, VPC publicIPv4, ECR, S3, Route53, CloudWatch, KMS and taxes.
2. Enable/inspect Cost Explorer (availability can be delayed), group daily unblended cost by service, region and usage type, then examine the OrderFlow project/environment tags after activating cost allocation tags.
3. Confirm actual and forecast budget email delivery; recipient confirmation may be necessary. An absent email is not evidence of low spend.
4. After each demo confirm EC2 is `stopped`, the EBS attachment is present and the EIP remains allocated intentionally. Review unattached volumes, retained image releases, backup versions and optional snapshots before approving any cleanup.
5. Compare40/80hour estimates with measured startup/shutdown overhead and actual storage. Reprice before changing region, instance type, storage or credit mode.

**Approval inputs still required:** exact account, region, owned domain/zone, EIP choice, root/data storage, budget, email, optional monitoring and retention, plus an AWS Calculator/SKU confirmation of all regional rates. No billable action is authorized by this document.
