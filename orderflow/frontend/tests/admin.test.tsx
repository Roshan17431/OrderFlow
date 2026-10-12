import {
  beforeAll,
  afterAll,
  afterEach,
  beforeEach,
  describe,
  it,
  expect,
} from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { AuthProvider } from "@/providers/auth-provider";
import { session } from "@/lib/auth/session";
import { RefundForm } from "@/features/admin/payments";
import { CategoryEditor } from "@/features/admin/categories";
import { DeliveryPanel } from "@/features/admin/deliveries";
import { IncidentsPage, AdminOverview } from "@/features/admin/operations";
import type { Payment, Category, Delivery } from "@/types/contracts";
const base = "http://localhost:8080";
const payment: Payment = {
  id: "payment-1",
  orderId: "order-1",
  status: "PARTIALLY_REFUNDED",
  amount: "0.30",
  currency: "USD",
  provider: "SIMULATOR",
  refundedAmount: "0.10",
  failureCode: null,
  paymentDeadline: "2026-10-12T12:00:00Z",
  createdAt: "2026-10-12T10:00:00Z",
  updatedAt: "2026-10-12T10:00:00Z",
  succeededAt: "2026-10-12T10:00:00Z",
  failedAt: null,
  cancelledAt: null,
};
const category: Category = {
  id: "category-1",
  name: "Home",
  slug: "home",
  description: "Useful things",
  status: "ACTIVE",
  version: 7,
  createdAt: "2026-10-12T10:00:00Z",
  updatedAt: "2026-10-12T10:00:00Z",
};
const delivery: Delivery = {
  id: "delivery-1",
  orderId: "order-1",
  status: "DELIVERED",
  deliveryAgentId: "agent-1",
  shippingAddress: {
    recipientName: "Test recipient",
    city: "Pune",
    stateOrRegion: "Maharashtra",
    countryCode: "IN",
  },
  assignedAt: null,
  pickedUpAt: null,
  outForDeliveryAt: null,
  deliveredAt: "2026-10-12T11:00:00Z",
  failedAt: null,
  cancelledAt: null,
  version: 5,
};
const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterAll(() => server.close());
afterEach(() => server.resetHandlers());
beforeEach(() => {
  session.clear();
  session.set({
    accessToken: "test-token",
    tokenType: "Bearer",
    expiresIn: 900,
    user: {
      id: "admin-1",
      email: "admin@example.test",
      displayName: "Admin",
      status: "ACTIVE",
      roles: ["ADMIN"],
      createdAt: "2026-10-12T10:00:00Z",
    },
  });
  document.cookie = "XSRF-TOKEN=test-token";
});
function view(children: React.ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>,
  );
}
it("refreshes public and vendor category choices after a real category mutation", async () => {
  server.use(
    http.post(base + "/api/admin/categories", () =>
      HttpResponse.json(category, { status: 201 }),
    ),
  );
  const client = new QueryClient();
  for (const key of [
    ["categories", "public"],
    ["products", "categories"],
    ["catalog", "recent"],
  ])
    client.setQueryData(key, [category]);
  render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <CategoryEditor />
      </AuthProvider>
    </QueryClientProvider>,
  );
  await userEvent.type(screen.getByLabelText("Category name"), "Home");
  await userEvent.type(screen.getByLabelText("Category slug"), "home");
  await userEvent.click(
    screen.getByRole("button", { name: "Create category" }),
  );
  expect(await screen.findByText("Category saved.")).toBeVisible();
  for (const key of [
    ["categories", "public"],
    ["products", "categories"],
    ["catalog", "recent"],
  ])
    expect(client.getQueryState(key)?.isInvalidated).toBe(true);
});
describe("refund safety", () => {
  it.each(["0", "0.201", "0.21", "-0.10"])(
    "blocks invalid or excessive amount %s before sending",
    async (amount) => {
      const user = userEvent.setup();
      view(<RefundForm payment={payment} />);
      await user.type(screen.getByLabelText("Refund amount"), amount);
      await user.selectOptions(
        screen.getByLabelText("Refund reason"),
        "CUSTOMER_REQUEST",
      );
      await user.click(screen.getByRole("button", { name: "Request refund" }));
      expect(await screen.findByRole("alert")).toHaveTextContent(
        /positive|two decimal|remaining/i,
      );
    },
  );
  it("uses exact remaining decimal and retains the same key and body after an uncertain result", async () => {
    const requests: { key: string | null; body: unknown }[] = [];
    server.use(
      http.post(
        base + "/api/admin/payments/payment-1/refunds",
        async ({ request }) => {
          requests.push({
            key: request.headers.get("Idempotency-Key"),
            body: await request.json(),
          });
          return requests.length === 1
            ? HttpResponse.json(
                {
                  code: "REFUND_PROVIDER_UNAVAILABLE",
                  message: "Provider unavailable",
                },
                { status: 503 },
              )
            : HttpResponse.json(
                {
                  id: "refund-1",
                  paymentId: "payment-1",
                  amount: "0.20",
                  currency: "USD",
                  status: "PROCESSING",
                  reason: "CUSTOMER_REQUEST",
                  failureCode: null,
                  createdAt: "2026-10-12T10:00:00Z",
                  updatedAt: "2026-10-12T10:00:00Z",
                  succeededAt: null,
                  failedAt: null,
                },
                { status: 202 },
              );
        },
      ),
    );
    const user = userEvent.setup();
    view(<RefundForm payment={payment} />);
    await user.click(
      screen.getByRole("button", { name: "Use full remaining amount" }),
    );
    expect(screen.getByLabelText("Refund amount")).toHaveValue("0.20");
    await user.selectOptions(
      screen.getByLabelText("Refund reason"),
      "CUSTOMER_REQUEST",
    );
    await user.click(screen.getByRole("button", { name: "Request refund" }));
    await screen.findByText(/result is uncertain/i);
    expect(screen.getByLabelText("Refund amount")).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Retry same refund" }));
    expect(await screen.findByText("Processing")).toBeVisible();
    expect(requests).toHaveLength(2);
    expect(requests[0].key).toBeTruthy();
    expect(requests[1]).toEqual(requests[0]);
    expect(requests[0].body).toEqual({
      amount: "0.20",
      reason: "CUSTOMER_REQUEST",
    });
    expect(screen.queryByText("Succeeded")).not.toBeInTheDocument();
  });
  it("does not offer a refund for a payment awaiting provider confirmation", () => {
    view(<RefundForm payment={{ ...payment, status: "REFUND_PENDING" }} />);
    expect(
      screen.queryByRole("button", { name: "Request refund" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText(/provider confirmation/i)).toBeVisible();
  });
});
it("sends the category slug and current version when updating", async () => {
  let body: unknown;
  server.use(
    http.put(base + "/api/admin/categories/category-1", async ({ request }) => {
      body = await request.json();
      return HttpResponse.json({ ...category, name: "New home", version: 8 });
    }),
  );
  const user = userEvent.setup();
  view(<CategoryEditor category={category} />);
  await user.clear(screen.getByLabelText("Category name"));
  await user.type(screen.getByLabelText("Category name"), "New home");
  await user.click(screen.getByRole("button", { name: "Save category" }));
  await screen.findByText("Category saved.");
  expect(body).toEqual({
    name: "New home",
    slug: "home",
    description: "Useful things",
    version: 7,
  });
});
it("does not offer manual assignment for a delivered delivery and shows backend history", async () => {
  server.use(
    http.get(base + "/api/admin/deliveries/delivery-1/history", () =>
      HttpResponse.json({
        content: [
          {
            previousStatus: "OUT_FOR_DELIVERY",
            newStatus: "DELIVERED",
            actorUserId: "agent-1",
            sourceEventId: null,
            correlationId: "correlation-1",
            changedAt: "2026-10-12T11:00:00Z",
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    ),
  );
  view(<DeliveryPanel delivery={delivery} />);
  expect(
    screen.queryByRole("button", { name: "Assign delivery" }),
  ).not.toBeInTheDocument();
  expect(await screen.findByText("correlation-1")).toBeVisible();
});
it("reads snake case incident fields and submits a real resolution note", async () => {
  let body: unknown;
  let resolved = false;
  server.use(
    http.get(base + "/api/admin/operations/incidents", () =>
      HttpResponse.json({
        content: [
          {
            id: "incident-1",
            incident_type: "PAYMENT_FAILURE",
            severity: "HIGH",
            source_service: "payment-service",
            reason_code: "PROVIDER_TIMEOUT",
            occurred_at: "2026-10-12T10:00:00Z",
            resolved,
            resolved_at: null,
            resolution_note: null,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    ),
    http.patch(
      base + "/api/admin/operations/incidents/incident-1/resolve",
      async ({ request }) => {
        body = await request.json();
        resolved = true;
        return new HttpResponse(null, { status: 204 });
      },
    ),
  );
  const user = userEvent.setup();
  view(<IncidentsPage />);
  expect(await screen.findByText("payment-service")).toBeVisible();
  expect(screen.getByText("PROVIDER_TIMEOUT")).toBeVisible();
  await user.click(screen.getByRole("button", { name: "Resolve incident" }));
  await user.type(
    screen.getByLabelText("Resolution note"),
    "Checked provider recovery.",
  );
  await user.click(screen.getByRole("button", { name: "Confirm resolution" }));
  await screen.findByRole("cell", { name: /^Resolved/ });
  expect(body).toEqual({ resolutionNote: "Checked provider recovery." });
});
it("shows actual overview metrics and treats missing daily refund report as unavailable", async () => {
  server.use(
    http.get(base + "/api/admin/operations/overview", () =>
      HttpResponse.json({
        date: "2026-10-12",
        ordersCreated: 2,
        paymentsSucceeded: 1,
        paymentsFailed: 3,
        paymentVolume: "45.50",
        inventoryReservationFailures: 4,
        deliveriesPendingAssignment: 5,
        deliveriesCompleted: 6,
        deliveriesFailed: 7,
        notificationsFailed: 8,
        openIncidents: 9,
      }),
    ),
    http.get(base + "/api/admin/operations/reports/daily", () =>
      HttpResponse.json({
        content: [],
        page: 0,
        size: 1,
        totalElements: 0,
        totalPages: 0,
      }),
    ),
  );
  view(<AdminOverview />);
  expect(await screen.findByText("45.50")).toBeVisible();
  expect(await screen.findByText("No report for this date")).toBeVisible();
  expect(screen.getByText("Pending assignments")).toBeVisible();
});
it("keeps financial and fulfillment status independent on an authorized order detail", async () => {
  const { AdminOrderDetail } = await import("@/features/admin/orders");
  server.use(
    http.get(base + "/api/orders/order-1", () =>
      HttpResponse.json({
        id: "order-1",
        customerId: "customer-1",
        status: "PARTIALLY_REFUNDED",
        fulfillmentStatus: "DELIVERED",
        shippingAddress: {
          recipientName: "Test recipient",
          addressLine1: "12 Example Lane",
          addressLine2: null,
          city: "Pune",
          stateOrRegion: "Maharashtra",
          postalCode: "411001",
          countryCode: "IN",
          deliveryInstructions: null,
        },
        currency: "USD",
        subtotal: "45.50",
        totalAmount: "45.50",
        cartVersion: 1,
        cancellationReason: null,
        inventoryReservationId: null,
        inventoryReservationExpiresAt: null,
        paymentId: "payment-1",
        paidAt: null,
        paymentFailedAt: null,
        paymentFailureCode: null,
        refundedAmount: "10.00",
        refundedAt: null,
        inventoryReleasedAt: null,
        deliveryId: "delivery-1",
        deliveryAgentId: "agent-1",
        deliveryRequestedAt: null,
        deliveryAssignedAt: null,
        pickedUpAt: null,
        outForDeliveryAt: null,
        deliveredAt: "2026-10-12T11:00:00Z",
        deliveryFailedAt: null,
        deliveryCancelledAt: null,
        items: [],
        createdAt: "2026-10-12T10:00:00Z",
        updatedAt: "2026-10-12T11:00:00Z",
        version: 4,
      }),
    ),
    http.get(base + "/api/orders/order-1/history", () =>
      HttpResponse.json([
        {
          previousStatus: "PAID",
          newStatus: "PARTIALLY_REFUNDED",
          reasonCode: "REFUND_SUCCEEDED",
          eventId: null,
          correlationId: "order-correlation",
          changedAt: "2026-10-12T11:00:00Z",
        },
      ]),
    ),
  );
  view(<AdminOrderDetail orderId="order-1" />);
  expect(await screen.findByText("Financial status")).toBeVisible();
  expect(await screen.findByText("Fulfillment status")).toBeVisible();
  expect(screen.getByText("Delivered", { selector: ".badge" })).toBeVisible();
  expect(screen.getByText("12 Example Lane")).toBeVisible();
  expect(await screen.findByText("order-correlation")).toBeVisible();
  expect(
    screen.queryByRole("button", { name: /change.*status/i }),
  ).not.toBeInTheDocument();
});
it("uses supported order status filters and resets pagination after filtering", async () => {
  const { AdminOrdersPage } = await import("@/features/admin/orders");
  const requests: URL[] = [];
  server.use(
    http.get(base + "/api/admin/orders", ({ request }) => {
      requests.push(new URL(request.url));
      return HttpResponse.json({
        items: [],
        page: requests.at(-1)!.searchParams.get("page") === "1" ? 1 : 0,
        size: 20,
        totalElements: 40,
        totalPages: 2,
      });
    }),
  );
  const user = userEvent.setup();
  view(<AdminOrdersPage />);
  await screen.findByText("No matching orders");
  await user.click(screen.getByRole("button", { name: "Next page" }));
  await screen.findByText("40 results · Page 2 of 2");
  await user.selectOptions(screen.getByLabelText("Financial status"), "PAID");
  await user.click(screen.getByRole("button", { name: "Apply filters" }));
  await screen.findByText("40 results · Page 1 of 2");
  expect(requests.at(-1)!.searchParams.get("status")).toBe("PAID");
  expect(requests.at(-1)!.searchParams.get("page")).toBe("0");
  expect(requests.at(-1)!.searchParams.has("fulfillmentStatus")).toBe(false);
});
it("requires a supported refund reason before creating a refund", async () => {
  const user = userEvent.setup();
  view(<RefundForm payment={payment} />);
  await user.type(screen.getByLabelText("Refund amount"), "0.10");
  await user.click(screen.getByRole("button", { name: "Request refund" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Choose a refund reason",
  );
});
it("refreshes an accepted refund result from authoritative detail before offering another request", async () => {
  server.use(
    http.post(base + "/api/admin/payments/payment-1/refunds", () =>
      HttpResponse.json(
        {
          id: "refund-1",
          paymentId: "payment-1",
          amount: "0.10",
          currency: "USD",
          status: "PROCESSING",
          reason: "CUSTOMER_REQUEST",
          failureCode: null,
          createdAt: "2026-10-12T10:00:00Z",
          updatedAt: "2026-10-12T10:00:00Z",
          succeededAt: null,
          failedAt: null,
        },
        { status: 202 },
      ),
    ),
  );
  const user = userEvent.setup();
  const confirmed = {
    id: "refund-1",
    paymentId: "payment-1",
    amount: "0.10",
    currency: "USD",
    status: "SUCCEEDED" as const,
    reason: "CUSTOMER_REQUEST",
    failureCode: null,
    createdAt: "2026-10-12T10:00:00Z",
    updatedAt: "2026-10-12T11:00:00Z",
    succeededAt: "2026-10-12T11:00:00Z",
    failedAt: null,
  };
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = (children: React.ReactNode) => (
    <QueryClientProvider client={client}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  );
  const rendered = render(
    wrapper(<RefundForm payment={payment} refunds={[]} />),
  );
  await user.type(screen.getByLabelText("Refund amount"), "0.10");
  await user.selectOptions(
    screen.getByLabelText("Refund reason"),
    "CUSTOMER_REQUEST",
  );
  await user.click(screen.getByRole("button", { name: "Request refund" }));
  await screen.findByText("Processing");
  expect(
    screen.queryByRole("button", { name: "Start another refund" }),
  ).not.toBeInTheDocument();
  rendered.rerender(
    wrapper(
      <RefundForm
        payment={{ ...payment, refundedAmount: "0.20" }}
        refunds={[confirmed]}
      />,
    ),
  );
  expect(screen.getByText("Succeeded")).toBeVisible();
  await user.click(
    screen.getByRole("button", { name: "Start another refund" }),
  );
  expect(screen.getByRole("button", { name: "Request refund" })).toBeVisible();
  expect(screen.getByText("$ 0.10")).toBeVisible();
});
it("provides a reload action after a category version conflict", async () => {
  let reloaded = false;
  server.use(
    http.put(base + "/api/admin/categories/category-1", () =>
      HttpResponse.json(
        {
          code: "CATEGORY_VERSION_CONFLICT",
          message: "Category changed elsewhere",
        },
        { status: 409 },
      ),
    ),
  );
  const user = userEvent.setup();
  view(
    <CategoryEditor
      category={category}
      onReload={() => {
        reloaded = true;
      }}
    />,
  );
  await user.click(screen.getByRole("button", { name: "Save category" }));
  await screen.findByText("Category changed elsewhere");
  await user.click(
    screen.getByRole("button", { name: "Reload current category" }),
  );
  expect(reloaded).toBe(true);
});
it("shows agent availability conflicts without pretending assignment succeeded", async () => {
  server.use(
    http.get(base + "/api/admin/deliveries/delivery-1/history", () =>
      HttpResponse.json({
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      }),
    ),
    http.post(base + "/api/admin/deliveries/delivery-1/assign", () =>
      HttpResponse.json(
        {
          code: "DELIVERY_AGENT_NOT_AVAILABLE",
          message: "Delivery agent is not available",
        },
        { status: 409 },
      ),
    ),
  );
  const user = userEvent.setup();
  view(
    <DeliveryPanel
      delivery={{
        ...delivery,
        status: "PENDING_ASSIGNMENT",
        deliveryAgentId: null,
      }}
    />,
  );
  await user.type(
    screen.getByLabelText("Delivery agent profile ID"),
    "123e4567-e89b-42d3-a456-426614174000",
  );
  await user.click(screen.getByRole("button", { name: "Assign delivery" }));
  expect(
    await screen.findByText("Delivery agent is not available"),
  ).toBeVisible();
  expect(screen.getByText("Pending assignment")).toBeVisible();
  expect(
    screen.queryByText("Assigned", { selector: ".badge" }),
  ).not.toBeInTheDocument();
});
it("reads camel case daily reports and preserves exact monetary totals", async () => {
  const { ReportsPage } = await import("@/features/admin/operations");
  server.use(
    http.get(base + "/api/admin/operations/reports/daily", () =>
      HttpResponse.json({
        content: [
          {
            id: "report-1",
            reportDate: "2026-10-11",
            status: "FINALIZED",
            generatedAt: "2026-10-12T10:00:00Z",
            finalizedAt: "2026-10-12T11:00:00Z",
            ordersCreated: 2,
            paymentsSucceeded: 1,
            paymentsFailed: 3,
            paymentVolume: "99999999999999999.99",
            refundVolume: "0.10",
            inventoryReservationFailures: 4,
            deliveriesCompleted: 5,
            deliveriesFailed: 6,
            notificationsDelivered: 7,
            notificationsFailed: 8,
            version: 2,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    ),
  );
  view(<ReportsPage />);
  expect(await screen.findByText("99,999,999,999,999,999.99")).toBeVisible();
  expect(screen.getByText("0.10")).toBeVisible();
  expect(screen.getByText("Finalized", { selector: ".badge" })).toBeVisible();
});
it("reads camel case job state and treats never-run jobs as unavailable", async () => {
  const { JobsPage } = await import("@/features/admin/operations");
  server.use(
    http.get(base + "/api/admin/operations/jobs", () =>
      HttpResponse.json([
        {
          jobName: "daily_report",
          lastStatus: "SUCCEEDED",
          lastStartedAt: "2026-10-12T10:00:00Z",
          lastCompletedAt: "2026-10-12T10:00:01Z",
          lastDurationMs: 1000,
          failureCount: 2,
        },
        {
          jobName: "pending_assignment",
          lastStatus: null,
          lastStartedAt: null,
          lastCompletedAt: null,
          lastDurationMs: null,
          failureCount: 0,
        },
      ]),
    ),
  );
  view(<JobsPage />);
  expect(await screen.findByText("daily_report")).toBeVisible();
  expect(screen.getByText("1,000 ms")).toBeVisible();
  expect(screen.getByText("Not run")).toBeVisible();
  expect(
    screen.queryByRole("button", { name: /run job/i }),
  ).not.toBeInTheDocument();
});
it("refreshes refund history together with the backend payment after provider confirmation", async () => {
  const { PaymentDetailPage } = await import("@/features/admin/payments");
  let confirmed = false;
  const refund = {
    id: "refund-1",
    paymentId: "payment-1",
    amount: "0.10",
    currency: "USD",
    status: "PROCESSING",
    reason: "CUSTOMER_REQUEST",
    failureCode: null,
    createdAt: "2026-10-12T10:00:00Z",
    updatedAt: "2026-10-12T10:00:00Z",
    succeededAt: null,
    failedAt: null,
  };
  server.use(
    http.get(base + "/api/admin/payments/payment-1", () =>
      HttpResponse.json({
        payment: {
          ...payment,
          status: confirmed ? "PARTIALLY_REFUNDED" : "REFUND_PENDING",
          updatedAt: confirmed ? "2026-10-12T11:00:00Z" : payment.updatedAt,
        },
        attempts: [],
        refunds: [
          { ...refund, status: confirmed ? "SUCCEEDED" : "PROCESSING" },
        ],
      }),
    ),
    http.get(base + "/api/admin/payments/payment-1/refunds", () =>
      HttpResponse.json({
        content: [
          { ...refund, status: confirmed ? "SUCCEEDED" : "PROCESSING" },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    ),
  );
  const user = userEvent.setup();
  view(<PaymentDetailPage paymentId="payment-1" />);
  await screen.findByText("Processing", { selector: ".badge" });
  confirmed = true;
  await user.click(screen.getByRole("button", { name: "Refresh payment" }));
  expect(
    await screen.findByText("Succeeded", { selector: ".badge" }),
  ).toBeVisible();
});
it("assigns an available agent using the real profile UUID and the backend response", async () => {
  const profileId = "123e4567-e89b-42d3-a456-426614174000";
  let body: unknown;
  server.use(
    http.get(base + "/api/admin/deliveries/delivery-1/history", () =>
      HttpResponse.json({
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      }),
    ),
    http.post(
      base + "/api/admin/deliveries/delivery-1/assign",
      async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({
          ...delivery,
          status: "ASSIGNED",
          deliveryAgentId: profileId,
          version: 6,
        });
      },
    ),
  );
  const user = userEvent.setup();
  view(
    <DeliveryPanel
      delivery={{
        ...delivery,
        status: "PENDING_ASSIGNMENT",
        deliveryAgentId: null,
      }}
    />,
  );
  await user.type(
    screen.getByLabelText("Delivery agent profile ID"),
    profileId,
  );
  await user.click(screen.getByRole("button", { name: "Assign delivery" }));
  expect(
    await screen.findByText("Assigned", { selector: ".badge" }),
  ).toBeVisible();
  expect(body).toEqual({ deliveryAgentId: profileId });
  expect(
    screen.queryByRole("button", { name: "Assign delivery" }),
  ).not.toBeInTheDocument();
});
