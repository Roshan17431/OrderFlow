# OAuth2 login

OAuth2 client support is loaded only with the `oauth2` Spring profile. Normal
startup and CI need no provider credentials. The profile defines Google
`openid/profile/email` and GitHub `read:user/user:email` scopes and uses Spring
Security's authorization-request state protection.

Google identity uses `sub` and a verified email. GitHub identity uses the
provider user ID and explicitly queries the email API for a primary verified
email. OrderFlow rejects absent or unverified email; it never fabricates one.

An existing provider link wins. Otherwise a verified, normalized email may link
to the matching eligible active OrderFlow user without replacing their password.
If none exists, a passwordless `CUSTOMER` is created and the provider account is
persisted. Conflicting or disabled identities fail generically.

The callback never creates application tokens in a URL. It stores a hash-indexed
one-time exchange code in Redis for 60 seconds and redirects with only `code`.
`POST /api/auth/oauth2/exchange`, protected by CSRF, atomically consumes the
code and returns the normal access-token/refresh-cookie pair. Reuse and expiry
return a generic authentication failure.

Provider end-to-end behavior requires real credentials and registered redirect
URIs. Automated tests mock provider identity rules and never contact live
providers.
