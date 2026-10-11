# API versioning policy

Existing endpoints remain under `/api/**`; Phase 8 does not rename them merely to add a version prefix. The current representation is treated as v1 by policy.

Compatible changes include adding an optional response field, adding an optional request field with a server default, and adding an endpoint. Existing field names, types, nullability, units, precision, status codes, authorization rules, idempotency behavior, and `ApiError` shape must remain compatible. Deprecations require release notes and an announced removal window.

A change that removes or renames a field, makes optional input required, changes semantics/types, changes ownership or authorization, or makes an existing success/failure status incompatible requires `/api/v2` (or an explicitly negotiated media type). Run v1 and v2 in parallel while callers migrate; do not silently reinterpret v1.

Consumer-side tests define the product-resolution, cart, product-management-access, gateway-route, and standard error contracts. A producer change is complete only when those consumers pass and correlation/authorization headers continue to propagate.
