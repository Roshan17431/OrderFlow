# Frontend architecture

App Router route files render feature components. `components/layout` provides public and role workspaces; `components/ui` provides shared semantic controls and states. Business features are grouped under catalogue/cart/checkout/orders/payments/vendor/inventory/delivery-agent/admin/auth/notifications/settings. `types/contracts.ts` mirrors inspected backend DTOs and enums.

The browser calls only the configured API Gateway. `lib/api/http.ts` owns credentialed requests, memory bearer access tokens, CSRF bootstrap, correlation IDs, a 20-second deadline, lossless JSON money parsing, error normalization, bounded refresh and Retry-After handling. `lib/api/index.ts` exposes typed service operations and normalizes both backend `items` and `content` page shapes. No business proxy, fake session provider or alternative identity store is introduced.

TanStack Query caches reads; mutations use backend responses and invalidate actual dependent keys. Public catalogue/product/category queries survive anonymous session restoration failure so their observers can settle. Private queries and mutation cache are canceled/removed on logout or session epoch change. User-specific keys isolate ordinary account reads; epoch checks also protect shared staff resource keys from stale in-flight responses. Public product/category mutation invalidation covers list/detail/category selectors.

Form input uses React Hook Form/Zod and applies actual backend field errors. Versioned writes include the fetched backend version. Decimal strings remain exact: formatting avoids Number conversion and cart multiplication uses 40-digit Decimal precision. Charts convert only count metrics, not monetary totals. Backend order/payment/refund totals remain authoritative.

Checkout prepares a stable body/key after authoritative cart validation. Transport errors and unreadable accepted responses enter explicit uncertain recovery; retries reuse the saved body/key without another cart validation. Payment, cancellation and refund requests retain the same key for an uncertain attempt. Non-idempotent stock and additive cart writes reconcile through successful backend reads before another attempt. Delivery actions show only allowed transitions; BUSY is backend-controlled.

Order/payment polling ends on terminal state, transport failure or the two-minute budget. Admin refund polling is bounded to one minute and refreshes payment plus refund history. Manual refresh remains available. SSE uses Authorization headers, bounded reconnect/backoff and deduplication; 30-second notification REST queries continue even if streaming is unavailable.

The frontend image builds with Node 22, serves standalone Next.js as UID/GID 1001, embeds only public build variables, and has an HTTP health check. The optional `frontend` Compose profile preserves the existing default backend topology. CI adds independent npm quality/build/fixture-browser checks without deployment or mandatory real provider credentials.

Minimal compatible backend additions are documented in the contract matrix: ADMIN paginated category read; an assignment-owned agent full-address read; additive agent profile UUID; OAuth gateway forwarding; localhost:3000 callback defaults. Financial lifecycle, database ownership, migrations and service authorization remain the existing backend implementation.

Protected content uses a session-epoch Fragment key. Same-role account changes remount private query observers and local form/dialog state; clearing the cache alone does not rebind every already-mounted shared-key observer. The regression was observed failing with the old user’s address still rendered and passing after the key change.
