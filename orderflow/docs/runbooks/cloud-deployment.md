# Cloud deployment runbook

Prerequisites: explicit account/region/environment/domain, approved existing infrastructure, reviewed cost/budget, correct EC2 image platform, private SSM parameters and persistent key files, valid DNS/TLS prerequisites, required GitHub environment reviewers, exact release artifact, authorized journey test credentials. No infrastructure is created by these commands.

Prefer manual `deploy.yml` dispatch using a successful immutable publication run ID. For a new blank cloud-demo installation, explicitly select the `initial_demo_seed` checkbox only after reviewing the fixed synthetic role accounts and the trusted-host role grant; it defaults to false and remains behind the protected manual deployment approval. The workflow transports the release privately and waits for SSM. For an already authorized SSM session on the host, use the unpacked artifact directory:

```bash
cd /opt/orderflow/releases/RELEASE_DIRECTORY
args=(--account "$APPROVED_ACCOUNT_ID" --region "$APPROVED_REGION" \
  --environment cloud-demo --domain "$APPROVED_DOMAIN" --release "$RELEASE_VERSION" \
  --manifest "$PWD/deploy/release-manifest.json" --repo "$PWD" \
  --runtime-env /opt/orderflow/config/runtime.env --state-dir /opt/orderflow/releases/state \
  --verification-script "$PWD/scripts/cloud/verify-journey.sh")
./scripts/deploy/fetch-secrets.sh "${args[@]}"
./scripts/deploy/preflight.sh "${args[@]}"
./scripts/deploy/deploy-release.sh "${args[@]}"
```

Preflight validates STS account identity, explicit target and all image digests, exact migration hashes, protected secrets/key permissions, disk space, merged Compose and the executable journey hook. Deployment pulls images, creates private database/bootstrap services, captures/verifies all eight databases, records candidate schema metadata, updates the release and waits with a fixed deadline. Observability is included through the `observability` profile.

Release success requires actual container health, ten expected Eureka registrations, public HTTPS homepage/catalog/JWKS and `scripts/cloud/verify-journey.sh` for actual authorization/customer workflow/operations checks. The protected `cloud-accounts.json` contains authorized VENDOR/DELIVERY_AGENT/ADMIN demo account credentials. The hook receives explicit target variables and an inspected private Prometheus URL. Hook inputs are private runtime environment values; do not place credentials in workflow arguments. Deployed browser tests must run separately against authorized demo infrastructure. The script never labels mocks/local tests as deployed verification.

On failure, inspect the specific SSM invocation, suppressed-output-safe host logs, candidate digest set, database migration state, metrics and `schema-release.json`. Last healthy metadata remains unchanged. No automatic rollback/restore occurs. A timeout may leave the remote command active: check SSM before another dispatch. Use the rollback runbook only after compatibility review.

## First installation and seed recovery

Provide the protected fake `cloud-accounts.json` before the first deployment. `APP_BOOTSTRAP_ADMIN_ENABLED=false` remains enforced. A blank database has no privileged fixture users until the owner explicitly authorizes initial seeding. With `initial_demo_seed=true`, deployment first starts the services and checks basic readiness, then calls the existing guarded seed with `--initial-only` and the exact approved target/runtime/digest environment. It registers new synthetic `@example.test` accounts, grants only fixed VENDOR/DELIVERY_AGENT/ADMIN roles on the trusted host, and creates the bounded demo fixtures. Existing-account registration conflicts are rejected before role grant. The complete journey runs afterward; only its success marks the release healthy.

The equivalent already-authorized host command adds `--initial-demo-seed` to `deploy-release.sh`. The option refuses any environment except cloud-demo, refuses an existing healthy release, and refuses a recorded completed initial seed. Leave it disabled for ordinary updates, rollbacks and a retry after successful seeding.

A failed seed never advances healthy release metadata or resets/deletes records. Partial registration/fixture work remains visible. Initial-only retries may therefore encounter 409 and stop. Inspect the exact synthetic identities and preserved release, then obtain separate authorization to resume using the normal guarded `scripts/cloud/seed-demo.sh` with explicit `ORDERFLOW_DEMO_SEED=true` and `ORDERFLOW_TRUSTED_HOST_ROLE_GRANT=true`, the approved target/account file/runtime file, and that release's `IMAGE_*` environment. Normal recovery must be an operator decision, never an automatic account promotion. After an authorized seed resume, rerun deployment with the default seed option disabled; full verification still gates healthy metadata. Never delete accounts, reset passwords, overwrite stock or enable a default administrator to bypass the failure.
