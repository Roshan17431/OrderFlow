# ADR 0034: Environment-scoped GitHub OIDC

Status: Accepted for prepared cloud deployment.

GitHub workflows obtain temporary AWS credentials via OIDC rather than permanent access keys. Publish and deploy use separate least-privilege roles, exact `Roshan17431/OrderFlow` environment subjects and audience `sts.amazonaws.com`. GitHub environment reviewers and deployment branch restrictions complement AWS trust. Read-only CI does not request OIDC permissions. Deployment uses SSM and a private SSE-encrypted release artifact rather than permanent SSH keys.

Consequences: account/region/role variables and environment protection must be configured by an authorized owner. Local workflow validation cannot prove remote authentication. Missing configuration fails closed.
