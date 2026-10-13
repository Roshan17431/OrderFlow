# Cloud secret management

Runtime values belong in SSM Parameter Store SecureStrings provisioned by an
authorized operator, outside Terraform. Terraform defines only the instance IAM
read permissions and optional existing KMS key reference. It never creates
`aws_ssm_parameter` values, reads secret-value data sources or outputs passwords,
PEMs or tokens. Marking a Terraform variable `sensitive` would only hide display;
it would still put ordinary resource values in state, so it is not sufficient.

## Operator provisioning and IAM

The default exact hierarchy is `/orderflow/cloud-demo/runtime/`. Other
project/environment values get their own separate subtree; do not put unrelated
parameters beneath a runtime prefix whose role has recursive read access.
Only the instance role receives `GetParameter`, `GetParameters` and
`GetParametersByPath` there. Publisher/deployer roles cannot fetch the hierarchy,
write values or change IAM. Short-lived operator credentials are required to
provision/update the parameters. The operator IAM grant must be restricted to
the approved parameter prefix and encryption key, with no access keys in GitHub.

The deployment fetcher expects these five **SecureString** parameters:

| Parameter suffix | Protected contents |
| --- | --- |
| `runtime.env` | Complete multiline runtime environment based on `infrastructure/cloud/runtime.env.example` |
| `jwt-private.pem` | Persistent RS256 PKCS#8 private signing key |
| `jwt-public.pem` | Matching public key |
| `backup.key` | Persistent backup encryption passphrase/key, kept separate from archives |
| `cloud-accounts.json` | Authorized synthetic VENDOR/DELIVERY_AGENT/ADMIN journey accounts, mode0600 on host |

Create random values for all PostgreSQL owner/application passwords, Redis,
webhook verification, Grafana administration and enabled OAuth provider secrets.
Use fake demo data and separate credentials per service database. Browser
`NEXT_PUBLIC_*` settings must never contain any secret; same-origin browser API
routes do not need internal service hostnames. OAuth secrets are needed only when
that provider is explicitly enabled; inspect actual callback settings before use.

Provision using the AWS console's SecureString form or an approved secrets tool.
If using CLI, stage content in a temporary protected file (`umask 077`, mode 0600)
and provide `--value file://<absolute-protected-file>` rather than putting the value
on the command line. Run only after account/region/secret-write authorization;
do not print command responses, decrypted values, environment dumps or filenames
with secret contents in CI. Avoid shell tracing and command substitution that
would log values. Verify metadata/type and IAM without displaying values.
Discard staging files according to the workstation's security policy; retain an
approved encrypted recovery copy of JWT and backup keys outside the EC2 host.

Standard SecureString size is limited; the multiline environment must fit the
current [Parameter Store limits](https://docs.aws.amazon.com/systems-manager/latest/userguide/parameter-store-quotas.html).
Do not silently upgrade to charged advanced parameters. If the runtime file
exceeds the allowed standard size, get explicit cost authorization for advanced
tier or refactor the retrieval schema after updating both fetcher and docs.

SSM's AWS-managed key is supported; when using an existing customer-managed key,
set `runtime_kms_key_arn`. The instance receives `kms:Decrypt` only for that key,
through the regional SSM service and matching parameter encryption context.
The CMK key policy also has to permit the role; an IAM policy alone cannot grant
access when key policy denies it. Terraform does not create or export key
material. See [AWS SecureString encryption](https://docs.aws.amazon.com/systems-manager/latest/userguide/secure-string-parameter-kms-encryption.html).

## Runtime files and persistence

Instance-local retrieval suppresses secret values and writes into persistent
`/opt/orderflow/config`. Directory mode is 0700 and environment and backup files are 0600; JWT PEMs are 0400 with the intended auth-container owner. The deployment
fetcher rejects changes to existing signing/backup keys unless an operator
performs a separately authorized rotation. Inspect the script's documented
arguments instead of echoing its results. Application secrets are injected at
runtime and never baked into image layers or nonsecret release manifests.

JWT PEMs must be readable by the actual non-root auth container through the
intended mount only. The bootstrap/cloud Compose configuration arranges intended
UID ownership and mount permissions; do not make private keys world-readable to
fix a container startup failure. Keep the host parent directory restricted.
Do not put full runtime environment into PostgreSQL backup archives, S3 release
bundles, smoke test screenshots or support logs. Archive backup encryption keys
and signing keys independently from the backup data.

Any process with host root or Docker daemon access can obtain host runtime
secrets. The GitHub deployment environment is therefore privileged even though
its AWS IAM role cannot directly read SSM. Limit required reviewers, branch
access and retained workflow artifacts. A protected local file reduces exposure,
but does not protect against a compromised host/container runtime.

## Rotation and recovery

Database and Redis credential changes require coordinated server/client updates,
readiness verification and rollback planning; changing a container environment
alone does not rotate an existing PostgreSQL user's password. Webhook key rotation
must preserve safe handling of in-flight simulator events. Restrict OAuth changes
to the enabled provider and actual registered callback origins.

The current auth key implementation exposes one active signing key. Do not
claim automatic overlapping JWKS key rotation: plan a controlled invalidation
window or implement/test multiple verification keys before a seamless rotation.
Preserve the old public key for any required token-lifetime overlap; never replace
PEMs casually during a release. Verify new private/public matching, issuer and
JWT validation in a staging exercise before public use. If a key is compromised,
revoke sessions as required and treat rotation as a security incident.

Recreation of EC2 must reattach the retained EBS volume or restore the same
approved secret values. Never generate signing keys on each restart or restore
an old runtime database automatically. [Disaster recovery](cloud-disaster-recovery.md)
is a separate explicitly authorized operation.

Provision `/orderflow/cloud-demo/runtime/cloud-accounts.json` as a fifth SecureString parameter. It supplies private demo VENDOR, DELIVERY_AGENT and ADMIN email/password accounts to the deployed verification hook; the host file is 0600 and never mounted into the frontend. Configure authorized synthetic role accounts separately; the tool never promotes random users.

The journey account JSON must contain exactly `VENDOR`, `DELIVERY_AGENT`, `ADMIN`. Each has only `email` and `password`: distinct lowercase fake `@example.test` addresses and passwords of 16–128 characters. The fetcher validates this policy before installing configuration; no real customer credentials are accepted.
