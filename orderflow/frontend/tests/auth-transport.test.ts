import { beforeAll, afterAll, afterEach, it, expect, vi } from "vitest";
import { http, HttpResponse, delay } from "msw";
import { setupServer } from "msw/node";
import { request, refreshSession, ApiError } from "@/lib/api/http";
import { session } from "@/lib/auth/session";
import type { Authentication } from "@/types/contracts";
const auth: Authentication = {
  accessToken: "new-token",
  tokenType: "Bearer",
  expiresIn: 900,
  user: {
    id: "user",
    email: "user@example.test",
    displayName: "Reader",
    status: "ACTIVE",
    roles: ["CUSTOMER"],
    createdAt: "2026-10-12T00:00:00Z",
  },
};
const server = setupServer(
  http.get("http://localhost:8080/api/auth/csrf", () =>
    HttpResponse.json({ token: "csrf", headerName: "X-XSRF-TOKEN" }),
  ),
);
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterAll(() => server.close());
afterEach(() => {
  server.resetHandlers();
  session.clear();
  document.cookie = "XSRF-TOKEN=;max-age=0";
  vi.unstubAllGlobals();
});
it("coalesces simultaneous 401s and retries with rotated token", async () => {
  let refreshes = 0;
  session.set({ ...auth, accessToken: "old-token" });
  server.use(
    http.post(
      "http://localhost:8080/api/auth/refresh",
      async ({ request: r }) => {
        expect(r.headers.get("X-XSRF-TOKEN")).toBe("csrf");
        refreshes++;
        await delay(30);
        return HttpResponse.json(auth);
      },
    ),
    http.get("http://localhost:8080/api/orders", ({ request: r }) =>
      r.headers.get("Authorization") === "Bearer new-token"
        ? HttpResponse.json({ ok: true })
        : HttpResponse.json(
            { code: "TOKEN_EXPIRED", message: "Expired" },
            { status: 401 },
          ),
    ),
  );
  const values = await Promise.all(
    Array.from({ length: 5 }, () => request<{ ok: boolean }>("/api/orders")),
  );
  expect(values.every((x) => x.ok)).toBe(true);
  expect(refreshes).toBe(1);
});
it("failed refresh clears authentication without a loop", async () => {
  let refreshes = 0;
  session.set({ ...auth, accessToken: "old-token" });
  server.use(
    http.get("http://localhost:8080/api/orders", () =>
      HttpResponse.json({ message: "Expired" }, { status: 401 }),
    ),
    http.post("http://localhost:8080/api/auth/refresh", () => {
      refreshes++;
      return HttpResponse.json({ message: "Invalid refresh" }, { status: 401 });
    }),
  );
  await expect(request("/api/orders")).rejects.toBeInstanceOf(ApiError);
  expect(refreshes).toBe(1);
  expect(session.get().status).toBe("anonymous");
});
it("logout prevents an in-flight refresh from resurrecting the account", async () => {
  server.use(
    http.post("http://localhost:8080/api/auth/refresh", async () => {
      await delay(40);
      return HttpResponse.json(auth);
    }),
  );
  const promise = refreshSession();
  await delay(10);
  session.clear();
  await expect(promise).rejects.toMatchObject({ code: "SESSION_CHANGED" });
  expect(session.get().user).toBeNull();
});
it("does not refresh on permission errors and preserves field errors", async () => {
  server.use(
    http.get("http://localhost:8080/api/admin/orders", () =>
      HttpResponse.json(
        {
          code: "FORBIDDEN",
          message: "Permission denied",
          correlationId: "safe-reference",
          fieldErrors: [{ field: "role", message: "Admin required" }],
        },
        { status: 403 },
      ),
    ),
  );
  await expect(request("/api/admin/orders")).rejects.toMatchObject({
    status: 403,
    correlationId: "safe-reference",
    fieldErrors: [{ field: "role", message: "Admin required" }],
  });
});
it("preserves decimal digits and large integers from JSON", async () => {
  server.use(
    http.get(
      "http://localhost:8080/api/money",
      () =>
        new HttpResponse('{"amount":99999999999999999.99,"count":2}', {
          headers: { "Content-Type": "application/json" },
        }),
    ),
  );
  expect(await request("/api/money", { auth: false })).toEqual({
    amount: "99999999999999999.99",
    count: 2,
  });
});
it("honors Retry-After without sending an immediate repeated request", async () => {
  let calls = 0;
  server.use(
    http.get("http://localhost:8080/api/limited-fixture", () => {
      calls++;
      return HttpResponse.json(
        { code: "RATE_LIMITED", message: "Too many requests" },
        { status: 429, headers: { "Retry-After": "10" } },
      );
    }),
  );
  await expect(
    request("/api/limited-fixture", { auth: false }),
  ).rejects.toMatchObject({ status: 429, retryAfter: 10 });
  await expect(
    request("/api/limited-fixture", { auth: false }),
  ).rejects.toMatchObject({ status: 429 });
  expect(calls).toBe(1);
});
it.each([false, true])(
  "orders refresh completion before logout cookie writes (Web Locks: %s)",
  async (native) => {
    const events: string[] = [];
    const lock = vi.fn(async (_name: string, action: () => Promise<unknown>) =>
      action(),
    );
    vi.stubGlobal("navigator", native ? { locks: { request: lock } } : {});
    session.set({ ...auth, accessToken: "old-token" });
    let started!: () => void;
    const ready = new Promise<void>((resolve) => {
      started = resolve;
    });
    server.use(
      http.post("http://localhost:8080/api/auth/refresh", async () => {
        events.push("refresh start");
        started();
        await delay(40);
        events.push("refresh end");
        return HttpResponse.json(auth);
      }),
      http.post("http://localhost:8080/api/auth/logout", () => {
        events.push("logout");
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const refreshing = refreshSession();
    await ready;
    const loggingOut = request<void>("/api/auth/logout", { method: "POST" });
    await Promise.all([refreshing, loggingOut]);
    expect(events).toEqual(["refresh start", "refresh end", "logout"]);
    if (native)
      expect(lock.mock.calls.map(([name]) => name)).toEqual([
        "orderflow-auth-cookies",
        "orderflow-auth-cookies",
      ]);
  },
);
it("rejects an old-account mutation instead of replaying it for a refreshed different user", async () => {
  session.set({ ...auth, accessToken: "expired-a" });
  const epoch = session.get().epoch;
  let posts = 0;
  server.use(
    http.post("http://localhost:8080/api/cart/items", () => {
      posts++;
      return HttpResponse.json({ message: "Expired" }, { status: 401 });
    }),
    http.post("http://localhost:8080/api/auth/refresh", () =>
      HttpResponse.json({ ...auth, user: { ...auth.user, id: "user-b" } }),
    ),
  );
  await expect(
    request("/api/cart/items", {
      method: "POST",
      body: { productId: "product", quantity: 1 },
    }),
  ).rejects.toMatchObject({ code: "SESSION_CHANGED" });
  expect(posts).toBe(1);
  expect(session.get().epoch).toBeGreaterThan(epoch);
  expect(session.get().user?.id).toBe("user-b");
});
it.each(["{broken", "", "null"])(
  "marks an unreadable accepted mutation as an uncertain transport result (%s)",
  async (body) => {
    server.use(
      http.post(
        "http://localhost:8080/api/orders",
        () =>
          new HttpResponse(body, {
            status: 202,
            headers: { "Content-Type": "application/json" },
          }),
      ),
    );
    await expect(
      request("/api/orders", { method: "POST", body: {}, auth: false }),
    ).rejects.toMatchObject({ status: 502, code: "INVALID_RESPONSE" });
  },
);
