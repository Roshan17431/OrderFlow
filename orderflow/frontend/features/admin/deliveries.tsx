"use client";
import Link from "next/link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { date } from "@/lib/formatting";
import {
  Alert,
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
import type { Delivery } from "@/types/contracts";
import { useAdmin } from "./common";
const assignSchema = z.object({
  deliveryAgentId: z.uuid("Enter the real delivery agent UUID"),
});
export function DeliveryPanel({
  delivery,
  onUpdated,
}: {
  delivery: Delivery;
  onUpdated?: (value: Delivery) => void;
}) {
  const { userId, enabled } = useAdmin();
  const cache = useQueryClient();
  const [page, setPage] = useState(0);
  const [assigned, setAssigned] = useState<Delivery | null>(null);
  const current =
    assigned?.id === delivery.id && assigned.version > delivery.version
      ? assigned
      : delivery;
  const form = useForm<z.infer<typeof assignSchema>>({
    resolver: zodResolver(assignSchema),
    defaultValues: { deliveryAgentId: "" },
  });
  const history = useQuery({
    queryKey: ["admin", userId, "deliveryHistory", delivery.id, page],
    queryFn: () => api.admin.deliveryHistory(delivery.id, { page, size: 20 }),
    enabled,
  });
  const mutation = useMutation({
    retry: false,
    mutationFn: ({ deliveryAgentId }: z.infer<typeof assignSchema>) =>
      api.admin.assign(delivery.id, deliveryAgentId),
    onSuccess: (value) => {
      setAssigned(value);
      onUpdated?.(value);
      toast.success("Delivery assigned");
      void cache.invalidateQueries({ queryKey: ["admin", userId] });
    },
    onError: () =>
      void cache.invalidateQueries({
        queryKey: ["admin", userId, "deliveryHistory", delivery.id],
      }),
  });
  return (
    <div className="stack">
      <div className="split">
        <Card title="Delivery details">
          <p className="small muted">{current.id}</p>
          <p>
            <StatusBadge status={current.status} />
          </p>
          <p>
            Order:{" "}
            <Link href={`/admin/orders/${current.orderId}`}>
              {current.orderId}
            </Link>
          </p>
          <p>Agent: {current.deliveryAgentId ?? "Awaiting assignment"}</p>
          <p>
            Recipient: {current.shippingAddress.recipientName}
            <br />
            {current.shippingAddress.city},{" "}
            {current.shippingAddress.stateOrRegion},{" "}
            {current.shippingAddress.countryCode}
          </p>
          <p className="small muted">
            Assigned {date(current.assignedAt)}
            <br />
            Picked up {date(current.pickedUpAt)}
            <br />
            Out for delivery {date(current.outForDeliveryAt)}
            <br />
            Delivered {date(current.deliveredAt)}
            <br />
            Failed {date(current.failedAt)}
            <br />
            Cancelled {date(current.cancelledAt)}
          </p>
        </Card>
        <Card title="Assignment">
          {current.status === "PENDING_ASSIGNMENT" ? (
            <>
              <Alert tone="info">
                Enter an existing agent profile UUID shared by the agent from
                their workspace. The backend requires AVAILABLE status. An agent
                directory is not supplied by this API.
              </Alert>
              <form
                onSubmit={form.handleSubmit((value) => mutation.mutate(value))}
                noValidate
              >
                <Field
                  label="Delivery agent profile ID"
                  error={form.formState.errors.deliveryAgentId?.message}
                >
                  <Input
                    {...form.register("deliveryAgentId")}
                    disabled={mutation.isPending}
                    placeholder="Agent profile UUID"
                  />
                </Field>
                {mutation.isError && <ErrorState error={mutation.error} />}
                <Button type="submit" pending={mutation.isPending}>
                  Assign delivery
                </Button>
              </form>
            </>
          ) : (
            <p>
              Manual assignment is available only while the delivery is pending
              assignment.
            </p>
          )}
        </Card>
      </div>
      <Card title="Status history">
        {history.isPending ? (
          <LoadingState />
        ) : history.isError ? (
          <ErrorState
            error={history.error}
            retry={() => void history.refetch()}
          />
        ) : history.data.items.length === 0 ? (
          <EmptyState title="No delivery history" />
        ) : (
          <>
            <Table
              headers={[
                "From",
                "To",
                "Actor",
                "Changed",
                "Correlation reference",
              ]}
            >
              {history.data.items.map((item, index) => (
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
                  <td>{item.actorUserId ?? "System"}</td>
                  <td>{date(item.changedAt)}</td>
                  <td>{item.correlationId}</td>
                </tr>
              ))}
            </Table>
            <Pagination
              page={page}
              totalPages={history.data.totalPages}
              totalElements={history.data.totalElements}
              onPageChange={setPage}
            />
          </>
        )}
      </Card>
    </div>
  );
}
const lookupSchema = z.object({
  mode: z.enum(["order", "delivery"]),
  id: z.uuid("Enter a valid order or delivery UUID"),
});
export function DeliveriesPage({
  initialOrderId = "",
  initialDeliveryId = "",
}: {
  initialOrderId?: string;
  initialDeliveryId?: string;
}) {
  const { userId, enabled } = useAdmin();
  const [page, setPage] = useState(0);
  const initial = {
    mode: initialDeliveryId ? ("delivery" as const) : ("order" as const),
    id: initialDeliveryId || initialOrderId,
  };
  const [lookup, setLookup] = useState<z.infer<typeof lookupSchema> | null>(
    lookupSchema.safeParse(initial).success ? initial : null,
  );
  const form = useForm<z.infer<typeof lookupSchema>>({
    resolver: zodResolver(lookupSchema),
    defaultValues: initial,
  });
  const detail = useQuery({
    queryKey: ["admin", userId, "deliveryLookup", lookup],
    queryFn: () =>
      lookup?.mode === "delivery"
        ? api.delivery.get(lookup.id)
        : api.delivery.byOrder(lookup!.id),
    enabled: enabled && !!lookup,
  });
  const orders = useQuery({
    queryKey: ["admin", userId, "deliveryOrders", page],
    queryFn: () => api.admin.orders({ page, size: 20, sort: "createdAt,desc" }),
    enabled,
  });
  return (
    <div className="stack">
      <PageHeader
        title="Deliveries"
        description="Look up deliveries, inspect their history, and assign available agents."
      />
      <Card title="Find a delivery">
        <form
          onSubmit={form.handleSubmit((value) => setLookup(value))}
          noValidate
        >
          <div className="form-grid">
            <Field label="Lookup by">
              <Select {...form.register("mode")}>
                <option value="order">Order ID</option>
                <option value="delivery">Delivery ID</option>
              </Select>
            </Field>
            <Field
              label="Order or delivery ID"
              error={form.formState.errors.id?.message}
            >
              <Input {...form.register("id")} placeholder="UUID" />
            </Field>
          </div>
          <Button type="submit" pending={detail.isFetching}>
            Find delivery
          </Button>
        </form>
      </Card>
      {lookup &&
        (detail.isPending ? (
          <LoadingState />
        ) : detail.isError ? (
          <ErrorState
            error={detail.error}
            retry={() => void detail.refetch()}
          />
        ) : (
          <>
            <div className="row">
              <Button
                variant="secondary"
                pending={detail.isFetching}
                onClick={() => void detail.refetch()}
              >
                Refresh delivery
              </Button>
            </div>
            <DeliveryPanel
              key={`${detail.data.id}-${detail.data.version}`}
              delivery={detail.data}
              onUpdated={() => void detail.refetch()}
            />
          </>
        ))}
      <Card title="Delivery references from orders">
        <p className="muted small">
          This API has no general delivery list. These references come from the
          current page of authorized orders; pagination counts orders. A
          delivery may not yet exist for an unpaid order.
        </p>
        {orders.isPending ? (
          <LoadingState />
        ) : orders.isError ? (
          <ErrorState
            error={orders.error}
            retry={() => void orders.refetch()}
          />
        ) : (
          <>
            <Table
              headers={["Order", "Delivery", "Fulfillment", "Agent", "Inspect"]}
            >
              {orders.data.items
                .filter((order) => order.deliveryId)
                .map((order) => (
                  <tr key={order.id}>
                    <td>
                      <Link href={`/admin/orders/${order.id}`}>{order.id}</Link>
                    </td>
                    <td>{order.deliveryId}</td>
                    <td>
                      <StatusBadge status={order.fulfillmentStatus} />
                    </td>
                    <td>{order.deliveryAgentId ?? "Unassigned"}</td>
                    <td>
                      <Button
                        variant="secondary"
                        onClick={() => {
                          const value = {
                            mode: "delivery" as const,
                            id: order.deliveryId!,
                          };
                          form.reset(value);
                          setLookup(value);
                        }}
                      >
                        View delivery
                      </Button>
                    </td>
                  </tr>
                ))}
            </Table>
            {!orders.data.items.some((order) => order.deliveryId) && (
              <EmptyState
                title="No delivery references on this order page"
                description="Try another page or use a known order or delivery ID."
              />
            )}
            <Pagination
              page={page}
              totalPages={orders.data.totalPages}
              totalElements={orders.data.totalElements}
              onPageChange={setPage}
            />
          </>
        )}
      </Card>
    </div>
  );
}
