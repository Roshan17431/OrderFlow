"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { date, label, money } from "@/lib/formatting";
import { useAuth } from "@/providers/auth-provider";
import {
  EmptyState,
  ErrorState,
  Field,
  LoadingState,
  PageHeader,
  Pagination,
  Select,
  StatusBadge,
  Table,
} from "@/components/ui";
import { catalogFilters } from "@/features/catalog/filters";
import { orderStatuses } from "./state";

export function OrdersPage() {
  const { user } = useAuth();
  const params = useSearchParams();
  const router = useRouter();
  const requestedStatus = params.get("status") ?? "";
  const status = orderStatuses.some((value) => value === requestedStatus)
    ? requestedStatus
    : "";
  const page = catalogFilters(new URLSearchParams(params.toString())).page;
  const orders = useQuery({
    queryKey: ["orders", user?.id, { status, page }],
    queryFn: () =>
      api.orders.list({ status, page, size: 10, sort: "createdAt,desc" }),
    enabled: !!user,
    retry: 1,
  });
  function navigate(nextPage: number, nextStatus = status) {
    const next = new URLSearchParams();
    if (nextStatus) next.set("status", nextStatus);
    if (nextPage) next.set("page", String(nextPage));
    router.push("/orders" + (next.size ? "?" + next.toString() : ""));
  }
  return (
    <>
      <PageHeader
        title="Your orders"
        eyebrow="From cart to doorstep"
        description="Payment and delivery progress, always shown separately."
        actions={
          <Link href="/products" className="btn btn-primary">
            Browse products
          </Link>
        }
      />
      <div style={{ maxWidth: 280, marginBottom: 24 }}>
        <Field label="Financial status">
          <Select
            value={status}
            onChange={(event) => navigate(0, event.target.value)}
          >
            <option value="">All statuses</option>
            {orderStatuses.map((value) => (
              <option value={value} key={value}>
                {label(value)}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      {orders.isFetching && !orders.isPending && (
        <p role="status" className="small muted">
          Refreshing your orders…
        </p>
      )}
      {orders.isPending ? (
        <LoadingState label="Loading orders" />
      ) : orders.isError ? (
        <ErrorState error={orders.error} retry={() => void orders.refetch()} />
      ) : orders.data?.items.length ? (
        <>
          <Table
            headers={[
              "Order",
              "Financial status",
              "Fulfillment status",
              "Total",
              "Placed",
              "Details",
            ]}
          >
            {orders.data.items.map((order) => (
              <tr key={order.id}>
                <td>
                  #{order.id.slice(0, 8)}
                  <br />
                  <span className="muted small">
                    {order.items.length}{" "}
                    {order.items.length === 1 ? "product" : "products"}
                  </span>
                </td>
                <td>
                  <StatusBadge status={order.status} />
                </td>
                <td>
                  <StatusBadge status={order.fulfillmentStatus} />
                </td>
                <td>
                  {money(order.totalAmount, order.currency)}
                  <br />
                  <span className="muted small">{order.currency}</span>
                </td>
                <td>{date(order.createdAt)}</td>
                <td>
                  <Link
                    href={"/orders/" + order.id}
                    className="btn btn-secondary"
                    aria-label={"View order " + order.id.slice(0, 8)}
                  >
                    View order
                  </Link>
                </td>
              </tr>
            ))}
          </Table>
          <Pagination
            page={orders.data.page}
            totalPages={orders.data.totalPages}
            totalElements={orders.data.totalElements}
            onPageChange={(next) => navigate(next)}
          />
        </>
      ) : (
        <EmptyState
          title={
            status
              ? "No orders with this status"
              : "Your first order starts here"
          }
          description={
            status
              ? "Choose another status to find your orders."
              : "Browse the collection and place an order to see it here."
          }
          action={
            <Link href="/products" className="btn btn-primary">
              Explore the collection
            </Link>
          }
        />
      )}
    </>
  );
}
