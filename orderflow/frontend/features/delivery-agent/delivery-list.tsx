"use client";
import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { useAuth } from "@/providers/auth-provider";
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
} from "@/components/ui";
import { DeliveryTable } from "./delivery-table";
export function AgentDeliveryList() {
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const deliveries = useQuery({
    queryKey: ["agent", "deliveries", user?.id, page],
    queryFn: () => api.delivery.list({ page, size: 20 }),
    enabled: !!user,
    retry: false,
  });
  const missing =
    deliveries.error instanceof ApiError && deliveries.error.status === 404;
  return (
    <div className="stack">
      <PageHeader
        title="Assigned deliveries"
        description="Deliveries assigned to your profile, including completed work."
        actions={
          <Button variant="secondary" onClick={() => void deliveries.refetch()}>
            Refresh
          </Button>
        }
      />
      <Card>
        {deliveries.isPending ? (
          <LoadingState label="Loading assigned deliveries" />
        ) : missing ? (
          <EmptyState
            title="Set up your delivery profile"
            description="Set your availability in the dashboard to register your profile."
            action={
              <Link className="btn btn-primary" href="/delivery-agent">
                Open dashboard
              </Link>
            }
          />
        ) : deliveries.isError ? (
          <ErrorState
            error={deliveries.error}
            retry={() => void deliveries.refetch()}
          />
        ) : (
          <DeliveryTable data={deliveries.data} onPageChange={setPage} />
        )}
      </Card>
    </div>
  );
}
