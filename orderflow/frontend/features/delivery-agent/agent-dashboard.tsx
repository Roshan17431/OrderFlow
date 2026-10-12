"use client";
import Link from "next/link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { useAuth } from "@/providers/auth-provider";
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
  StatCard,
  StatusBadge,
} from "@/components/ui";
import { DeliveryTable } from "./delivery-table";
export function AgentDashboard() {
  const { user } = useAuth();
  const client = useQueryClient();
  const [page, setPage] = useState(0);
  const [copyMessage, setCopyMessage] = useState("");
  const profile = useQuery({
    queryKey: ["agent", "profile", user?.id],
    queryFn: api.delivery.agent,
    enabled: !!user,
    retry: false,
  });
  const missing =
    profile.error instanceof ApiError && profile.error.status === 404;
  const deliveries = useQuery({
    queryKey: ["agent", "deliveries", user?.id, page],
    queryFn: () => api.delivery.list({ page, size: 20 }),
    enabled: !!profile.data,
    retry: false,
  });
  const active = useQuery({
    queryKey: ["agent", "delivery", profile.data?.activeDeliveryId],
    queryFn: () => api.delivery.assigned(profile.data!.activeDeliveryId!),
    enabled: !!profile.data?.activeDeliveryId,
    retry: false,
  });
  const availability = useMutation({
    retry: false,
    mutationFn: api.delivery.availability,
    onSuccess: async (value) => {
      client.setQueryData(["agent", "profile", user?.id], value);
      await client.invalidateQueries({ queryKey: ["agent", "deliveries"] });
      toast.success("Availability updated.");
    },
  });
  const busy = profile.data?.availability === "BUSY";
  async function copyProfileId() {
    const profileId = profile.data?.profileId;
    if (!profileId) return;
    try {
      if (!navigator.clipboard?.writeText)
        throw new Error("Clipboard unavailable");
      await navigator.clipboard.writeText(profileId);
      setCopyMessage("Profile ID copied.");
    } catch {
      setCopyMessage(
        "Copy unavailable. Select the profile ID and copy it manually.",
      );
    }
  }
  return (
    <div className="stack">
      <PageHeader
        title="Delivery workspace"
        description="Your availability, assigned work, and next delivery action."
        eyebrow="On the move"
        actions={
          <Button
            variant="secondary"
            onClick={() => {
              void profile.refetch();
              if (profile.data) void deliveries.refetch();
              if (profile.data?.activeDeliveryId) void active.refetch();
            }}
          >
            Refresh
          </Button>
        }
      />
      {profile.isPending ? (
        <LoadingState label="Loading delivery profile" />
      ) : profile.isError && !missing ? (
        <ErrorState
          error={profile.error}
          retry={() => void profile.refetch()}
        />
      ) : (
        <>
          <Card title="Availability">
            {missing && !profile.data ? (
              <Alert tone="info">
                Set your availability to register your delivery profile and
                receive assigned work.
              </Alert>
            ) : (
              <div className="row">
                <StatusBadge status={profile.data?.availability ?? "OFFLINE"} />
                <span className="small muted">
                  Updated {date(profile.data?.updatedAt)}
                </span>
              </div>
            )}
            {busy && (
              <p className="muted">
                Server controls availability during an active delivery.
              </p>
            )}
            {profile.data?.profileId && (
              <div style={{ marginTop: 18 }}>
                <Field label="Agent profile ID">
                  <Input
                    readOnly
                    value={profile.data.profileId}
                    onFocus={(event) => event.currentTarget.select()}
                  />
                </Field>
                <div className="row">
                  <Button
                    type="button"
                    variant="secondary"
                    aria-label="Copy agent profile ID"
                    onClick={() => void copyProfileId()}
                  >
                    Copy ID
                  </Button>
                  {copyMessage && (
                    <p role="status" className="small" style={{ margin: 0 }}>
                      {copyMessage}
                    </p>
                  )}
                </div>
                <p className="small muted" style={{ marginTop: 12 }}>
                  Share this ID with an administrator for manual delivery
                  assignment.
                </p>
              </div>
            )}
            {availability.error && <ErrorState error={availability.error} />}
            <div className="row" style={{ marginTop: 18 }}>
              <Button
                pending={availability.isPending}
                disabled={busy || profile.data?.availability === "AVAILABLE"}
                onClick={() => availability.mutate("AVAILABLE")}
              >
                Become available
              </Button>
              <Button
                variant="secondary"
                pending={availability.isPending}
                disabled={busy || profile.data?.availability === "OFFLINE"}
                onClick={() => availability.mutate("OFFLINE")}
              >
                Go offline
              </Button>
            </div>
          </Card>
          {profile.data && (
            <>
              {deliveries.data && (
                <div className="stat-grid">
                  <StatCard
                    label="Assigned deliveries"
                    value={deliveries.data.totalElements}
                    description="All statuses across your delivery list"
                  />
                  <StatCard
                    label="Pending work on this page"
                    value={
                      deliveries.data.items.filter((d) =>
                        ["ASSIGNED", "PICKED_UP", "OUT_FOR_DELIVERY"].includes(
                          d.status,
                        ),
                      ).length
                    }
                    description={`Page ${page + 1} of up to 20 deliveries`}
                  />
                  <StatCard
                    label="Completed on this page"
                    value={
                      deliveries.data.items.filter(
                        (d) => d.status === "DELIVERED",
                      ).length
                    }
                    description={`Page ${page + 1} of up to 20 deliveries`}
                  />
                </div>
              )}
              <Card title="Active delivery">
                {profile.data.activeDeliveryId ? (
                  active.isPending ? (
                    <LoadingState label="Loading active delivery" />
                  ) : active.isError ? (
                    <ErrorState
                      error={active.error}
                      retry={() => void active.refetch()}
                    />
                  ) : (
                    <div className="stack">
                      <div className="row">
                        <StatusBadge status={active.data.status} />
                        <span>Order {active.data.orderId}</span>
                      </div>
                      <p>
                        {active.data.shippingAddress.recipientName} ·{" "}
                        {active.data.shippingAddress.city},{" "}
                        {active.data.shippingAddress.stateOrRegion}
                      </p>
                      <div>
                        <Link
                          className="btn btn-primary"
                          href={`/delivery-agent/deliveries/${active.data.id}`}
                        >
                          View active delivery
                        </Link>
                      </div>
                    </div>
                  )
                ) : (
                  <EmptyState
                    title="No active delivery"
                    description="Your next active assignment will appear here."
                  />
                )}
              </Card>
              <Card title="Your assigned deliveries">
                {deliveries.isPending ? (
                  <LoadingState label="Loading assigned deliveries" />
                ) : deliveries.isError ? (
                  <ErrorState
                    error={deliveries.error}
                    retry={() => void deliveries.refetch()}
                  />
                ) : (
                  <DeliveryTable
                    data={deliveries.data}
                    onPageChange={setPage}
                  />
                )}
              </Card>
            </>
          )}
        </>
      )}
    </div>
  );
}
