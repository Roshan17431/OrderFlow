# OrderFlow Phase 9 Implementation Plan

> **For agentic workers:** Implementation was executed in the current working tree. Independent role feature tasks used superpowers:dispatching-parallel-agents after the shared foundation was ready. The implementation phase prohibited commits, branches, pushes and data destruction. The user's subsequent 12 October 2026 instruction authorizes committing and pushing this completed work to the existing GitHub repository with at most 15 new commits; preserve user data and unrelated files.

**Goal:** Build the complete real-backend four-role web interface requested in Phase 9.
**Architecture:** Direct typed gateway calls, memory-only access tokens, backend refresh cookies/CSRF, feature-based pages with shared accessible UI and query cache. Backend state remains authoritative.
**Tech Stack:** Next.js/React/TypeScript strict/Tailwind/TanStack Query/RHF/Zod/Vitest/RTL/MSW/Playwright.
**Spec:** phase9-design.md and the user's supplied Phase 9 request.

## Global Constraints

- Gateway http://localhost:8080; frontend http://localhost:3000; configurable public API URL.
- No tokens in persistent storage/query URLs, no browser webhook secrets, no fake production responses.
- Preserve user files/data. Implementation stayed in the existing working tree; subsequent GitHub publication follows the user's authorization and 15-commit limit.
- All asynchronous queries/mutations have real failure states; retries are bounded and mutations are never implicitly retried.

## Review Focus

- Concurrent stale 401s: one refresh, no logout resurrection; test transport generation/single-flight.
- Uncertain checkout/refund: reuse key and identical body; test attempt fingerprint/key retention.
- Mixed pagination shapes: normalize items/content at typed boundary; contract tests for every service.
- Partial refunds/terminal delivery: financial and fulfillment separate, decimal exactness; form/state tests.
- SSE loss and user changes: cleanup/deduplication/REST fallback/private cache cleared; stream/auth tests.

### Task 1: Contract matrix and frontend foundation
Files: frontend/docs/backend-api-mapping.md; package/config/app/providers; types/contracts.ts; components/ui; lib/api/http.ts and index.ts.
Interfaces: request<T>(path, {method,body,auth,signal,idempotencyKey}); Page<T> normalized items; typed service clients; useAuth {user,status,login,register,logout}; UI props documented in shared exports.
- [x] Map controllers, DTOs, enums/security and exact errors.
- [x] Write transport/money/safe-redirect tests; observe failure before implementation.
- [x] Install compatible dependencies, implement shared client/session/UI and run targeted tests.

### Task 2: Storefront and customer journeys
Files: features/catalog, cart, checkout, orders, payments; public/customer App Router pages; tests/customer.
Consumes Task 1 clients/types/guard/UI. Produces rendered home/products/cart/checkout/orders/tracking pages.
- [x] Write meaningful catalog/cart/checkout/payment component and contract tests.
- [x] Implement URL filters, pagination, details, real cart mutations/validation, shipping form, stable keys, bounded order/payment polling, history/cancel/tracking.
- [x] Run feature tests/typecheck; document backend gaps.

### Task 3: Vendor and agent experiences
Files: features/vendor, inventory, delivery-agent; corresponding routes; tests/vendor-agent.
Consumes Task 1 types and clients. Produces actual forms/actions with query invalidation and role restriction.
- [x] Test product/inventory form validation and agent transitions.
- [x] Implement product listing/create/edit/status, inventory quantities/adjustments/history, profile/availability/list/detail/lifecycle.
- [x] Verify owned data only; run feature tests/typecheck.

### Task 4: Admin experiences
Files: features/admin; admin routes; tests/admin.
Consumes Task 1 normalized clients/UI. Produces category/order/payment/refund/delivery and operations pages.
- [x] Test decimal refund limits, supported status/actions and API shapes.
- [x] Implement bounded lists, detail links, stable refund keys, assignment lookup, reports charts/incident resolution/jobs.
- [x] Run feature tests/typecheck; exact gaps documented.

### Task 5: Auth, notifications, integrations
Files: features/auth, notifications, settings; lib/sse; app login/register/oauth/account; backend minimal compatible changes only if required.
- [x] Test session restore/login/logout/role guards, concurrent refresh, SSE parser/reconnect/deduplication and preferences.
- [x] Implement cookie/CSRF memory session lifecycle, safe redirects, OAuth configured-only flow, notification bell/list/read/preferences/SSE teardown and fallback.
- [x] Document trusted CLI payment webhook tool and test without browser signing secrets.

### Task 6: Container/CI/docs and browser verification
Files: frontend Dockerfile/env/README/docs/e2e; Compose, root README, CI and ignore rules.
- [x] Configure fixture Playwright and real-backend isolated account/role/browser journeys.
- [x] Run npm ci/lint/typecheck/test/build and fixture browsers; inspect desktop/mobile screenshots.
- [x] Start existing Docker services safely, run real gateway browser journey and trusted payment completion.
- [x] Build/start frontend image, verify Compose and Java21 Maven regression sequentially to avoid Docker resource pressure.
- [x] Review all changes/security and capture actual results/limits before the subsequently authorized GitHub publication.
