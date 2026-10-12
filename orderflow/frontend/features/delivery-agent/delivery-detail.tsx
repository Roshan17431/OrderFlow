"use client";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { request } from "@/lib/api/http";
import { date, label } from "@/lib/formatting";
import {
  Alert,
  Button,
  Card,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
} from "@/components/ui";
import type { Address, Delivery } from "@/types/contracts";
import { DeliveryActions } from "./delivery-actions";
function DeliveryTimeline({ delivery }: { delivery: Delivery }) {
  const steps = [
    ["ASSIGNED", delivery.assignedAt],
    ["PICKED_UP", delivery.pickedUpAt],
    ["OUT_FOR_DELIVERY", delivery.outForDeliveryAt],
    ["DELIVERED", delivery.deliveredAt],
    ["FAILED", delivery.failedAt],
    ["CANCELLED", delivery.cancelledAt],
  ] as const;
  const recorded = steps
    .filter(
      (step): step is (typeof steps)[number] & readonly [string, string] =>
        !!step[1],
    )
    .sort((a, b) => Date.parse(a[1]!) - Date.parse(b[1]!));
  return recorded.length ? (
    <ol className="timeline">
      {recorded.map(([status, time]) => (
        <li key={status}>
          <p>{label(status)}</p>
          <p className="small muted">{date(time)}</p>
        </li>
      ))}
    </ol>
  ) : (
    <p className="muted">No recorded delivery milestones yet.</p>
  );
}
export function AgentDeliveryDetail({ deliveryId }: { deliveryId: string }) {
  const delivery = useQuery({
    queryKey: ["agent", "delivery", deliveryId],
    queryFn: () => api.delivery.assigned(deliveryId),
    retry: false,
  });
  const address = useQuery({
    queryKey: ["agent", "address", deliveryId],
    queryFn: () =>
      request<Address>(
        `/api/delivery/agents/me/deliveries/${encodeURIComponent(deliveryId)}/address`,
      ),
    enabled: delivery.isSuccess && !delivery.isError,
    retry: false,
  });
  const refresh = () => {
    void delivery.refetch();
    if (delivery.data) void address.refetch();
  };
  return (
    <div className="stack">
      <PageHeader
        title="Delivery details"
        description={`Delivery ${deliveryId}`}
        actions={
          <>
            <Link
              className="btn btn-secondary"
              href="/delivery-agent/deliveries"
            >
              Assigned deliveries
            </Link>
            <Button variant="secondary" onClick={refresh}>
              Refresh
            </Button>
          </>
        }
      />
      {delivery.isPending ? (
        <LoadingState label="Loading delivery details" />
      ) : delivery.isError ? (
        <ErrorState
          error={delivery.error}
          retry={() => void delivery.refetch()}
        />
      ) : (
        <>
          <div className="row">
            <StatusBadge status={delivery.data.status} />
            <span className="small muted">
              Order reference: {delivery.data.orderId}
            </span>
          </div>
          {["CANCELLED", "CANCEL_REQUESTED"].includes(delivery.data.status) && (
            <Alert>
              This delivery has been cancelled or is awaiting cancellation.
              Delivery actions are unavailable.
            </Alert>
          )}
          {delivery.data.status === "DELIVERED" && (
            <Alert tone="info">This delivery is complete.</Alert>
          )}
          <div className="split">
            <div className="stack">
              <Card title="Delivery address">
                {address.isPending ? (
                  <LoadingState label="Loading authorized delivery address" />
                ) : address.isError ? (
                  <>
                    <ErrorState
                      error={address.error}
                      retry={() => void address.refetch()}
                    />
                    <p className="muted">
                      The complete address could not be loaded. Refresh the
                      authorized address before travelling.
                    </p>
                  </>
                ) : (
                  <>
                    <p>{address.data.recipientName}</p>
                    <p>{address.data.addressLine1}</p>
                    {address.data.addressLine2 && (
                      <p>{address.data.addressLine2}</p>
                    )}
                    <p>
                      {address.data.city}, {address.data.stateOrRegion}{" "}
                      {address.data.postalCode}
                      <br />
                      {address.data.countryCode}
                    </p>
                    <h3>Delivery instructions</h3>
                    <p>
                      {address.data.deliveryInstructions ||
                        "No delivery instructions provided."}
                    </p>
                  </>
                )}
              </Card>
              <Card title="Status timeline">
                <DeliveryTimeline delivery={delivery.data} />
              </Card>
            </div>
            <Card title="Next action">
              <DeliveryActions delivery={delivery.data} />
              <p className="small muted" style={{ marginTop: 20 }}>
                Actions are available only for the current delivery state.
                Updated status is confirmed by the service.
              </p>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
