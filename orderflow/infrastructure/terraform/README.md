# OrderFlow AWS infrastructure

Prepared configuration only. No cloud account, DNS record or deployment is
assumed to exist. `apply` and `destroy` require explicit user authorization;
never run them as part of validation or CI. Read [AWS setup](../../docs/aws-setup.md),
[costs](../../docs/cloud-cost-management.md) and [secrets](../../docs/secrets-management.md).

## Locally validate without an AWS account

Terraform 1.16.5 and AWS provider 6.68.0 were selected from the maintained
[HashiCorp releases](https://releases.hashicorp.com/terraform/1.16.5/)
and [provider registry](https://registry.terraform.io/providers/hashicorp/aws/6.68.0).
Use the reviewed `.terraform.lock.hcl`; upgrade only after reviewing release notes.
From this directory:

```sh
terraform fmt -check -recursive
terraform init -backend=false -lockfile=readonly
terraform validate
terraform test
```

Every test uses `command = plan` and a mocked AWS provider; it does not create
resources, retrieve real secrets or validate actual account permissions.
An authenticated real AWS plan is a separate read-only readiness step.
The provider lock contains verified package hashes for `linux_amd64` CI and
`darwin_arm64` local execution.

## Configuration and proposed resources

Copy `terraform.tfvars.example` to ignored `terraform.tfvars`, replace the
required authorized account ID and review region, capacity, retention and optional
features. Do not add passwords or PEM values. A provider account allowlist and
hard preconditions reject mismatched accounts, unsupported CPU architectures,
incorrect AZs, incorrect OIDC providers, missing bootstrap files, invalid budget
recipients and incomplete Route 53 settings. The selected AMI is filtered by its
actual architecture and availability. Default AMI is Canonical Ubuntu 24.04
from its public SSM parameter; pin an approved image for repeatable infrastructure
changes after reviewing the first real plan.

Default proposal: one IPv4 VPC/public subnet/Internet Gateway/route table; an
80/443-only public security group; Ubuntu 24.04 `t3.xlarge` x86 host; encrypted
30 GiB root disk and retained encrypted 100 GiB gp3 data volume; restricted EC2
IAM/SSM channels; 12 immutable ECR repositories; separate GitHub publish/deploy
OIDC roles; a private encrypted/versioned S3 backup and release artifact bucket.
This configuration creates no remote state bucket and no runtime secret values.

Only Caddy publishes container ports 80/443. No SSH key/port is configured.
Outbound HTTP/HTTPS supports installation, AWS service APIs and ACME; DNS is
limited to the VPC CIDR. SSM and ECR authorization token APIs require wildcard
resources because these APIs cannot be constrained to resource ARNs; secret,
image, backup and deployment privileges are individually scoped.

`enable_elastic_ip`, `enable_route53`, `enable_observability` and
`enable_budget_alerts` default false. Route 53 needs an existing zone, authorized
hostname and Elastic IP; `allow_overwrite=false` refuses existing records.
An external DNS provider can point an authorized A record to the public IP.
Without an EIP, stopping/starting the host may change its public address.
Public IPv4 remains billable with either addressing method.

## Persistent storage and recovery

`/opt/orderflow` resides on the standalone encrypted data volume, including
`config/`, `releases/`, `data/` and `backups/`. Cloud-init embeds the reviewed
bootstrap shell as base64 rather than templating Bash. It passes the exact EBS
volume ID, verifies mount identity and permits formatting only a new blank disk.
Existing filesystem signatures or partitioned/unknown disks are preserved and
require operator inspection. Bootstrap waits for the asynchronously attached disk
and fails if attachment does not arrive within the bounded timeout; review
cloud-init logs and safely rerun through SSM after verifying the correct volume.

The data volume and S3 bucket have `prevent_destroy=true`; bucket/repositories
also refuse forced deletion. These protections do not replace backups or
organization-level delete protection. Never remove the resource block/state to
bypass them casually. Host replacement must stop the application, verify backup
and retained disk identity, review attachment handling and bootstrap before
starting the replacement. Root disk is replaceable; data disk is retained.
`data_snapshot_id` supports an explicitly approved *new* recovery volume; never
point a running application at an old snapshot as part of release rollback.

## State

`init -backend=false` only initializes providers locally. All `.terraform/`,
real variable files, state, crash logs and binary plans are excluded from Git.
Use the backend example only after a separately authorized state bucket has been
created/reused with versioning, encryption, public blocking and scoped access.
S3 native lock files (`use_lockfile=true`) provide locking with this Terraform
version; DynamoDB locking is deprecated. Backend credentials come from an
approved short-lived local operator role, never source files. State permissions
must cover just its key and `.tflock` object; deployment CI roles cannot access it.

ECR names default to `orderflow/<service>`, matching the release manifest and
workflow interface. Use separate approved accounts or a coordinated naming/schema
change for additional environments; do not let separate Terraform states both
manage the same repositories in one account.
