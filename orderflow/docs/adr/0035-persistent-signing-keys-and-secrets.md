# ADR 0035: Persistent signing keys and scoped runtime secrets

Status: accepted.

Store runtime secret values in operator-provisioned SSM SecureString parameters outside Terraform. Retrieve only the deployment-scoped `runtime/` path with the instance role, using bounded calls and no plaintext output. Protected host files are `/opt/orderflow/config/runtime.env`, `jwt-private.pem`, `jwt-public.pem` and `backup.key` on encrypted persistent EBS. Root-only parent directories prevent unrelated host users traversing to the keys. Auth mounts only the two PEM files read-only; their file ownership permits the verified non-root auth UID to read them.

The cloud auth process disables ephemeral keys and requires a stable key ID plus matching RSA PEM pair. Ordinary deployment never regenerates keys. Changing an image or restarting auth preserves token verification identity. Rotation requires planned JWK overlap, access-token expiry and refresh-token/revocation handling; replacing a single key pair is a disruptive revocation operation, not transparent rotation.

Database owners and Redis/payment/Grafana credentials have no development fallback in the effective cloud topology. The PostgreSQL administrator is separate from all eight application owners. Environment injection exposes values to privileged Docker administrators; IAM, SSM and host root are trusted. No application secret enters browser/public build variables, Terraform values, release manifests or image layers.

Encrypted EBS is not a backup. Preserve SSM secret versions/keys and restricted encrypted off-host backups. Validate real image UID, PEM parsing/match and file modes before deployment. Cloud credentials and secret provisioning remain authorization prerequisites.
