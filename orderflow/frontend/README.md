# OrderFlow frontend

Next.js App Router interface for the actual OrderFlow gateway, with customer, vendor, delivery-agent and administrator workspaces. Backend ownership and authorization remain authoritative.

## Run locally

Use Node.js 22.12 or newer within the package engine range (Node 22 LTS is the container/CI baseline), npm, Java 21 and Docker Desktop for backend workflows.

From the Maven project directory `orderflow/`:

```sh
./scripts/dev-up.sh
cd frontend
cp .env.example .env.local
npm ci
npm run dev
```

Open http://localhost:3000. The public API default is http://localhost:8080. An unavailable gateway produces an explicit retry state; production pages contain no fixture fallback.

`NEXT_PUBLIC_API_BASE_URL` is the **browser-reachable** gateway address. `NEXT_PUBLIC_OAUTH_PROVIDERS` is an optional comma-separated list (`google`, `github`) and defaults to empty. Enable only providers configured in auth-service. Public variables are embedded at build time. Never place signing keys, webhook secrets, database passwords or OAuth client secrets in public variables.

The optional Compose profile builds a non-root standalone frontend:

```sh
docker compose --profile frontend up -d --build frontend
```

For a fresh isolated local test database while retaining previous volumes, consistently prefix backend startup, frontend startup and real tests with `COMPOSE_PROJECT_NAME=orderflow-phase9`. This project still uses the normal host ports, so run only one stack on those ports. Do not delete volumes to reset a test.

## Routes and roles

| Audience | Routes |
| --- | --- |
| Public | `/`, `/products`, `/products/[id]`, `/login`, `/register`, `/oauth2/success`, `/oauth2/failure` |
| Authenticated customer | `/cart`, `/checkout`, `/orders`, `/orders/[id]`, `/orders/[id]/tracking` |
| Any signed-in account | `/account`, `/notifications`, `/settings/notifications` |
| Vendor / permitted administrator | `/vendor`, `/vendor/products`, `/vendor/products/new`, `/vendor/products/[id]/edit`, `/vendor/inventory`, `/vendor/inventory/[productId]` |
| Delivery agent | `/delivery-agent`, `/delivery-agent/deliveries`, `/delivery-agent/deliveries/[id]` |
| Administrator | `/admin`, `/admin/categories`, `/admin/orders`, `/admin/orders/[id]`, `/admin/payments`, `/admin/payments/[id]`, `/admin/deliveries`, `/admin/operations`, `/admin/operations/reports`, `/admin/operations/incidents`, `/admin/operations/jobs` |

Registration creates a customer. Staff roles must be granted by trusted administration; the registration form cannot choose a privileged role. The opted-in real browser fixture creates new, unique local users and grants roles only to those returned UUIDs. It never edits arbitrary existing accounts.

## Features

- URL-backed catalogue search, category/price/sort filters and pagination; exact monetary display and local decorative artwork because the API has no image field.
- Real cart mutations and validation, shipping forms, stable checkout/cancel/payment/refund attempt keys, bounded asynchronous polling and independent financial/fulfillment timelines.
- Vendor create/edit/status forms with backend versions; owned stock, signed adjustments and history. Ambiguous additive cart/stock results require reconciliation before another attempt.
- Agent availability, copyable profile UUID, assigned deliveries, strictly authorized complete address and allowed pickup/out-for-delivery/delivered/failure actions.
- Admin categories, orders, payments/attempts/refunds, delivery lookup/manual assignment, actual operations metrics/reports/charts/incidents and read-only job state.
- Durable notifications, unread bell, read/read-all, email/SMS preferences and authenticated SSE with REST polling fallback.
- Loading, empty, validation, ownership, conflict, rate-limit and unavailable-service states; semantic controls, keyboard focus, responsive navigation and tables.

## Verification

```sh
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
npx playwright install chromium
npm run test:e2e
```

Fixture browser tests control gateway responses only inside tests. For the real four-role commerce journey, first start the isolated backend project, then run:

```sh
COMPOSE_PROJECT_NAME=orderflow-phase9 ORDERFLOW_REAL_E2E=true npm run test:e2e:real
```

Set `PLAYWRIGHT_EXTERNAL_SERVER=true` when testing an already-running production frontend. Real tests disable trace/video recording and form-value failure snapshots, generate unique accounts, assert HttpOnly cookies and empty browser storage, and complete signed simulator callbacks from a trusted Node CLI. No external provider credentials are required. Native Playwright launches are one worker to respect local memory limits. Run Java integration tests separately from the full stack/Chromium on small Docker hosts.

See [testing](docs/testing.md), [architecture](docs/frontend-architecture.md), [authentication](docs/authentication.md), [simulator payments](docs/payment-simulator.md), [backend contracts](docs/backend-api-mapping.md) and [verification report](docs/phase9-verification.md). These distinguish executed checks from unavailable external integrations.

## Backend gaps represented honestly

There are no product images/public stock, vendor search/status filters or direct management-detail read, general agent directory, arbitrary delivery directory, notification destination links/in-app toggle, or currency field on aggregate operations volumes. Vendor detail lookup is bounded to 20 pages of 100 owned records and supports seeding from a list link. Admin delivery lookup uses authorized orders or an entered identifier; agents share their real profile UUID for manual assignment. Metrics label their actual page/snapshot scope. Real OAuth, real payments and actual email/SMS delivery need separate provider configuration.
