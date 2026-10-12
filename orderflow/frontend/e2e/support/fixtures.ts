import type { Page, Route } from "@playwright/test";
import type { Role } from "../../types/contracts";
const now = "2026-10-12T06:00:00Z";
export const productId = "10000000-0000-4000-8000-000000000001";
export const orderId = "20000000-0000-4000-8000-000000000001";
const category = {
  id: "30000000-0000-4000-8000-000000000001",
  name: "Desk essentials",
  slug: "desk-essentials",
  description: "Useful objects for everyday work",
  status: "ACTIVE",
  version: 0,
  createdAt: now,
  updatedAt: now,
};
const product = {
  id: productId,
  vendorId: "vendor-user",
  sku: "TEST-DESK-001",
  name: "Everyday desk lamp",
  slug: "everyday-desk-lamp",
  description: "A thoughtful desk essential.",
  priceAmount: "39.50",
  currency: "USD",
  category,
  status: "ACTIVE",
  version: 0,
  createdAt: now,
  updatedAt: now,
};
const address = {
  recipientName: "Fixture Reader",
  addressLine1: "123 Test Street",
  addressLine2: "",
  city: "Austin",
  stateOrRegion: "Texas",
  postalCode: "78701",
  countryCode: "US",
  deliveryInstructions: "Side entrance",
};
const emptyPage = (items: unknown[] = [], content = false) => ({
  [content ? "content" : "items"]: items,
  page: 0,
  size: 20,
  totalElements: items.length,
  totalPages: items.length ? 1 : 0,
});
export async function fixtures(page: Page, initialRole?: Role) {
  let role = initialRole;
  let cartItems: Record<string, unknown>[] = [];
  let orderExists = false;
  let status = "PAYMENT_PENDING";
  let paymentStatus = "PENDING";
  let productStatus = "ACTIVE";
  let stock = 10;
  let availability = "OFFLINE";
  let deliveryStatus = "ASSIGNED";
  let read = false;
  const user = () => ({
    id: "40000000-0000-4000-8000-000000000001",
    email: "reader@example.test",
    displayName: "Fixture Reader",
    status: "ACTIVE",
    roles: [role ?? "CUSTOMER"],
    createdAt: now,
  });
  const auth = () => ({
    accessToken: "fixture-access-token",
    tokenType: "Bearer",
    expiresIn: 900,
    user: user(),
  });
  const cart = () => ({
    userId: user().id,
    version: cartItems.length + 1,
    currency: cartItems.length ? "USD" : null,
    items: cartItems,
    distinctItemCount: cartItems.length,
    totalQuantity: cartItems.reduce((n, x) => n + Number(x.quantity), 0),
    subtotal: cartItems
      .reduce((n, x) => n + Number(x.quantity) * 39.5, 0)
      .toFixed(2),
    createdAt: now,
    updatedAt: now,
    expiresAt: "2026-10-13T06:00:00Z",
  });
  const order = () => ({
    id: orderId,
    customerId: user().id,
    status,
    fulfillmentStatus: "NOT_REQUESTED",
    shippingAddress: address,
    currency: "USD",
    subtotal: "79.00",
    totalAmount: "79.00",
    cartVersion: 2,
    items: [
      {
        productId,
        productName: product.name,
        quantity: 2,
        unitPrice: "39.50",
        lineTotal: "79.00",
        currency: "USD",
        productVersion: 0,
      },
    ],
    paymentId: "50000000-0000-4000-8000-000000000001",
    inventoryReservationId: "60000000-0000-4000-8000-000000000001",
    inventoryReservationExpiresAt: now,
    cancellationReason: null,
    refundedAmount: "0.00",
    deliveryId: null,
    deliveryAgentId: null,
    createdAt: now,
    updatedAt: now,
    version: 1,
  });
  const payment = () => ({
    id: "50000000-0000-4000-8000-000000000001",
    orderId,
    status: paymentStatus,
    amount: "79.00",
    currency: "USD",
    provider: "SIMULATOR",
    refundedAmount: "0.00",
    failureCode: null,
    paymentDeadline: "2099-10-12T06:00:00Z",
    createdAt: now,
    updatedAt: now,
  });
  const delivery = () => ({
    id: "70000000-0000-4000-8000-000000000001",
    orderId,
    status: deliveryStatus,
    deliveryAgentId: "80000000-0000-4000-8000-000000000001",
    shippingAddress: address,
    assignedAt: now,
    pickedUpAt: null,
    outForDeliveryAt: null,
    deliveredAt: null,
    failedAt: null,
    cancelledAt: null,
    version: 1,
  });
  async function respond(route: Route, statusCode: number, value?: unknown) {
    await route.fulfill({
      status: statusCode,
      headers: {
        "Access-Control-Allow-Origin": "http://localhost:3000",
        "Access-Control-Allow-Credentials": "true",
        "Access-Control-Allow-Headers":
          "Authorization,Content-Type,X-XSRF-TOKEN,X-Correlation-ID,Idempotency-Key",
        "Access-Control-Allow-Methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS",
        "Content-Type": "application/json",
      },
      body: value === undefined ? "" : JSON.stringify(value),
    });
  }
  await page.route("http://localhost:8080/**", async (route) => {
    const r = route.request(),
      u = new URL(r.url()),
      p = u.pathname,
      m = r.method();
    if (m === "OPTIONS") return respond(route, 204);
    const body = () => r.postDataJSON() as Record<string, unknown>;
    if (p === "/api/auth/csrf")
      return respond(route, 200, {
        token: "fixture-csrf",
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      });
    if (p === "/api/auth/refresh")
      return role
        ? respond(route, 200, auth())
        : respond(route, 401, {
            code: "INVALID_REFRESH_TOKEN",
            message: "Sign in to continue.",
          });
    if (p === "/api/auth/login" || p === "/api/auth/register") {
      role ??= "CUSTOMER";
      return respond(route, p.endsWith("register") ? 201 : 200, auth());
    }
    if (p === "/api/auth/logout" || p === "/api/auth/logout-all") {
      role = undefined;
      return respond(route, 204);
    }
    if (p === "/api/auth/me") return respond(route, 200, user());
    if (p === "/api/categories")
      return respond(route, 200, { items: [category] });
    if (p === "/api/products")
      return respond(
        route,
        200,
        emptyPage(u.searchParams.get("q") === "missing" ? [] : [product]),
      );
    if (p === "/api/products/" + productId) return respond(route, 200, product);
    if (p.startsWith("/api/vendor/products")) {
      if (m === "PATCH")
        productStatus = p.endsWith("archive")
          ? "ARCHIVED"
          : p.endsWith("deactivate")
            ? "INACTIVE"
            : "ACTIVE";
      if (m === "POST" || m === "PUT" || m === "PATCH")
        return respond(route, m === "POST" ? 201 : 200, {
          ...product,
          ...body(),
          status: productStatus,
        });
      return respond(
        route,
        200,
        emptyPage([{ ...product, status: productStatus, vendorId: user().id }]),
      );
    }
    if (p === "/api/cart" && m === "DELETE") {
      cartItems = [];
      return respond(route, 204);
    }
    if (p === "/api/cart") return respond(route, 200, cart());
    if (p === "/api/cart/validate")
      return respond(route, 200, {
        cart: cart(),
        valid: cartItems.length > 0,
        issues: [],
      });
    if (p.startsWith("/api/cart/items")) {
      if (m === "POST")
        cartItems = [
          {
            productId,
            productNameSnapshot: product.name,
            quantity: Number(body().quantity),
            unitPriceSnapshot: "39.50",
            currency: "USD",
            productVersionSnapshot: 0,
            purchasable: true,
            priceChanged: false,
            addedAt: now,
            updatedAt: now,
          },
        ];
      if (m === "PATCH") cartItems[0].quantity = Number(body().quantity);
      if (m === "DELETE") cartItems = [];
      return respond(route, 200, cart());
    }
    if (p === "/api/orders" && m === "POST") {
      orderExists = true;
      return respond(route, 202, order());
    }
    if (p === "/api/orders")
      return respond(route, 200, emptyPage(orderExists ? [order()] : []));
    if (p.endsWith("/cancel")) {
      status = "CANCEL_REQUESTED";
      return respond(route, 202, order());
    }
    if (p === "/api/orders/" + orderId + "/history")
      return respond(route, 200, [
        {
          previousStatus: "INVENTORY_PENDING",
          newStatus: status,
          reasonCode: "INVENTORY_RESERVED",
          eventId: "history",
          correlationId: "fixture-trace",
          changedAt: now,
        },
      ]);
    if (p === "/api/orders/" + orderId) return respond(route, 200, order());
    if (p.startsWith("/api/payments/")) {
      if (p.endsWith("/confirm")) {
        paymentStatus =
          body().paymentMethodToken === "tok_declined"
            ? "FAILED"
            : body().paymentMethodToken === "tok_requires_action"
              ? "REQUIRES_ACTION"
              : "PROCESSING";
        if (paymentStatus === "FAILED")
          return respond(route, 402, {
            code: "PAYMENT_DECLINED",
            message: "The simulator declined this payment.",
          });
      }
      return respond(route, 200, payment());
    }
    if (p.startsWith("/api/inventory/products/")) {
      if (p.endsWith("/adjustments") && m === "POST")
        stock += Number(body().quantityDelta);
      if (p.endsWith("/adjustments") && m === "GET")
        return respond(route, 200, emptyPage([]));
      return respond(route, 200, {
        productId,
        onHandQuantity: stock,
        reservedQuantity: 0,
        availableQuantity: stock,
        version: 0,
        updatedAt: now,
      });
    }
    if (p === "/api/delivery/agents/me/availability") {
      availability = String(body().availability);
      return respond(route, 200, {
        userId: user().id,
        profileId: "80000000-0000-4000-8000-000000000001",
        availability,
        activeDeliveryId: delivery().id,
        updatedAt: now,
      });
    }
    if (p === "/api/delivery/agents/me")
      return respond(route, 200, {
        userId: user().id,
        profileId: "80000000-0000-4000-8000-000000000001",
        availability,
        activeDeliveryId: delivery().id,
        updatedAt: now,
      });
    if (p === "/api/delivery/agents/me/deliveries")
      return respond(route, 200, emptyPage([delivery()], true));
    if (p.endsWith("/address")) return respond(route, 200, address);
    if (p.startsWith("/api/delivery/agents/me/deliveries/")) {
      if (m === "POST")
        deliveryStatus = p.endsWith("pickup")
          ? "PICKED_UP"
          : p.endsWith("out-for-delivery")
            ? "OUT_FOR_DELIVERY"
            : p.endsWith("delivered")
              ? "DELIVERED"
              : "FAILED";
      return respond(route, 200, delivery());
    }
    if (p.startsWith("/api/deliveries/"))
      return respond(route, 404, {
        code: "DELIVERY_NOT_FOUND",
        message: "Delivery is not available yet.",
      });
    if (p === "/api/notifications/stream")
      return route.fulfill({
        status: 200,
        headers: {
          "Content-Type": "text/event-stream",
          "Access-Control-Allow-Origin": "http://localhost:3000",
          "Access-Control-Allow-Credentials": "true",
        },
        body: 'event: connected\ndata: {"connected":true}\n\n',
      });
    if (p === "/api/notifications/unread-count")
      return respond(route, 200, { count: read ? 0 : 1 });
    if (p === "/api/notifications/preferences")
      return respond(route, 200, {
        emailEnabled: false,
        emailAddress: null,
        smsEnabled: false,
        phoneNumber: null,
        ...(m === "PUT" ? body() : {}),
        updatedAt: now,
        version: 0,
      });
    if (p.endsWith("/read-all") || p.endsWith("/read")) {
      read = true;
      return respond(route, 200, { count: 0 });
    }
    if (p === "/api/notifications")
      return respond(
        route,
        200,
        emptyPage(
          [
            {
              id: "90000000-0000-4000-8000-000000000001",
              type: "ORDER_PLACED",
              title: "Order received",
              message: "Your order is being prepared.",
              read,
              createdAt: now,
              readAt: read ? now : null,
            },
          ],
          true,
        ),
      );
    if (p === "/api/admin/categories") {
      if (m === "POST") return respond(route, 201, { ...category, ...body() });
      return respond(route, 200, emptyPage([category]));
    }
    if (p.startsWith("/api/admin/categories/"))
      return respond(route, 200, { ...category, ...body() });
    if (p === "/api/admin/orders")
      return respond(route, 200, emptyPage([order()]));
    if (p === "/api/admin/payments")
      return respond(route, 200, emptyPage([payment()], true));
    if (p.endsWith("/refunds"))
      return respond(
        route,
        m === "POST" ? 202 : 200,
        m === "POST"
          ? {
              id: "refund",
              paymentId: payment().id,
              amount: body().amount,
              currency: "USD",
              status: "PROCESSING",
              reason: body().reason,
              createdAt: now,
              updatedAt: now,
            }
          : emptyPage([], true),
      );
    if (p.startsWith("/api/admin/payments/"))
      return respond(route, 200, {
        payment: payment(),
        attempts: [],
        refunds: [],
      });
    if (p === "/api/admin/operations/overview")
      return respond(route, 200, {
        date: "2026-10-12",
        ordersCreated: 4,
        paymentsSucceeded: 2,
        paymentsFailed: 1,
        paymentVolume: "100.00",
        inventoryReservationFailures: 0,
        deliveriesPendingAssignment: 1,
        deliveriesCompleted: 1,
        deliveriesFailed: 0,
        notificationsFailed: 0,
        openIncidents: 0,
      });
    if (p === "/api/admin/operations/jobs")
      return respond(route, 200, [
        {
          jobName: "dailyReport",
          lastStatus: "SUCCEEDED",
          lastStartedAt: now,
          lastCompletedAt: now,
          lastDurationMs: 5,
          failureCount: 0,
        },
      ]);
    if (p.startsWith("/api/admin/operations/"))
      return respond(route, 200, emptyPage([], true));
    return respond(route, 404, {
      code: "NOT_FOUND",
      message: "This controlled fixture has no matching endpoint.",
    });
  });
}
