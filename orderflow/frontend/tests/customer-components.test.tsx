import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { AuthProvider } from "@/providers/auth-provider";
import { session } from "@/lib/auth/session";
import { ProductCard } from "@/features/catalog/product-card";
import { CartPage } from "@/features/cart/cart-page";
import { CheckoutPage } from "@/features/checkout/checkout-page";
import { PaymentPanel } from "@/features/payments/payment-panel";
import { OrderDetailPage } from "@/features/orders/order-detail-page";
import { CatalogPage } from "@/features/catalog/catalog-page";
import { ProductDetailPage } from "@/features/catalog/product-detail-page";
import { HomePage } from "@/features/catalog/home-page";
import { TrackingPage } from "@/features/orders/tracking-page";
import { OrdersPage } from "@/features/orders/orders-page";
import {
  authentication,
  cart,
  order,
  payment,
  product,
} from "./customer-fixtures";
const navigation = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  search: "",
}));
vi.mock("next/navigation", () => ({
  useRouter: () => navigation,
  usePathname: () => "/products",
  useSearchParams: () => new URLSearchParams(navigation.search),
}));
const base = "http://localhost:8080";
const server = setupServer(
  http.get(base + "/api/auth/csrf", () =>
    HttpResponse.json({ token: "csrf-test" }),
  ),
  http.get(base + "/api/cart", () => HttpResponse.json(cart)),
  http.post(base + "/api/cart/validate", () =>
    HttpResponse.json({ cart, valid: true, issues: [] }),
  ),
  http.get(base + "/api/payments/orders/:id", () => HttpResponse.json(payment)),
  http.get(base + "/api/orders/:id", () => HttpResponse.json(order)),
  http.get(base + "/api/orders/:id/history", () => HttpResponse.json([])),
);
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterAll(() => server.close());
afterEach(() => {
  server.resetHandlers();
  session.clear();
});
beforeEach(() => {
  navigation.push.mockClear();
  navigation.replace.mockClear();
  navigation.search = "";
  session.set(authentication);
});
function view(element: React.ReactNode) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false, retryDelay: 1 },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>{element}</AuthProvider>
    </QueryClientProvider>,
  );
}
async function fillAddress() {
  for (const [label, value] of Object.entries({
    "Recipient name": "R Lee",
    "Address line 1": "23 Green Lane",
    City: "Pune",
    "State or region": "Maharashtra",
    "Postal code": "411001",
    "Country code": "IN",
  }))
    await userEvent.type(screen.getByLabelText(label), value);
}
describe("real customer components", () => {
  it("requires a successful cart read before another additive retry after a lost response", async () => {
    let added = 0,
      reads = 0;
    server.use(
      http.get(base + "/api/products/" + product.id, () =>
        HttpResponse.json(product),
      ),
      http.post(base + "/api/cart/items", () => {
        added++;
        return HttpResponse.error();
      }),
      http.get(base + "/api/cart", () =>
        ++reads === 1
          ? HttpResponse.json({ message: "Unavailable" }, { status: 503 })
          : HttpResponse.json({
              ...cart,
              items: [{ ...cart.items[0], quantity: added }],
            }),
      ),
    );
    view(<ProductDetailPage productId={product.id} />);
    await screen.findByRole("heading", { name: product.name });
    await userEvent.click(screen.getByRole("button", { name: "Add to cart" }));
    await screen.findByText(/result is uncertain/i);
    expect(screen.getByRole("button", { name: "Add to cart" })).toBeDisabled();
    await userEvent.click(
      screen.getByRole("button", { name: "Check current cart" }),
    );
    await screen.findByText("Unavailable");
    expect(screen.getByRole("button", { name: "Add to cart" })).toBeDisabled();
    await userEvent.click(
      screen.getByRole("button", { name: "Check current cart" }),
    );
    await screen.findByText("Current cart quantity: 1");
    expect(screen.getByRole("button", { name: "Add to cart" })).toBeEnabled();
    expect(added).toBe(1);
  });
  it("renders a product with its exact price and UUID details link", () => {
    view(<ProductCard product={product} />);
    expect(screen.getByRole("link", { name: product.name })).toHaveAttribute(
      "href",
      "/products/" + product.id,
    );
    expect(screen.getByText(/12.10/)).toBeVisible();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });
  it("updates cart quantities through the API and shows snapshot price warnings", async () => {
    server.use(
      http.patch(base + "/api/cart/items/:id", async ({ request, params }) => {
        expect(params.id).toBe(product.id);
        expect(await request.json()).toEqual({ quantity: 3 });
        return HttpResponse.json({
          ...cart,
          version: 8,
          subtotal: "36.30",
          totalQuantity: 3,
          items: [{ ...cart.items[0], quantity: 3 }],
        });
      }),
    );
    view(<CartPage />);
    expect(await screen.findByText(/price has changed/i)).toBeVisible();
    const quantity = screen.getByLabelText("Quantity for Ceramic cup");
    await userEvent.clear(quantity);
    await userEvent.type(quantity, "3");
    await userEvent.click(
      screen.getByRole("button", { name: "Update Ceramic cup" }),
    );
    expect(await screen.findAllByText(/36.30/)).not.toHaveLength(0);
  });
  it("blocks checkout when backend validation reports an unavailable item", async () => {
    server.use(
      http.post(base + "/api/cart/validate", () =>
        HttpResponse.json({
          cart,
          valid: false,
          issues: [
            {
              productId: product.id,
              code: "PRODUCT_UNAVAILABLE",
              message: "This product is no longer purchasable.",
            },
          ],
        }),
      ),
    );
    view(<CartPage />);
    await screen.findByRole("heading", { name: product.name });
    await userEvent.click(
      screen.getByRole("button", { name: "Validate and checkout" }),
    );
    expect(
      await screen.findByText("This product is no longer purchasable."),
    ).toBeVisible();
    expect(navigation.push).not.toHaveBeenCalled();
  });
  it("validates shipping before creating an order", async () => {
    view(<CheckoutPage />);
    await screen.findByRole("button", { name: "Place order" });
    await userEvent.click(screen.getByRole("button", { name: "Place order" }));
    expect(await screen.findByText("Recipient name is required")).toBeVisible();
    expect(navigation.push).not.toHaveBeenCalled();
  });
  it.each(["503", "malformed202", "empty202"])(
    "retries uncertain checkout (%s) with the identical key and body then opens the accepted order",
    async (failure) => {
      const requests: { key: string | null; body: unknown }[] = [];
      server.use(
        http.post(base + "/api/orders", async ({ request }) => {
          requests.push({
            key: request.headers.get("Idempotency-Key"),
            body: await request.json(),
          });
          return requests.length === 1
            ? failure === "503"
              ? HttpResponse.json(
                  { code: "REQUEST_TIMEOUT", message: "Outcome uncertain" },
                  { status: 503 },
                )
              : new HttpResponse(failure === "malformed202" ? "{broken" : "", {
                  status: 202,
                })
            : HttpResponse.json(
                { ...order, status: "INVENTORY_PENDING", paymentId: null },
                { status: 202 },
              );
        }),
      );
      view(<CheckoutPage />);
      await screen.findByRole("button", { name: "Place order" });
      await fillAddress();
      await userEvent.click(
        screen.getByRole("button", { name: "Place order" }),
      );
      await screen.findByRole("button", { name: "Retry same order" });
      await userEvent.click(
        screen.getByRole("button", { name: "Retry same order" }),
      );
      await waitFor(() =>
        expect(navigation.push).toHaveBeenCalledWith("/orders/" + order.id),
      );
      expect(requests).toHaveLength(2);
      expect(requests[0].key).toBeTruthy();
      expect(requests[1]).toEqual(requests[0]);
      expect(requests[0].body).toEqual({
        expectedCartVersion: 7,
        shippingAddress: {
          recipientName: "R Lee",
          addressLine1: "23 Green Lane",
          addressLine2: "",
          city: "Pune",
          stateOrRegion: "Maharashtra",
          postalCode: "411001",
          countryCode: "IN",
          deliveryInstructions: "",
        },
      });
    },
  );
  it("confirms a simulator payment without announcing success from a PROCESSING response", async () => {
    server.use(
      http.post(
        base + "/api/payments/orders/:id/confirm",
        async ({ request }) => {
          expect(await request.json()).toEqual({
            paymentMethodToken: "tok_success",
          });
          expect(request.headers.get("X-XSRF-TOKEN")).toBe("csrf-test");
          return HttpResponse.json({ ...payment, status: "PROCESSING" });
        },
      ),
    );
    view(
      <PaymentPanel
        order={order}
        onChange={() => {}}
        pollingStartedAt={Date.now()}
      />,
    );
    await screen.findByLabelText("Simulator outcome");
    await userEvent.selectOptions(
      screen.getByLabelText("Simulator outcome"),
      "success",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Confirm simulated payment" }),
    );
    expect(await screen.findByText("Processing")).toBeVisible();
    expect(screen.getByText(/trusted.*webhook/i)).toBeVisible();
    expect(screen.queryByText("Payment succeeded")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Confirm simulated payment" }),
    ).not.toBeInTheDocument();
  });
  it("shows requires-action honestly without collecting card details", async () => {
    server.use(
      http.get(base + "/api/payments/orders/:id", () =>
        HttpResponse.json({ ...payment, status: "REQUIRES_ACTION" }),
      ),
    );
    view(
      <PaymentPanel
        order={order}
        onChange={() => {}}
        pollingStartedAt={Date.now()}
      />,
    );
    expect(await screen.findByText("Requires action")).toBeVisible();
    expect(screen.getByText(/authentication.*required/i)).toBeVisible();
    expect(
      screen.queryByRole("button", { name: "Confirm simulated payment" }),
    ).not.toBeInTheDocument();
  });
  it("does not offer cancellation for a paid order and labels fulfillment separately", async () => {
    server.use(
      http.get(base + "/api/orders/:id", () =>
        HttpResponse.json({
          ...order,
          status: "PAID",
          fulfillmentStatus: "DELIVERY_PENDING",
        }),
      ),
    );
    view(<OrderDetailPage orderId={order.id} />);
    expect(await screen.findByText("Paid")).toBeVisible();
    expect(screen.getByText("Financial status")).toBeVisible();
    expect(screen.getByText("Fulfillment status")).toBeVisible();
    expect(
      screen.queryByRole("button", { name: "Cancel order" }),
    ).not.toBeInTheDocument();
  });
  it("submits supported catalog filters through shareable URL parameters", async () => {
    server.use(
      http.get(base + "/api/products", () =>
        HttpResponse.json({
          items: [product],
          page: 0,
          size: 12,
          totalElements: 1,
          totalPages: 1,
        }),
      ),
      http.get(base + "/api/categories", () =>
        HttpResponse.json({
          items: [
            {
              ...product.category,
              status: "ACTIVE",
              description: null,
              version: 1,
              createdAt: product.updatedAt,
              updatedAt: product.updatedAt,
            },
          ],
        }),
      ),
    );
    view(<CatalogPage />);
    await screen.findByRole("link", { name: product.name });
    await userEvent.type(screen.getByLabelText("Search products"), "cup");
    await userEvent.selectOptions(screen.getByLabelText("Category"), "home");
    await userEvent.selectOptions(screen.getByLabelText("Sort"), "price,asc");
    await userEvent.click(
      screen.getByRole("button", { name: "Apply filters" }),
    );
    expect(navigation.push).toHaveBeenCalledWith(
      "/products?q=cup&category=home&sort=price%2Casc",
    );
  });
  it("adds the requested detail quantity and updates real cart state", async () => {
    server.use(
      http.get(base + "/api/products/:id", () => HttpResponse.json(product)),
      http.post(base + "/api/cart/items", async ({ request }) => {
        expect(await request.json()).toEqual({
          productId: product.id,
          quantity: 3,
        });
        return HttpResponse.json({ ...cart, totalQuantity: 3 });
      }),
    );
    view(<ProductDetailPage productId={product.id} />);
    await screen.findByRole("heading", { name: product.name });
    await userEvent.clear(screen.getByLabelText("Quantity"));
    await userEvent.type(screen.getByLabelText("Quantity"), "3");
    await userEvent.click(screen.getByRole("button", { name: "Add to cart" }));
    expect(await screen.findByText("Added to your cart")).toBeVisible();
  });
  it("shows honest storefront empty state rather than fabricated products", async () => {
    server.use(
      http.get(base + "/api/products", () =>
        HttpResponse.json({
          items: [],
          page: 0,
          size: 8,
          totalElements: 0,
          totalPages: 0,
        }),
      ),
      http.get(base + "/api/categories", () =>
        HttpResponse.json({ items: [] }),
      ),
    );
    view(<HomePage />);
    expect(
      await screen.findByText("New arrivals are on their way"),
    ).toBeVisible();
    expect(
      screen.queryByRole("link", { name: product.name }),
    ).not.toBeInTheDocument();
  });
  it("renders actual delivery timestamps without a fabricated map or ETA", async () => {
    server.use(
      http.get(base + "/api/orders/:id", () =>
        HttpResponse.json({
          ...order,
          status: "PAID",
          deliveryId: "623e4567-e89b-42d3-a456-426614174000",
          fulfillmentStatus: "OUT_FOR_DELIVERY",
        }),
      ),
      http.get(base + "/api/deliveries/orders/:id", () =>
        HttpResponse.json({
          id: "623e4567-e89b-42d3-a456-426614174000",
          orderId: order.id,
          status: "OUT_FOR_DELIVERY",
          deliveryAgentId: "agent-id",
          shippingAddress: {
            recipientName: "R Lee",
            city: "Pune",
            stateOrRegion: "Maharashtra",
            countryCode: "IN",
          },
          assignedAt: "2026-10-12T01:00:00Z",
          pickedUpAt: "2026-10-12T02:00:00Z",
          outForDeliveryAt: "2026-10-12T03:00:00Z",
          deliveredAt: null,
          failedAt: null,
          cancelledAt: null,
          version: 3,
        }),
      ),
    );
    view(<TrackingPage orderId={order.id} />);
    expect(await screen.findByText("Agent assigned")).toBeVisible();
    expect(screen.getAllByText("Out for delivery")).not.toHaveLength(0);
    expect(screen.getByText("Delivery agent assigned")).toBeVisible();
    expect(
      screen.queryByText(/estimated arrival|GPS|vehicle location/i),
    ).not.toBeInTheDocument();
    expect(screen.queryByText("agent-id")).not.toBeInTheDocument();
  });
  it("lists actual orders and filters financial state through supported URL fields", async () => {
    navigation.search = "status=PAID&page=1";
    server.use(
      http.get(base + "/api/orders", ({ request }) => {
        const params = new URL(request.url).searchParams;
        expect(params.get("status")).toBe("PAID");
        expect(params.get("page")).toBe("1");
        expect(params.get("size")).toBe("10");
        return HttpResponse.json({
          items: [{ ...order, status: "PAID", fulfillmentStatus: "DELIVERED" }],
          page: 1,
          size: 10,
          totalElements: 11,
          totalPages: 2,
        });
      }),
    );
    view(<OrdersPage />);
    expect(
      await screen.findByRole("link", {
        name: "View order " + order.id.slice(0, 8),
      }),
    ).toHaveAttribute("href", "/orders/" + order.id);
    expect(screen.getAllByText("Paid")).not.toHaveLength(0);
    expect(screen.getByText("Delivered")).toBeVisible();
    await userEvent.selectOptions(
      screen.getByLabelText("Financial status"),
      "PAYMENT_FAILED",
    );
    expect(navigation.push).toHaveBeenCalledWith(
      "/orders?status=PAYMENT_FAILED",
    );
  });
  it("removes a cart item only after a successful real delete", async () => {
    server.use(
      http.delete(base + "/api/cart/items/:id", ({ params }) => {
        expect(params.id).toBe(product.id);
        return HttpResponse.json({
          ...cart,
          items: [],
          subtotal: "0.00",
          totalQuantity: 0,
          distinctItemCount: 0,
        });
      }),
    );
    view(<CartPage />);
    await screen.findByRole("button", { name: "Remove " + product.name });
    await userEvent.click(
      screen.getByRole("button", { name: "Remove " + product.name }),
    );
    expect(await screen.findByText("Your cart is waiting")).toBeVisible();
  });
  it("handles inactive product detail without enabling cart addition", async () => {
    server.use(
      http.get(base + "/api/products/:id", () =>
        HttpResponse.json(
          { code: "PRODUCT_NOT_FOUND", message: "Product is unavailable" },
          { status: 404 },
        ),
      ),
    );
    view(<ProductDetailPage productId={product.id} />);
    expect(await screen.findByText("Product is unavailable")).toBeVisible();
    expect(
      screen.queryByRole("button", { name: "Add to cart" }),
    ).not.toBeInTheDocument();
  });
  it("shows gateway failure with a retry instead of an endless product loader", async () => {
    server.use(
      http.get(base + "/api/products", () =>
        HttpResponse.json(
          { code: "GATEWAY_UNAVAILABLE", message: "Catalog cannot be reached" },
          { status: 503 },
        ),
      ),
      http.get(base + "/api/categories", () =>
        HttpResponse.json({ items: [] }),
      ),
    );
    view(<CatalogPage />);
    expect(await screen.findByText("Catalog cannot be reached")).toBeVisible();
    expect(screen.getByRole("button", { name: "Try again" })).toBeVisible();
    expect(
      screen.queryByRole("status", { name: "Loading products" }),
    ).not.toBeInTheDocument();
  });
  it("shows payment preparation without a confirmation form when no real payment exists", async () => {
    server.use(
      http.get(base + "/api/payments/orders/:id", () =>
        HttpResponse.json(
          { code: "PAYMENT_NOT_FOUND", message: "Not yet created" },
          { status: 404 },
        ),
      ),
    );
    view(
      <PaymentPanel
        order={{ ...order, paymentId: null }}
        onChange={() => {}}
        pollingStartedAt={Date.now()}
      />,
    );
    expect(
      await screen.findByText(/payment is still being prepared/i),
    ).toBeVisible();
    expect(
      screen.queryByRole("button", { name: "Confirm simulated payment" }),
    ).not.toBeInTheDocument();
  });
  it("disables duplicate checkout while cart validation is in flight", async () => {
    let release!: () => void;
    const validationGate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const requests: unknown[] = [];
    server.use(
      http.post(base + "/api/cart/validate", async () => {
        await validationGate;
        return HttpResponse.json({ cart, valid: true, issues: [] });
      }),
      http.post(base + "/api/orders", async ({ request }) => {
        requests.push(await request.json());
        return HttpResponse.json(
          { ...order, status: "INVENTORY_PENDING" },
          { status: 202 },
        );
      }),
    );
    view(<CheckoutPage />);
    await screen.findByRole("button", { name: "Place order" });
    await fillAddress();
    await userEvent.click(screen.getByRole("button", { name: "Place order" }));
    expect(screen.getByRole("button", { name: "Place order" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Place order" }));
    release();
    await waitFor(() =>
      expect(navigation.push).toHaveBeenCalledWith("/orders/" + order.id),
    );
    expect(requests).toHaveLength(1);
  });
  it("refreshes history when the backend advances the order version", async () => {
    let queryCount = 0;
    server.use(
      http.get(base + "/api/orders/:id", () => {
        queryCount += 1;
        const response =
          queryCount > 1
            ? {
                ...order,
                status: "INVENTORY_REJECTED",
                paymentId: null,
                version: 2,
              }
            : { ...order, status: "INVENTORY_PENDING", paymentId: null };
        return HttpResponse.json(response);
      }),
      http.get(base + "/api/orders/:id/history", () =>
        HttpResponse.json([
          {
            previousStatus: "INVENTORY_PENDING",
            newStatus:
              queryCount > 1 ? "INVENTORY_REJECTED" : "INVENTORY_PENDING",
            reasonCode:
              queryCount > 1 ? "INVENTORY_UNAVAILABLE" : "ORDER_CREATED",
            eventId: "inventory-event",
            correlationId: "test-correlation",
            changedAt: "2026-10-12T00:01:00Z",
          },
        ]),
      ),
    );
    view(<OrderDetailPage orderId={order.id} />);
    expect(await screen.findByText("Order created")).toBeVisible();
    expect(
      await screen.findByText("Inventory unavailable", {}, { timeout: 4000 }),
    ).toBeVisible();
  });
  it("explains invalid prices from a shared catalog URL before calling products", async () => {
    navigation.search = "minPrice=20&maxPrice=5";
    server.use(
      http.get(base + "/api/categories", () =>
        HttpResponse.json({ items: [] }),
      ),
    );
    view(<CatalogPage />);
    expect(
      await screen.findByText("Minimum price must not exceed maximum price."),
    ).toBeVisible();
  });
  it("routes vendor storefront visitors to their dashboard instead of customer orders", async () => {
    session.set({
      ...authentication,
      user: { ...authentication.user, roles: ["VENDOR"] },
    });
    server.use(
      http.get(base + "/api/products", () =>
        HttpResponse.json({
          items: [],
          page: 0,
          size: 8,
          totalElements: 0,
          totalPages: 0,
        }),
      ),
      http.get(base + "/api/categories", () =>
        HttpResponse.json({ items: [] }),
      ),
    );
    view(<HomePage />);
    await screen.findByText("New arrivals are on their way");
    expect(
      await screen.findByRole("link", { name: "Open your dashboard" }),
    ).toHaveAttribute("href", "/vendor");
    expect(
      screen.queryByRole("link", { name: "View your orders" }),
    ).not.toBeInTheDocument();
  });
});
