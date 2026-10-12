import { afterAll, afterEach, beforeAll, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { ProductForm } from "@/features/vendor/product-form";
import { AdjustmentForm } from "@/features/inventory/adjustment-form";
import { DeliveryActions } from "@/features/delivery-agent/delivery-actions";
import type {
  Category,
  Delivery,
  Stock,
  VendorProduct,
} from "@/types/contracts";

const base = "http://localhost:8080";
const server = setupServer(
  http.get(`${base}/api/auth/csrf`, () =>
    HttpResponse.json({ token: "csrf-test" }),
  ),
);
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
const category: Category = {
  id: "2cb83930-bbe0-4b7b-a45d-354857507220",
  name: "Home",
  slug: "home",
  description: null,
  status: "ACTIVE",
  version: 0,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};
const product: VendorProduct = {
  id: "bcb83930-bbe0-4b7b-a45d-354857507220",
  vendorId: "vendor-1",
  sku: "HOME-1",
  slug: "home-one",
  name: "Tray",
  description: "Oak tray",
  priceAmount: "999999999999999.25",
  currency: "USD",
  status: "ACTIVE",
  category,
  version: 7,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};
const stock: Stock = {
  productId: product.id,
  onHandQuantity: 12,
  reservedQuantity: 9,
  availableQuantity: 3,
  version: 2,
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
function mount(
  ui: React.ReactNode,
  client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  }),
) {
  return render(
    <QueryClientProvider client={client}>{ui}</QueryClientProvider>,
  );
}

it("edits with the fetched version and exact decimal, leaving SKU and slug out of the update request", async () => {
  let accepted: unknown;
  server.use(
    http.put(
      `${base}/api/vendor/products/${product.id}`,
      async ({ request }) => {
        accepted = await request.json();
        return HttpResponse.json({
          ...product,
          version: 8,
          name: "Large tray",
        });
      },
    ),
  );
  const client = new QueryClient();
  client.setQueryData(["catalog", "recent"], [product]);
  client.setQueryData(["product", product.id], product);
  mount(<ProductForm product={product} categories={[category]} />, client);
  expect(screen.getByLabelText("SKU")).toBeDisabled();
  expect(screen.getByLabelText("Slug")).toBeDisabled();
  await userEvent.clear(screen.getByLabelText("Product name"));
  await userEvent.type(screen.getByLabelText("Product name"), "Large tray");
  await userEvent.click(screen.getByRole("button", { name: "Save product" }));
  await waitFor(() =>
    expect(accepted).toEqual({
      name: "Large tray",
      description: "Oak tray",
      priceAmount: "999999999999999.25",
      currency: "USD",
      categoryId: category.id,
      version: 7,
    }),
  );
  expect(await screen.findByText("Product saved.")).toBeVisible();
  expect(client.getQueryState(["catalog", "recent"])?.isInvalidated).toBe(true);
  expect(client.getQueryState(["product", product.id])?.isInvalidated).toBe(
    true,
  );
});

it("validates product price before sending a create request", async () => {
  mount(<ProductForm categories={[category]} />);
  await userEvent.type(screen.getByLabelText("Product name"), "Tray");
  await userEvent.type(screen.getByLabelText("SKU"), "HOME-1");
  await userEvent.selectOptions(screen.getByLabelText("Category"), category.id);
  await userEvent.type(screen.getByLabelText("Price"), "12.345");
  await userEvent.click(screen.getByRole("button", { name: "Create product" }));
  expect(await screen.findByText(/at most two decimal places/)).toBeVisible();
});

it("blocks zero, fractional and unsafe inventory adjustments", async () => {
  mount(<AdjustmentForm stock={stock} />);
  await userEvent.type(
    screen.getByLabelText("Adjustment reason"),
    "Stock count",
  );
  for (const value of ["0", "1.5", "9007199254740992"]) {
    await userEvent.clear(screen.getByLabelText("Quantity change"));
    await userEvent.type(screen.getByLabelText("Quantity change"), value);
    await userEvent.click(screen.getByRole("button", { name: "Adjust stock" }));
    expect(await screen.findByText(/non-zero safe whole number/)).toBeVisible();
  }
});

it("warns below reserved stock and sends the signed adjustment without inventing a final stock result", async () => {
  let accepted: unknown;
  server.use(
    http.post(
      `${base}/api/inventory/products/${product.id}/adjustments`,
      async ({ request }) => {
        accepted = await request.json();
        return HttpResponse.json({
          ...stock,
          onHandQuantity: 8,
          availableQuantity: 0,
          version: 3,
        });
      },
    ),
  );
  mount(<AdjustmentForm stock={stock} />);
  await userEvent.type(screen.getByLabelText("Quantity change"), "-4");
  await userEvent.type(
    screen.getByLabelText("Adjustment reason"),
    "Damaged count",
  );
  await userEvent.type(screen.getByLabelText("Reference"), "COUNT-12");
  expect(screen.getByText(/below the reserved quantity/)).toBeVisible();
  await userEvent.click(screen.getByRole("button", { name: "Adjust stock" }));
  await waitFor(() =>
    expect(accepted).toEqual({
      quantityDelta: -4,
      reason: "Damaged count",
      reference: "COUNT-12",
    }),
  );
  expect(await screen.findByText(/Stock adjusted/)).toBeVisible();
});

it("shows only the allowed delivery action and sends a supported failure code", async () => {
  let accepted: unknown;
  server.use(
    http.post(
      `${base}/api/delivery/agents/me/deliveries/delivery-1/failed`,
      async ({ request }) => {
        accepted = await request.json();
        return HttpResponse.json({
          ...delivery,
          status: "FAILED",
          failedAt: "2026-01-01T01:00:00Z",
        });
      },
    ),
  );
  mount(<DeliveryActions delivery={delivery} />);
  expect(screen.getByRole("button", { name: "Mark picked up" })).toBeEnabled();
  expect(
    screen.queryByRole("button", { name: "Mark delivered" }),
  ).not.toBeInTheDocument();
  await userEvent.selectOptions(
    screen.getByLabelText("Failure reason"),
    "ADDRESS_PROBLEM",
  );
  await userEvent.click(screen.getByRole("button", { name: "Report failure" }));
  await waitFor(() =>
    expect(accepted).toEqual({ failureCode: "ADDRESS_PROBLEM" }),
  );
  expect(await screen.findByText("Delivery updated.")).toBeVisible();
});

it.each(["DELIVERED", "FAILED", "CANCELLED", "CANCEL_REQUESTED"] as const)(
  "does not allow lifecycle writes for %s deliveries",
  (status) => {
    mount(<DeliveryActions delivery={{ ...delivery, status }} />);
    expect(
      screen.queryByRole("button", { name: /Mark|Report failure/ }),
    ).not.toBeInTheDocument();
  },
);

it("offers reload on optimistic-lock conflict and preserves user changes", async () => {
  server.use(
    http.put(`${base}/api/vendor/products/${product.id}`, () =>
      HttpResponse.json(
        {
          status: 409,
          code: "PRODUCT_VERSION_CONFLICT",
          message: "The product was modified by another request.",
        },
        { status: 409 },
      ),
    ),
  );
  mount(<ProductForm product={product} categories={[category]} />);
  await userEvent.clear(screen.getByLabelText("Product name"));
  await userEvent.type(screen.getByLabelText("Product name"), "My change");
  await userEvent.click(screen.getByRole("button", { name: "Save product" }));
  expect(await screen.findByText(/modified by another request/)).toBeVisible();
  expect(
    screen.getByRole("button", { name: /Reload latest product/ }),
  ).toBeVisible();
  expect(screen.getByLabelText("Product name")).toHaveValue("My change");
});
it("waits for stock and history refresh before permitting an uncertain adjustment to be retried", async () => {
  server.use(
    http.post(`${base}/api/inventory/products/${product.id}/adjustments`, () =>
      HttpResponse.json(
        {
          status: 503,
          code: "REQUEST_TIMEOUT",
          message: "The request timed out.",
        },
        { status: 503 },
      ),
    ),
  );
  let finishRefresh!: (value: boolean) => void;
  const refreshed = new Promise<boolean>((resolve) => {
    finishRefresh = resolve;
  });
  mount(<AdjustmentForm stock={stock} onRefresh={() => refreshed} />);
  await userEvent.type(screen.getByLabelText("Quantity change"), "2");
  await userEvent.type(
    screen.getByLabelText("Adjustment reason"),
    "Stock count",
  );
  await userEvent.click(screen.getByRole("button", { name: "Adjust stock" }));
  await screen.findByText("The request timed out.");
  expect(screen.getByRole("button", { name: "Adjust stock" })).toBeDisabled();
  await userEvent.click(
    screen.getByRole("button", { name: "Refresh stock and history" }),
  );
  expect(screen.getByRole("button", { name: "Adjust stock" })).toBeDisabled();
  finishRefresh(true);
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "Adjust stock" })).toBeEnabled(),
  );
});
