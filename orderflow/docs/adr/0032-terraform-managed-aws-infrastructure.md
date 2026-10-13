# ADR 0032: Terraform-managed AWS infrastructure

Status: Accepted for prepared configuration.

Use pinned maintained Terraform 1.16.x and AWS provider 6.68.x with a reviewed
provider checksum lockfile. Define account allowlisting, VPC/80+443 security
boundary, encrypted EC2/EBS, ECR, scoped EC2 and separate GitHub OIDC IAM,
private encrypted/versioned S3 backup/release storage, and explicit optional
DNS/monitoring/budgets. Local CI runs formatting/init without backend/validation
and mocked plan policy tests; it never applies or destroys.

Use a separately authorized encrypted/versioned S3 state backend with native
S3 lock files. Keep real variables, plans and state out of Git. Provision SSM
SecureString values outside Terraform so no secrets enter ordinary Terraform
state. Data EBS and backup S3 deletion protections prevent routine accidental
loss but do not replace backups or account policies. A real plan, cost review
and explicit authorization precede provisioning or DNS changes.
