# Phase 9 frontend design — 2026-10-12

The user requests a complete four-role frontend, real gateway integration, autonomous execution, and working-tree changes only. The supplied detailed request is the binding specification. It explicitly overrides skill prompts for approval pauses, new branches/worktrees, commits, and external deployment. Existing root .vscode work stays untouched.

## Architecture

Next.js App Router under orderflow/frontend, strict TypeScript, React, Tailwind, TanStack Query, React Hook Form/Zod, typed Fetch, Decimal.js with lossless JSON parsing, Lucide, Recharts, date-fns and Sonner. Node 22 LTS. Browsers call only the configured API Gateway with credentials, bearer access tokens held in memory, and the actual X-XSRF-TOKEN cookie/header flow. No second identity store. Restore with single-flight refresh; clear all private query data on logout/session switch; reject stale responses from prior sessions. Refresh once on authenticated 401, never on unrelated failures. Mutations have no automatic retry. Stable idempotency keys survive uncertain retry within the same form attempt. Bounded polling and HTTP deadlines avoid infinite loaders.

## Design

Warm neutral backgrounds, white cards, forest/olive primary accent, dark dashboard sidebar, sans-serif body and restrained serif storefront headlines. Responsive grids/tables, clear typography, semantic controls, focus rings and reduced motion. Local gradient/icon product placeholders communicate unavailable imagery. Reusable primitives include Button/Input/Select/Card/PageHeader/StatusBadge/ErrorState/EmptyState/Pagination/ConfirmDialog/FormField. Dialog uses native dialog focus containment and restoration. No fabricated catalogue, sales or delivery location.

## Feature boundaries

Customer: public home/catalog/product; real login/register/OAuth exchange; cart validation; address checkout; order list/details/history/cancel/payment/tracking; notifications/preferences/account.
Vendor: own paginated products, create/edit/status changes; inventory product lookup, stock adjustment and history. Dashboard product totals are backed by paginated API counts, with page-scoped status counts labelled accordingly.
Agent: actual profile/availability, assigned list/detail, allowed lifecycle actions and safe failure codes. Current delivery DTO masks street/address instructions; document the exact gap or add only an ownership-protected additive agent-specific address endpoint if essential.
Admin: category mutations and supported category reads, orders, payments/refunds, delivery lookup by order/id/manual assignment/history, operations overview/reports/incidents/jobs. General agent list and general delivery list are absent; require real identifiers or derive links from authorized orders. No arbitrary status edits or dangerous scheduler trigger.

## External boundaries

OAuth buttons enabled only by configured provider flags. Gateway needs OAuth authorization/callback route forwarding if enabled; frontend exchanges only backend one-use code and removes it from history. No live provider claim without credentials. Simulator completion is trusted CLI tooling, never a frontend route or browser secret. Email/SMS explicitly simulated. Notification SSE uses authenticated Fetch streaming, bounded reconnect, deduplication, teardown and REST fallback. No token in URL/storage/logs.

## Verification

Vitest/RTL/MSW test contract shapes, auth refresh races, logout, safe redirects, money, SSE parsing, forms, state transitions, navigation, and errors. Fixture Playwright checks responsive pages and negative flows; separately labelled real-backend browser suite creates isolated accounts/products, confirms payment using trusted tooling and completes agent delivery. Run npm ci/lint/typecheck/tests/build, both browser suites, fresh Java21 clean verify, Compose config and frontend Docker image/start. Capture truthful pass/fail/skip totals, current Git status and limits in phase9-verification.md. The implementation phase prohibited source commits and pushes; the user's subsequent 12 October 2026 instruction authorizes GitHub publication with at most 15 new commits.
