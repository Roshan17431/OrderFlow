import { afterAll, afterEach, beforeAll, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { session } from "@/lib/auth/session";
import { AuthProvider } from "@/providers/auth-provider";
import { findVendorProduct } from "@/features/vendor/product-lookup";
import { VendorDashboard } from "@/features/vendor/vendor-dashboard";
import { AgentDashboard } from "@/features/delivery-agent/agent-dashboard";
import { AgentDeliveryDetail } from "@/features/delivery-agent/delivery-detail";
import { VendorProducts } from "@/features/vendor/vendor-products";
import { InventoryDetail } from "@/features/inventory/inventory-detail";
import type { Agent, Delivery, User, VendorProduct } from "@/types/contracts";
const base = "http://localhost:8080";
const user: User = {
  id: "vendor-1",
  displayName: "Vendor",
  email: "vendor@example.test",
  status: "ACTIVE",
  roles: ["VENDOR"],
  createdAt: "2026-01-01T00:00:00Z",
};
const product: VendorProduct = {
  id: "product-1",
  vendorId: user.id,
  name: "Tray",
  sku: "TRAY-1",
  slug: "tray",
  description: null,
  priceAmount: "20.00",
  currency: "USD",
  status: "ACTIVE",
  category: { id: "category-1", name: "Home", slug: "home" },
  version: 4,
  updatedAt: "2026-01-01T00:00:00Z",
};
const agent: Agent = {
  userId: user.id,
  availability: "AVAILABLE",
  activeDeliveryId: "delivery-1",
  updatedAt: "2026-01-01T00:00:00Z",
};
const delivery: Delivery = {
  id: "delivery-1",
  orderId: "order-1",
  deliveryAgentId: "profile-1",
  status: "ASSIGNED",
  shippingAddress: {
    recipientName: "Sam",
    city: "Pune",
    stateOrRegion: "MH",
    countryCode: "IN",
  },
  assignedAt: "2026-01-01T00:00:00Z",
  pickedUpAt: null,
  outForDeliveryAt: null,
  deliveredAt: null,
  failedAt: null,
  cancelledAt: null,
  version: 0,
};
const server = setupServer(
  http.get(`${base}/api/auth/csrf`, () =>
    HttpResponse.json({ token: "csrf-test" }),
  ),
);
beforeAll(() => {
  server.listen({ onUnhandledRequest: "error" });
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute("open");
  };
});
afterEach(() => {
  server.resetHandlers();
  session.clear();
});
afterAll(() => server.close());
function mount(ui: React.ReactNode) {
  session.set({
    accessToken: "test-access",
    tokenType: "Bearer",
    expiresIn: 600,
    user,
  });
  return render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: {
            queries: { retry: false },
            mutations: { retry: false },
          },
        })
      }
    >
      <AuthProvider>{ui}</AuthProvider>
    </QueryClientProvider>,
  );
}

it("finds an owned product on a later page using only supported query parameters", async () => {
  const requested: string[] = [];
  server.use(
    http.get(`${base}/api/vendor/products`, ({ request }) => {
      const url = new URL(request.url);
      requested.push(url.search);
      const page = Number(url.searchParams.get("page"));
      return HttpResponse.json({
        items: page === 1 ? [product] : [],
        page,
        size: 100,
        totalElements: 101,
        totalPages: 2,
      });
    }),
  );
  const found = await findVendorProduct(product.id, user.id);
  expect(found).toEqual(product);
  expect(requested).toEqual([
    "?vendorId=vendor-1&page=0&size=100&sort=createdAt%2Cdesc",
    "?vendorId=vendor-1&page=1&size=100&sort=createdAt%2Cdesc",
  ]);
});
it("bounds product discovery when a large list never contains the requested product", async () => {
  let calls = 0;
  server.use(
    http.get(`${base}/api/vendor/products`, () => {
      calls++;
      return HttpResponse.json({
        items: [],
        page: calls - 1,
        size: 100,
        totalElements: 9000,
        totalPages: 90,
      });
    }),
  );
  await expect(findVendorProduct("missing", user.id)).rejects.toMatchObject({
    code: "PRODUCT_LOOKUP_LIMIT",
  });
  expect(calls).toBe(20);
});
it("uses totalElements for vendor totals and labels status counts as current-page counts", async () => {
  server.use(
    http.get(`${base}/api/vendor/products`, () =>
      HttpResponse.json({
        items: [product, { ...product, id: "product-2", status: "INACTIVE" }],
        page: 0,
        size: 20,
        totalElements: 42,
        totalPages: 3,
      }),
    ),
  );
  mount(<VendorDashboard />);
  expect(await screen.findByText("42")).toBeVisible();
  expect(screen.getByText("Active on this page")).toBeVisible();
  expect(screen.getByText("Inactive on this page")).toBeVisible();
  expect(screen.queryByText(/Revenue|Sales/)).not.toBeInTheDocument();
});
it("allows first-time delivery profile registration after profile not found", async () => {
  let availability: unknown;
  server.use(
    http.get(`${base}/api/delivery/agents/me`, () =>
      HttpResponse.json(
        {
          status: 404,
          code: "DELIVERY_NOT_FOUND",
          message: "Delivery not found",
        },
        { status: 404 },
      ),
    ),
    http.put(
      `${base}/api/delivery/agents/me/availability`,
      async ({ request }) => {
        availability = await request.json();
        return HttpResponse.json({ ...agent, activeDeliveryId: null });
      },
    ),
    http.get(`${base}/api/delivery/agents/me/deliveries`, () =>
      HttpResponse.json({
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      }),
    ),
  );
  mount(<AgentDashboard />);
  await userEvent.click(
    await screen.findByRole("button", { name: "Become available" }),
  );
  await waitFor(() =>
    expect(availability).toEqual({ availability: "AVAILABLE" }),
  );
});
it("treats busy as server-controlled and retrieves active delivery outside the visible page", async () => {
  server.use(
    http.get(`${base}/api/delivery/agents/me`, () =>
      HttpResponse.json({ ...agent, availability: "BUSY" }),
    ),
    http.get(`${base}/api/delivery/agents/me/deliveries`, () =>
      HttpResponse.json({
        content: [],
        page: 0,
        size: 20,
        totalElements: 40,
        totalPages: 2,
      }),
    ),
    http.get(`${base}/api/delivery/agents/me/deliveries/delivery-1`, () =>
      HttpResponse.json(delivery),
    ),
  );
  mount(<AgentDashboard />);
  expect(
    await screen.findByText(
      "Server controls availability during an active delivery.",
    ),
  ).toBeVisible();
  expect(
    screen.getByRole("button", { name: "Become available" }),
  ).toBeDisabled();
  expect(screen.getByRole("button", { name: "Go offline" })).toBeDisabled();
  expect(
    await screen.findByRole("link", { name: /View active delivery/ }),
  ).toHaveAttribute("href", "/delivery-agent/deliveries/delivery-1");
});
it("displays and copies the actual agent profile identifier for admin assignment", async () => {
  const profileId = "4c184752-589c-4119-84d5-0663b893c786";
  const interaction = userEvent.setup();
  server.use(
    http.get(`${base}/api/delivery/agents/me`, () =>
      HttpResponse.json({ ...agent, profileId, activeDeliveryId: null }),
    ),
    http.get(`${base}/api/delivery/agents/me/deliveries`, () =>
      HttpResponse.json({
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      }),
    ),
  );
  mount(<AgentDashboard />);
  expect(await screen.findByLabelText("Agent profile ID")).toHaveValue(
    profileId,
  );
  expect(screen.getByText(/Share this ID with an administrator/)).toBeVisible();
  await interaction.click(
    screen.getByRole("button", { name: "Copy agent profile ID" }),
  );
  expect(await navigator.clipboard.readText()).toBe(profileId);
  expect(await screen.findByText("Profile ID copied.")).toHaveAttribute(
    "role",
    "status",
  );
});
it("loads street address only from the authenticated assigned-agent address endpoint", async () => {
  server.use(
    http.get(`${base}/api/delivery/agents/me/deliveries/delivery-1`, () =>
      HttpResponse.json(delivery),
    ),
    http.get(
      `${base}/api/delivery/agents/me/deliveries/delivery-1/address`,
      () =>
        HttpResponse.json({
          recipientName: "Sam",
          addressLine1: "8 River Road",
          addressLine2: "Flat 4",
          city: "Pune",
          stateOrRegion: "MH",
          postalCode: "411001",
          countryCode: "IN",
          deliveryInstructions: "Ring the bell",
        }),
    ),
  );
  mount(<AgentDeliveryDetail deliveryId="delivery-1" />);
  expect(await screen.findByText("8 River Road")).toBeVisible();
  expect(screen.getByText("Ring the bell")).toBeVisible();
});
it("does not request or reveal address if assigned delivery read is denied", async () => {
  let addresses = 0;
  server.use(
    http.get(`${base}/api/delivery/agents/me/deliveries/delivery-1`, () =>
      HttpResponse.json(
        {
          status: 404,
          code: "DELIVERY_NOT_FOUND",
          message: "Delivery not found",
        },
        { status: 404 },
      ),
    ),
    http.get(
      `${base}/api/delivery/agents/me/deliveries/delivery-1/address`,
      () => {
        addresses++;
        return HttpResponse.json({ addressLine1: "Private road" });
      },
    ),
  );
  mount(<AgentDeliveryDetail deliveryId="delivery-1" />);
  expect(await screen.findByText("Delivery not found")).toBeVisible();
  expect(addresses).toBe(0);
  expect(screen.queryByText("Private road")).not.toBeInTheDocument();
});
it("confirms archive using the listed version and reloads the paginated vendor list", async () => {
  let accepted: unknown;
  let archived = false;
  server.use(
    http.get(`${base}/api/vendor/products`, () =>
      HttpResponse.json({
        items: [
          {
            ...product,
            status: archived ? "ARCHIVED" : "ACTIVE",
            version: archived ? 5 : 4,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    ),
    http.patch(
      `${base}/api/vendor/products/product-1/archive`,
      async ({ request }) => {
        accepted = await request.json();
        archived = true;
        return HttpResponse.json({
          ...product,
          status: "ARCHIVED",
          version: 5,
        });
      },
    ),
  );
  mount(<VendorProducts />);
  await userEvent.click(
    await screen.findByRole("button", { name: "Archive Tray" }),
  );
  await userEvent.click(
    screen.getByRole("button", { name: "Archive product" }),
  );
  await waitFor(() => expect(accepted).toEqual({ version: 4 }));
  expect(await screen.findByText("Archived")).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "Activate Tray" }),
  ).not.toBeInTheDocument();
});
it("shows confirmed inventory quantities and normalizes adjustment history items", async () => {
  server.use(
    http.get(`${base}/api/vendor/products`, () =>
      HttpResponse.json({
        items: [product],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
      }),
    ),
    http.get(`${base}/api/inventory/products/product-1`, () =>
      HttpResponse.json({
        productId: "product-1",
        onHandQuantity: 12,
        reservedQuantity: 9,
        availableQuantity: 3,
        version: 2,
        updatedAt: "2026-01-01T00:00:00Z",
      }),
    ),
    http.get(`${base}/api/inventory/products/product-1/adjustments`, () =>
      HttpResponse.json({
        items: [
          {
            id: "adj-1",
            productId: "product-1",
            quantityDelta: 12,
            resultingOnHand: 12,
            resultingReserved: 9,
            reason: "Initial stock",
            reference: "COUNT-001",
            actorUserId: user.id,
            createdAt: "2026-01-01T00:00:00Z",
            correlationId: "corr-1",
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    ),
  );
  mount(<InventoryDetail productId="product-1" />);
  expect(await screen.findByText("Initial stock")).toBeVisible();
  expect(screen.getByText("On hand")).toBeVisible();
  expect(screen.getByText("Reserved")).toBeVisible();
  expect(screen.getByText("Available")).toBeVisible();
  expect(screen.getByText("COUNT-001")).toBeVisible();
  expect(screen.getByRole("button", { name: "Adjust stock" })).toBeVisible();
});
