"use client";
import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { api } from "@/lib/api";
import { date, money } from "@/lib/formatting";
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Field,
  Input,
  LoadingState,
  PageHeader,
  Pagination,
  Select,
  StatusBadge,
  Table,
} from "@/components/ui";
import type { QueryParams } from "@/types/contracts";
import { instantInput, orderStatuses, StatusOptions, useAdmin } from "./common";
const schema = z
  .object({
    customerId: z.union([z.literal(""), z.uuid("Enter a valid customer UUID")]),
    status: z.string(),
    createdFrom: z.string(),
    createdTo: z.string(),
    sort: z.enum(["createdAt,desc", "createdAt,asc"]),
  })
  .refine(
    (value) =>
      !value.createdFrom ||
      !value.createdTo ||
      value.createdFrom <= value.createdTo,
    { message: "End date must be on or after start date", path: ["createdTo"] },
  );
export function AdminOrdersPage() {
  const { userId, enabled } = useAdmin();
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<QueryParams>({
    sort: "createdAt,desc",
  });
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      customerId: "",
      status: "",
      createdFrom: "",
      createdTo: "",
      sort: "createdAt,desc",
    },
  });
  const query = useQuery({
    queryKey: ["admin", userId, "orders", filters, page],
    queryFn: () => api.admin.orders({ ...filters, page, size: 20 }),
    enabled,
  });
  return (
    <div className="stack">
      <PageHeader
        title="Orders"
        description="Inspect authorized order records and their independent financial and fulfillment states."
      />
      <Card>
        <form
          onSubmit={form.handleSubmit((value) => {
            setFilters({
              ...value,
              customerId: value.customerId || undefined,
              createdFrom: instantInput(value.createdFrom),
              createdTo: instantInput(value.createdTo, true),
            });
            setPage(0);
          })}
          noValidate
        >
          <div className="form-grid">
            <Field
              label="Customer ID"
              error={form.formState.errors.customerId?.message}
            >
              <Input
                {...form.register("customerId")}
                placeholder="Customer UUID"
              />
            </Field>
            <Field label="Financial status">
              <Select {...form.register("status")}>
                <option value="">All financial statuses</option>
                <StatusOptions values={orderStatuses} />
              </Select>
            </Field>
            <Field label="Created from (UTC)">
              <Input type="date" {...form.register("createdFrom")} />
            </Field>
            <Field
              label="Created to (UTC)"
              error={form.formState.errors.createdTo?.message}
            >
              <Input type="date" {...form.register("createdTo")} />
            </Field>
            <Field label="Sort orders">
              <Select {...form.register("sort")}>
                <option value="createdAt,desc">Newest first</option>
                <option value="createdAt,asc">Oldest first</option>
              </Select>
            </Field>
          </div>
          <Button type="submit">Apply filters</Button>
        </form>
      </Card>
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => void query.refetch()} />
      ) : (
        <>
          {query.data.items.length === 0 ? (
            <EmptyState
              title="No matching orders"
              description="Try a different financial status, customer, or date range."
            />
          ) : (
            <Table
              headers={[
                "Order",
                "Customer",
                "Financial status",
                "Fulfillment status",
                "Amount",
                "Refunded",
                "Created",
              ]}
            >
              {query.data.items.map((order) => (
                <tr key={order.id}>
                  <td>
                    <Link href={`/admin/orders/${order.id}`}>{order.id}</Link>
                  </td>
                  <td>{order.customerId}</td>
                  <td>
                    <StatusBadge status={order.status} />
                  </td>
                  <td>
                    <StatusBadge status={order.fulfillmentStatus} />
                  </td>
                  <td>{money(order.totalAmount, order.currency)}</td>
                  <td>{money(order.refundedAmount, order.currency)}</td>
                  <td>{date(order.createdAt)}</td>
                </tr>
              ))}
            </Table>
          )}
          <Pagination
            page={page}
            totalPages={query.data.totalPages}
            totalElements={query.data.totalElements}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}
export function AdminOrderDetail({ orderId }: { orderId: string }) {
  const { userId, enabled } = useAdmin();
  const orderQuery = useQuery({
    queryKey: ["admin", userId, "order", orderId],
    queryFn: () => api.orders.get(orderId),
    enabled,
  });
  const history = useQuery({
    queryKey: ["admin", userId, "orderHistory", orderId],
    queryFn: () => api.orders.history(orderId),
    enabled: enabled && orderQuery.isSuccess,
  });
  if (orderQuery.isPending) return <LoadingState />;
  if (orderQuery.isError)
    return (
      <ErrorState
        error={orderQuery.error}
        retry={() => void orderQuery.refetch()}
      />
    );
  const order = orderQuery.data;
  const address = order.shippingAddress;
  const milestones = [
    ["Delivery requested", order.deliveryRequestedAt],
    ["Assigned", order.deliveryAssignedAt],
    ["Picked up", order.pickedUpAt],
    ["Out for delivery", order.outForDeliveryAt],
    ["Delivered", order.deliveredAt],
    ["Delivery failed", order.deliveryFailedAt],
    ["Delivery cancelled", order.deliveryCancelledAt],
  ] as const;
  return (
    <div className="stack">
      <PageHeader
        title="Order details"
        description={order.id}
        actions={
          <>
            <Link href="/admin/orders" className="btn btn-secondary">
              All orders
            </Link>
            <Button
              variant="secondary"
              pending={orderQuery.isFetching}
              onClick={() => {
                void orderQuery.refetch();
                void history.refetch();
              }}
            >
              Refresh order
            </Button>
          </>
        }
      />
      <div className="split">
        <Card title="Order overview">
          <div className="summary-line">
            <span>Financial status</span>
            <StatusBadge status={order.status} />
          </div>
          <div className="summary-line">
            <span>Fulfillment status</span>
            <StatusBadge status={order.fulfillmentStatus} />
          </div>
          <div className="summary-line">
            <span>Total amount</span>
            <strong>{money(order.totalAmount, order.currency)}</strong>
          </div>
          <div className="summary-line">
            <span>Refunded amount</span>
            <span>{money(order.refundedAmount, order.currency)}</span>
          </div>
          <p>Customer ID: {order.customerId}</p>
          <p className="small muted">
            Created {date(order.createdAt)}
            <br />
            Updated {date(order.updatedAt)} · Version {order.version}
          </p>
          <p>Cancellation reason: {order.cancellationReason ?? "—"}</p>
          <p>Payment failure code: {order.paymentFailureCode ?? "—"}</p>
          {order.paymentId && (
            <Link
              className="btn btn-secondary"
              href={`/admin/payments/${order.paymentId}`}
            >
              Inspect payment
            </Link>
          )}
        </Card>
        <Card title="Shipping address">
          <p>{address.recipientName}</p>
          <p>{address.addressLine1}</p>
          {address.addressLine2 && <p>{address.addressLine2}</p>}
          <p>
            {address.city}, {address.stateOrRegion} {address.postalCode}
            <br />
            {address.countryCode}
          </p>
          {address.deliveryInstructions && (
            <p>Instructions: {address.deliveryInstructions}</p>
          )}
        </Card>
      </div>
      <Card title="Order items">
        {order.items.length ? (
          <Table headers={["Product", "Quantity", "Unit price", "Line total"]}>
            {order.items.map((item, index) => (
              <tr key={`${item.productId}-${index}`}>
                <td>
                  {item.productName}
                  <p className="small muted">{item.productId}</p>
                </td>
                <td>{item.quantity}</td>
                <td>{money(item.unitPrice, item.currency)}</td>
                <td>{money(item.lineTotal, item.currency)}</td>
              </tr>
            ))}
          </Table>
        ) : (
          <EmptyState title="No item details returned" />
        )}
        <div className="summary-line">
          <span>Subtotal</span>
          <span>{money(order.subtotal, order.currency)}</span>
        </div>
      </Card>
      <div className="split">
        <Card title="Financial timeline">
          <p>Inventory reservation: {order.inventoryReservationId ?? "—"}</p>
          <p>
            Reservation expires: {date(order.inventoryReservationExpiresAt)}
          </p>
          <p>Paid: {date(order.paidAt)}</p>
          <p>Payment failed: {date(order.paymentFailedAt)}</p>
          <p>Refund confirmed: {date(order.refundedAt)}</p>
          <p>Inventory released: {date(order.inventoryReleasedAt)}</p>
        </Card>
        <Card title="Fulfillment timeline">
          <p>Delivery ID: {order.deliveryId ?? "Not requested"}</p>
          <p>Agent ID: {order.deliveryAgentId ?? "Unassigned"}</p>
          {milestones.map(([caption, value]) => (
            <div className="summary-line" key={caption}>
              <span>{caption}</span>
              <span>{date(value)}</span>
            </div>
          ))}
          <Link
            className="btn btn-secondary"
            href={`/admin/deliveries?orderId=${encodeURIComponent(order.id)}`}
          >
            Inspect delivery
          </Link>
        </Card>
      </div>
      <Card title="Financial status history">
        {history.isPending ? (
          <LoadingState />
        ) : history.isError ? (
          <ErrorState
            error={history.error}
            retry={() => void history.refetch()}
          />
        ) : history.data.length === 0 ? (
          <EmptyState title="No order history" />
        ) : (
          <Table
            headers={[
              "Previous",
              "New status",
              "Reason code",
              "Changed",
              "Correlation reference",
            ]}
          >
            {history.data.map((item, index) => (
              <tr key={`${item.changedAt}-${index}`}>
                <td>
                  {item.previousStatus ? (
                    <StatusBadge status={item.previousStatus} />
                  ) : (
                    "—"
                  )}
                </td>
                <td>
                  <StatusBadge status={item.newStatus} />
                </td>
                <td>{item.reasonCode}</td>
                <td>{date(item.changedAt)}</td>
                <td>{item.correlationId}</td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
    </div>
  );
}
