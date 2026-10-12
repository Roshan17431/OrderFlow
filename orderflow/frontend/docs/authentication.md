# Browser authentication

The backend issues a short-lived RS256 access token and opaque rotating refresh cookie. Access tokens exist only in module memory; they never enter localStorage, sessionStorage, URLs, source fixtures for production, logs or persistent browser state. Protected API/SSE requests attach `Authorization: Bearer ...`. Session restoration posts to `/api/auth/refresh` with credentials and CSRF.

The refresh cookie stays HttpOnly and is managed solely by auth-service. The readable XSRF cookie/bootstrap token is attached as `X-XSRF-TOKEN` on unsafe methods. Browser CORS uses an explicit configured origin with credentials; gateway authorization is complemented by independent service role and ownership checks.

A single in-tab refresh promise coalesces simultaneous 401s; other status codes do not initiate refresh. Cookie-writing login/register/exchange/refresh/logout requests share a same-origin Web Lock held through full response processing. An in-tab promise queue maintains ordering on browsers without Web Locks; cross-tab serialization on those browsers is a limitation. Lock release precedes an expired-request refresh, avoiding recursive acquisition deadlock.

Each session has an epoch. Logout/account login advances it; a refresh that returns a different user ID also advances it and clears private query/mutation caches. An earlier request is rejected before it can be retried for the newly returned account. In-flight old responses cannot repopulate private cache. Logout clears only its captured epoch so a newer login cannot be wiped by a delayed earlier response. Server logout failures are reported while the local account is cleared; reconnect and sign in to retry server-wide logout if needed.

The role guard waits for restoration and mounts protected content only for an allowed account. It is an interface boundary; backend authorization remains authoritative. Return URLs must resolve to a safe same-origin pathname and are normalized to reject encoded/protocol-relative redirects. Navigation is constructed from known role routes.

OAuth buttons appear only for explicitly configured providers. Authorization and provider callback requests are forwarded to auth-service. The success page consumes the one-use backend exchange code, removes it from browser history and receives ordinary backend authentication. Real OAuth flows were not verified without provider credentials. Client secrets remain backend-only.

Protected content uses a session-epoch Fragment key. Same-role account changes remount private query observers and local form/dialog state; clearing the cache alone does not rebind every already-mounted shared-key observer. The regression was observed failing with the old user’s address still rendered and passing after the key change.
