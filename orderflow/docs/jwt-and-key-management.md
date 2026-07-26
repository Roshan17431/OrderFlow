# JWT and key management

OrderFlow signs access tokens with RSA SHA-256 (RS256). The auth service owns the
private key. Its JWK endpoint exposes only `kty`, `use`, `kid`, `alg`, `n`, and
`e`; the gateway caches this public JWK through Spring Security's Nimbus decoder.
Both services pin RS256, validate the exact issuer and timestamps, and require
`sub`, `iat`, `exp`, `jti`, `email`, `roles`, `session_id`, and
`token_version`.

## Production keys

Set:

```text
APP_JWT_ALLOW_EPHEMERAL_KEYS=false
APP_JWT_PRIVATE_KEY_LOCATION=file:/run/secrets/orderflow-jwt-private.pem
APP_JWT_PUBLIC_KEY_LOCATION=file:/run/secrets/orderflow-jwt-public.pem
APP_JWT_KEY_ID=<versioned-key-id>
APP_JWT_ISSUER=<stable-https-issuer>
```

The private key must be unencrypted PKCS#8 PEM (`BEGIN PRIVATE KEY`) and the
public key X.509 PEM (`BEGIN PUBLIC KEY`), at least RSA-2048. Protect the files
with the deployment secret store and filesystem permissions. Never put PEM
content in Git, Compose, images, logs, or Actuator output.

Local Compose sets ephemeral generation explicitly. One key pair is generated
once per process; a restart invalidates outstanding access tokens. That mode is
unsuitable for production or multiple auth replicas. Automated multi-key
rotation and old-key overlap are deferred.

See [ADR 0003](adr/0003-rsa-jwt-signing.md).
