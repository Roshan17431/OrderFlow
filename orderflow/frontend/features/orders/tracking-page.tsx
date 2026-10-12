"use client";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { date } from "@/lib/formatting";
import { useAuth } from "@/providers/auth-provider";
import {
  Alert,
  Button,
  Card,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
} from "@/components/ui";
import { orderPollingInterval } from "./state";
import { usePollingWindow } from "./use-polling-window";

export function TrackingPage({ orderId }: { orderId: string }) {
  const { user } = useAuth();
  const polling = usePollingWindow();
  const order = useQuery({
    queryKey: ["order", user?.id, orderId],
    queryFn: () => api.orders.get(orderId),
    enabled: !!user,
    retry: 1,
    refetchInterval: (query) =>
      orderPollingInterval(
        query.state.data?.status,
        polling.startedAt,
        Date.now(),
        !!query.state.error,
        query.state.data?.fulfillmentStatus,
      ),
  });
  const requested =
    !!order.data &&
    (!!order.data.deliveryId ||
      order.data.fulfillmentStatus !== "NOT_REQUESTED");
  const delivery = useQuery({
    queryKey: ["delivery", user?.id, orderId],
    queryFn: () => api.delivery.byOrder(orderId),
    enabled: !!user && requested,
    retry: false,
    refetchInterval: (query) => {
      if (Date.now() - polling.startedAt >= 120000) return false;
      if (
        query.state.error &&
        (!(query.state.error instanceof ApiError) ||
          query.state.error.status !== 404)
      )
        return false;
      return !query.state.data ||
        !["DELIVERED", "FAILED", "CANCELLED"].includes(query.state.data.status)
        ? 2000
        : false;
    },
  });
  const pendingDelivery =
    delivery.error instanceof ApiError && delivery.error.status === 404;
  function reload() {
    polling.restart();
    void order.refetch();
    if (requested) void delivery.refetch();
  }
  const milestones = delivery.data
    ? [
        { name: "Delivery requested", at: order.data?.deliveryRequestedAt },
        { name: "Agent assigned", at: delivery.data.assignedAt },
        { name: "Picked up", at: delivery.data.pickedUpAt },
        { name: "Out for delivery", at: delivery.data.outForDeliveryAt },
        { name: "Delivered", at: delivery.data.deliveredAt },
        { name: "Delivery failed", at: delivery.data.failedAt },
        { name: "Delivery cancelled", at: delivery.data.cancelledAt },
      ]
        .filter((milestone) => milestone.at)
        .sort((a, b) => Date.parse(a.at!) - Date.parse(b.at!))
    : [];
  return (
    <>
      <PageHeader
        title="Delivery tracking"
        eyebrow={"Order #" + orderId.slice(0, 8)}
        description="Follow the confirmed milestones for your delivery."
        actions={
          <>
            <Link className="btn btn-secondary" href={"/orders/" + orderId}>
              Order details
            </Link>
            <Button
              variant="secondary"
              onClick={reload}
              disabled={order.isFetching || delivery.isFetching}
            >
              Reload tracking
            </Button>
          </>
        }
      />
      {polling.expired && (
        <Alert tone="info">
          Automatic tracking checks paused after two minutes. Reload tracking to
          check again.
        </Alert>
      )}
      {order.isPending ? (
        <LoadingState label="Loading tracking" />
      ) : order.isError ? (
        <ErrorState error={order.error} retry={reload} />
      ) : !requested ? (
        <Card title="Delivery is not yet requested">
          <p className="muted">
            {order.data?.status === "PAID"
              ? "Delivery preparation is in progress. Reload to check for the next milestone."
              : "Delivery is requested after payment is confirmed."}
          </p>
          <p className="small muted">Financial state</p>
          <StatusBadge status={order.data?.status ?? "UNKNOWN"} />
        </Card>
      ) : delivery.isPending ? (
        <LoadingState label="Loading delivery" />
      ) : pendingDelivery && !delivery.data ? (
        <Alert tone="info">
          The delivery is being prepared. Its record will appear when processing
          completes.
        </Alert>
      ) : delivery.isError ? (
        <ErrorState error={delivery.error} retry={reload} />
      ) : delivery.data ? (
        <div className="split">
          <Card title="Delivery progress">
            <div className="row justify-between">
              <StatusBadge status={delivery.data.status} />
              <p className="small muted" style={{ margin: 0 }}>
                {delivery.data.deliveryAgentId
                  ? "Delivery agent assigned"
                  : "Waiting for an agent"}
              </p>
            </div>
            {["FAILED", "CANCELLED", "CANCEL_REQUESTED"].includes(
              delivery.data.status,
            ) && (
              <Alert
                tone={delivery.data.status === "FAILED" ? "error" : "warning"}
              >
                Delivery{" "}
                {delivery.data.status.toLowerCase().replaceAll("_", " ")}. Check
                your order for payment and refund details.
              </Alert>
            )}
            <div style={{ marginTop: 28 }}>
              {milestones.length ? (
                <ol className="timeline">
                  {milestones.map((milestone) => (
                    <li key={milestone.name}>
                      <p>
                        <strong>{milestone.name}</strong>
                      </p>
                      <time className="small muted" dateTime={milestone.at!}>
                        {date(milestone.at)}
                      </time>
                    </li>
                  ))}
                </ol>
              ) : (
                <p className="muted">
                  Milestones will appear when the delivery progresses.
                </p>
              )}
            </div>
          </Card>
          <Card title="Destination">
            <p>
              {delivery.data.shippingAddress.recipientName}
              <br />
              {delivery.data.shippingAddress.city},{" "}
              {delivery.data.shippingAddress.stateOrRegion}
              <br />
              {delivery.data.shippingAddress.countryCode}
            </p>
            <p className="small muted">
              The complete shipping address is available in your order details.
            </p>
            <p className="small muted" style={{ overflowWrap: "anywhere" }}>
              Delivery ID: {delivery.data.id}
            </p>
          </Card>
        </div>
      ) : null}
    </>
  );
}
