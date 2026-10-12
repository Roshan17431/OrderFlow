import { it, expect, beforeAll, afterAll, vi } from "vitest";
import { render, screen, act } from "@testing-library/react";
import {
  useQuery,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { http, HttpResponse, delay } from "msw";
import { setupServer } from "msw/node";
import { AppProvider } from "@/providers/app-provider";
import { ApiError } from "@/lib/api/http";
import { AuthProvider } from "@/providers/auth-provider";
import { session } from "@/lib/auth/session";
import type { Authentication } from "@/types/contracts";
import { RoleGuard } from "@/components/layout/role-guard";
vi.mock("next/navigation", () => ({
  usePathname: () => "/delivery-agent/deliveries/example",
  useRouter: () => ({ replace: vi.fn() }),
}));
function PrivateAddress() {
  const query = useQuery({
    queryKey: ["agent", "address", "delivery"],
    staleTime: Infinity,
    queryFn: async () => ({
      street: session.get().user?.id === "b" ? "Private B" : "Private A",
    }),
  });
  return <p>{query.data?.street ?? "Loading private address"}</p>;
}
it("rebinds mounted shared-key private observers when an account changes to the same role", async () => {
  const auth: Authentication = {
    accessToken: "a",
    tokenType: "Bearer",
    expiresIn: 900,
    user: {
      id: "a",
      email: "a@example.test",
      displayName: "A",
      status: "ACTIVE",
      roles: ["DELIVERY_AGENT"],
      createdAt: "2026-10-12T00:00:00Z",
    },
  };
  session.clear();
  session.set(auth);
  const client = new QueryClient();
  client.setQueryData(["agent", "address", "delivery"], {
    street: "Private A",
  });
  render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <RoleGuard roles={["DELIVERY_AGENT"]}>
          <PrivateAddress />
        </RoleGuard>
      </AuthProvider>
    </QueryClientProvider>,
  );
  expect(screen.getByText("Private A")).toBeVisible();
  act(() => {
    session.set({ ...auth, user: { ...auth.user, id: "b" } });
  });
  expect(await screen.findByText("Private B")).toBeVisible();
  expect(screen.queryByText("Private A")).not.toBeInTheDocument();
  session.clear();
});
const server = setupServer(
  http.get("http://localhost:8080/api/auth/csrf", () =>
    HttpResponse.json({ token: "csrf" }),
  ),
  http.post("http://localhost:8080/api/auth/refresh", () =>
    HttpResponse.json({ message: "No session" }, { status: 401 }),
  ),
);
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterAll(() => server.close());
function PublicCatalog({ prefix }: { prefix: string }) {
  const q = useQuery({
    queryKey: [prefix, "public-failure-regression"],
    retry: false,
    queryFn: async () => {
      await delay(80);
      throw new ApiError(503, "UNAVAILABLE", "Catalog unavailable");
    },
  });
  return (
    <p>
      {q.isPending
        ? "Loading public catalog"
        : q.isError
          ? "Catalog unavailable"
          : "Loaded"}
    </p>
  );
}
it.each(["catalog", "product"])(
  "anonymous restore failure leaves public %s query observers able to settle",
  async (prefix) => {
    const auth: Authentication = {
      accessToken: "old",
      tokenType: "Bearer",
      expiresIn: 900,
      user: {
        id: "temp",
        email: "temp@example.test",
        displayName: "Temp",
        status: "ACTIVE",
        roles: ["CUSTOMER"],
        createdAt: "2026-10-12T00:00:00Z",
      },
    };
    session.set(auth);
    render(
      <AppProvider>
        <PublicCatalog prefix={prefix} />
      </AppProvider>,
    );
    act(() => {
      session.clear();
    });
    expect(await screen.findByText("Catalog unavailable")).toBeVisible();
  },
);
it("clears private cached data when refresh switches the user, preserving the public catalogue", () => {
  const auth: Authentication = {
    accessToken: "a",
    tokenType: "Bearer",
    expiresIn: 900,
    user: {
      id: "a",
      email: "a@example.test",
      displayName: "A",
      status: "ACTIVE",
      roles: ["DELIVERY_AGENT"],
      createdAt: "2026-10-12T00:00:00Z",
    },
  };
  session.set(auth);
  const client = new QueryClient();
  client.setQueryData(["agent", "address", "delivery"], {
    street: "Private A",
  });
  client.setQueryData(["catalog", "public"], { name: "Public" });
  render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <p>Ready</p>
      </AuthProvider>
    </QueryClientProvider>,
  );
  act(() => {
    session.set({ ...auth, user: { ...auth.user, id: "b" } });
  });
  expect(client.getQueryData(["agent", "address", "delivery"])).toBeUndefined();
  expect(client.getQueryData(["catalog", "public"])).toEqual({
    name: "Public",
  });
  session.clear();
});
