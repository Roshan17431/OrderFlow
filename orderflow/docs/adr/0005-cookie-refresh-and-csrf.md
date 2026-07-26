# ADR 0005: HttpOnly refresh cookie and CSRF

Status: accepted

The refresh credential is delivered in an HttpOnly cookie so browser JavaScript
cannot read it after an XSS flaw. HttpOnly does not prevent a browser from
automatically sending the cookie, so unsafe authentication actions still need
CSRF protection.

OrderFlow issues a separate readable `XSRF-TOKEN`; the SPA echoes its value in
`X-XSRF-TOKEN`. Gateway and auth service validate it. SameSite is configurable
and defaults to Strict locally. Production behind HTTPS must enable Secure.
SameSite=None is rejected unless Secure is enabled.

Local HTTP intentionally uses `Secure=false` so the Compose smoke flow works.
That exception is documented and must not be copied to production.
