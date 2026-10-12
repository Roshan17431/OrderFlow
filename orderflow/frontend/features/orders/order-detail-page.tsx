"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { date, label, money } from "@/lib/formatting";
import { useAuth } from "@/providers/auth-provider";
import {
  Alert,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Field,
  Input,
  LoadingState,
  PageHeader,
  StatusBadge,
  Table,
} from "@/components/ui";
import { PaymentPanel } from "@/features/payments/payment-panel";
import {
  canCancelOrder,
  financialMessage,
  orderPollingInterval,
} from "./state";
import { usePollingWindow } from "./use-polling-window";

export function OrderDetailPage({ orderId }: { orderId: string }) {
  const { user } = useAuth();
  const cache = useQueryClient();
  const key = ["order", user?.id, orderId];
  const polling = usePollingWindow();
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState<string | undefined>(undefined);
  const cancellation = useRef<{ reason: string; key: string } | null>(null);
  const order = useQuery({
    queryKey: key,
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
  const history = useQuery({
    queryKey: ["order-history", user?.id, orderId],
    queryFn: () => api.orders.history(orderId),
    enabled: !!user && !!order.data,
    retry: 1,
  });
  const historyVersion = order.data?.version;
  useEffect(() => {
    if (historyVersion != null)
      void cache.invalidateQueries({
        queryKey: ["order-history", user?.id, orderId],
      });
  }, [historyVersion, cache, user?.id, orderId]);
  function reload() {
    polling.restart();
    void order.refetch();
    void history.refetch();
  }
  const cancel = useMutation({
    mutationFn: () => {
      const trimmed = reason.trim();
      if (!cancellation.current || cancellation.current.reason !== trimmed)
        cancellation.current = { reason: trimmed, key: crypto.randomUUID() };
      return api.orders.cancel(orderId, trimmed, cancellation.current.key);
    },
    onSuccess: (result) => {
      cache.setQueryData(key, result);
      setCancelOpen(false);
      setReason("");
      cancellation.current = null;
      void cache.invalidateQueries({ queryKey: ["orders", user?.id] });
      void cache.invalidateQueries({
        queryKey: ["order-history", user?.id, orderId],
      });
      polling.restart();
    },
    onError: () => {
      setCancelOpen(false);
      reload();
    },
  });
  if (order.isPending) return <LoadingState label="Loading order" />;
  if (order.isError)
    return (
      <>
        <PageHeader title="Order details" />
        <ErrorState error={order.error} retry={() => void order.refetch()} />
      </>
    );
  if (!order.data) return <EmptyState title="Order not found" />;
  const data = order.data;
  const address = data.shippingAddress;
  return (
    <>
      <PageHeader
        title={"Order #" + data.id.slice(0, 8)}
        eyebrow="Your order"
        description={"Placed " + date(data.createdAt)}
        actions={
          <>
            <Link href="/orders" className="btn btn-secondary">
              All orders
            </Link>
            <Button
              variant="secondary"
              disabled={order.isFetching}
              onClick={reload}
            >
              {order.isFetching ? "Refreshing…" : "Reload status"}
            </Button>
          </>
        }
      />
      <p className="small muted" style={{ overflowWrap: "anywhere" }}>
        Order ID: {data.id}
      </p>
      <div className="grid grid-2" style={{ marginBottom: 24 }}>
        <Card>
          <p className="small muted">Financial status</p>
          <StatusBadge status={data.status} />
          <p style={{ marginTop: 16, marginBottom: 0 }}>
            {financialMessage(data.status)}
          </p>
          {data.paymentFailureCode && (
            <p className="field-error">
              Failure reason: {label(data.paymentFailureCode)}
            </p>
          )}
        </Card>
        <Card>
          <p className="small muted">Fulfillment status</p>
          <StatusBadge status={data.fulfillmentStatus} />
          <p style={{ marginTop: 16 }}>
            {data.fulfillmentStatus === "NOT_REQUESTED"
              ? "Delivery will be requested after payment is confirmed."
              : "Follow the actual delivery milestones for this order."}
          </p>
          <Link
            href={"/orders/" + data.id + "/tracking"}
            className="btn btn-secondary"
          >
            Track delivery
          </Link>
        </Card>
      </div>
      {polling.expired && (
        <Alert tone="info">
          Automatic status checks paused after two minutes. Use Reload status to
          check again.
        </Alert>
      )}
      {order.isFetching && (
        <p className="small muted" role="status">
          Checking order status…
        </p>
      )}
      <div className="split">
        <div className="stack">
          <Card title="Your items">
            <Table
              headers={["Product", "Quantity", "Unit price", "Line total"]}
            >
              {data.items.map((item) => (
                <tr key={item.productId}>
                  <td>{item.productName}</td>
                  <td>{item.quantity}</td>
                  <td>{money(item.unitPrice, item.currency)}</td>
                  <td>{money(item.lineTotal, item.currency)}</td>
                </tr>
              ))}
            </Table>
            <div className="summary-line">
              <strong>Total</strong>
              <strong>
                {money(data.totalAmount, data.currency)} {data.currency}
              </strong>
            </div>
            {data.refundedAmount !== "0.00" &&
              Number(data.refundedAmount) > 0 && (
                <div className="summary-line">
                  <span>Refunded</span>
                  <span>{money(data.refundedAmount, data.currency)}</span>
                </div>
              )}
          </Card>
          <Card title="Shipping address">
            <address style={{ fontStyle: "normal" }}>
              {address.recipientName}
              <br />
              {address.addressLine1}
              <br />
              {address.addressLine2 && (
                <>
                  {address.addressLine2}
                  <br />
                </>
              )}
              {address.city}, {address.stateOrRegion} {address.postalCode}
              <br />
              {address.countryCode}
            </address>
            {address.deliveryInstructions && (
              <p className="small muted" style={{ marginTop: 16 }}>
                Instructions: {address.deliveryInstructions}
              </p>
            )}
          </Card>
          <Card title="Order history">
            {history.isPending ? (
              <LoadingState label="Loading order history" />
            ) : history.isError ? (
              <ErrorState
                error={history.error}
                retry={() => void history.refetch()}
              />
            ) : history.data?.length ? (
              <ol className="timeline">
                {history.data.map((event, index) => (
                  <li key={event.changedAt + index}>
                    <p>
                      <StatusBadge status={event.newStatus} />
                    </p>
                    <p className="small">{label(event.reasonCode)}</p>
                    <time className="small muted" dateTime={event.changedAt}>
                      {date(event.changedAt)}
                    </time>
                  </li>
                ))}
              </ol>
            ) : (
              <p className="muted">
                Order history will appear as your order progresses.
              </p>
            )}
          </Card>
        </div>
        <div className="stack">
          <PaymentPanel
            key={data.id}
            order={data}
            pollingStartedAt={polling.startedAt}
            onChange={() => {
              polling.restart();
              void cache.invalidateQueries({ queryKey: key });
              void history.refetch();
            }}
          />
          {canCancelOrder(data.status) && (
            <Card title="Need to cancel?">
              <p className="small muted">
                Cancellation is available before payment succeeds. It completes
                asynchronously.
              </p>
              <Field
                label="Cancellation reason"
                error={reasonError}
                htmlFor="cancellation-reason"
              >
                <Input
                  id="cancellation-reason"
                  maxLength={200}
                  value={reason}
                  onChange={(event) => {
                    setReason(event.target.value);
                    setReasonError(undefined);
                  }}
                  placeholder="Tell us why you’re cancelling"
                />
              </Field>
              <Button
                variant="danger"
                pending={cancel.isPending}
                onClick={() => {
                  if (!reason.trim()) {
                    setReasonError("A cancellation reason is required");
                    return;
                  }
                  setCancelOpen(true);
                }}
              >
                Cancel order
              </Button>
            </Card>
          )}
          {cancel.error && (
            <ErrorState
              error={cancel.error}
              retry={() => {
                if (canCancelOrder(data.status)) cancel.mutate();
                else reload();
              }}
            />
          )}
          {data.cancellationReason && (
            <Alert>Cancellation reason: {data.cancellationReason}</Alert>
          )}
        </div>
      </div>
      <ConfirmDialog
        open={cancelOpen}
        onClose={() => setCancelOpen(false)}
        onConfirm={() => cancel.mutate()}
        pending={cancel.isPending}
        title="Cancel this order?"
        description="OrderFlow will request cancellation of inventory and payment. A concurrent payment success may prevent cancellation."
        confirmLabel="Confirm cancellation"
      />
    </>
  );
}
