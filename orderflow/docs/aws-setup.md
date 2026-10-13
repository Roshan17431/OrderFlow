# AWS setup and authorization

The repository prepares infrastructure; it has not created AWS resources. The
intended defaults are `us-east-1`, Ubuntu 24.04, x86_64, `t3.xlarge`, 30 GiB root
and 100 GiB retained data disk. Review the actual account, existing resources,
capacity and [cost proposal](cloud-cost-management.md) before authorizing any
billable operation. IAM-only changes and DNS changes also need authorization.

## Prerequisites

Use AWS IAM Identity Center or another approved short-lived operator role, with
MFA and sufficient narrowly delegated infrastructure permissions. Do not use root
or permanent AWS access keys. Record the authorized 12-digit account ID, region,
instance/AZ, storage/retention, expected costs, budget and recipient preferences.
The AWS account administrator decides the operator permission boundary; the
GitHub/runtime roles defined here cannot provision Terraform infrastructure.

Read-only inventory commands, after authenticating the selected profile:

```sh
aws sts get-caller-identity --profile orderflow-operator
aws ec2 describe-instances --region us-east-1 --profile orderflow-operator \
  --filters Name=tag:Project,Values=orderflow
aws ec2 describe-volumes --region us-east-1 --profile orderflow-operator \
  --filters Name=tag:Project,Values=orderflow
aws ecr describe-repositories --region us-east-1 --profile orderflow-operator
aws iam list-open-id-connect-providers --profile orderflow-operator
aws route53 list-hosted-zones --profile orderflow-operator
```

Inspect matching resources before creating duplicates. Existing GitHub OIDC
provider ARN goes into `github_oidc_provider_arn`. Existing unmanaged cloud
resources require a separately reviewed import/reconciliation plan; do not delete
or replace them automatically. List hosted zones only if Route 53 is intended;
external DNS does not need Route 53. Inspect DNS ownership and existing records
before proposing a new hostname. Buying a domain and changing DNS are separate
explicit authorizations.

## State backend preparation

Prefer an existing approved dedicated S3 state bucket; the application backup
bucket is not the Terraform backend. If no bucket exists, ask for explicit
provisioning approval before creating a separate state bootstrap stack or using
an authorized administrator. Require S3 Block Public Access, BucketOwnerEnforced,
server-side encryption, versioning, TLS-only policy and a scoped operator role.
Native S3 lock files are supported by Terraform 1.16.5. Keep the state key
`orderflow/cloud-demo/terraform.tfstate` and its `.tflock` object together.

Grant the infrastructure operator `s3:ListBucket` for that exact prefix,
`GetObject`/`PutObject` on the state key, and
`GetObject`/`PutObject`/`DeleteObject` on the lock key only. If using SSE-KMS,
include only the backend key's required encrypt/decrypt/data-key permissions.
No state bucket permissions are granted to application or GitHub release roles.
See [HashiCorp S3 backend guidance](https://developer.hashicorp.com/terraform/language/backend/s3).

From the Git root, safe local validation:

```sh
cd orderflow/infrastructure/terraform
terraform fmt -check -recursive
terraform init -backend=false -lockfile=readonly
terraform validate
terraform test
```

After backend/account authorization, copy/replace `backend.tf.example` and
`terraform.tfvars.example` placeholders into their ignored real files. Set
`AWS_PROFILE=orderflow-operator` in the operator shell and verify STS account
identity again. Initialize the authorized backend; migration of existing local
state requires deliberate review. Then a read-only `terraform plan -out=review.tfplan`
can describe proposed changes; treat the plan as sensitive and do not commit it.
Reject unexpected account/region, replacements, deletions, widened access or
new paid features. Share the concrete resource/cost proposal for approval.
**Do not run apply/destroy until explicitly authorized.**

## GitHub OIDC and approvals

Default exact trust subjects for repository `Roshan17431/OrderFlow` are:

- Publisher: `repo:Roshan17431/OrderFlow:environment:cloud-demo-publish`.
- Deployment: `repo:Roshan17431/OrderFlow:environment:cloud-demo`.
- Both require audience `sts.amazonaws.com`; separate roles have no static keys.

Create both GitHub environments with required reviewers, prevent self-review and
allow only the reviewed `main` branch (or a separately approved protected release
branch). Disable administrator bypass where your GitHub plan supports it. GitHub
environment OIDC subjects identify the environment, so the branch restriction
must be enforced by environment protection rules and workflow triggers; it is
not encoded in the default AWS subject. Do not remove these rules merely to
make a workflow execute. Verify availability of reviewers/rules on the actual
repository plan before enabling remote release execution.

Set workflow variables from reviewed Terraform outputs: account ID, region,
`container_platform`, `ecr_registry`, ECR prefix `orderflow`, instance
ID, publisher/deployer role ARNs, `release_bucket`, domain and parameter prefix.
Never use placeholder IDs. Both environment names must match workflow job
`environment:` exactly. Publisher can push/read only these 12 repositories;
it cannot deploy. Deployer can upload `releases/*`, inspect ECR digests and run
`AWS-RunShellScript` on this exact tagged instance; it cannot read runtime
parameters, create cloud resources or push images. `SendCommand` executes as
root on that instance, so its environment approval is an administrative gate.
The bounded `GetCommandInvocation` status API needs wildcard IAM resource access
because AWS does not support resource-level scoping for that API.

## Host and first deployment

After explicitly approved provisioning, verify the encrypted volume ID,
cloud-init result, SSM agent registration and outbound service connectivity.
The official [Canonical public AMI parameter](https://documentation.ubuntu.com/aws/aws-how-to/instances/find-ubuntu-images/)
selects Ubuntu 24.04 for the configured architecture. No public SSH access or
SSH key is needed; an authorized human Session Manager role is supplied by the
account administrator separately from the CI deploy role.

```sh
aws ssm start-session --target "$ORDERFLOW_INSTANCE_ID" --region "$AWS_REGION"
```

In the approved session, inspect `cloud-init status --long`,
`systemctl status snap.amazon-ssm-agent.amazon-ssm-agent.service`,
`findmnt /opt/orderflow`, `docker version` and `docker compose version`.
Never inspect runtime secrets using an environment dump or print PEM content.
Provision [runtime SecureStrings](secrets-management.md), then follow
[CI/CD](cloud-ci-cd.md) and the [deployment runbook](runbooks/cloud-deployment.md).
Configure the authorized DNS record only after the reviewed endpoint exists.
A public HTTPS URL is verified only after DNS resolution, certificate validation
and the deployed full-stack smoke tests succeed.
