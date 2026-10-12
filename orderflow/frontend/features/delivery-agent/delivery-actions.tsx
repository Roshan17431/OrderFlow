"use client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { label } from "@/lib/formatting";
import { Alert, Button, ErrorState, Field, Select } from "@/components/ui";
import type { Delivery, DeliveryStatus } from "@/types/contracts";
export const failureCodes = [
  "CUSTOMER_UNAVAILABLE",
  "ADDRESS_PROBLEM",
  "PACKAGE_DAMAGED",
  "VEHICLE_PROBLEM",
  "OTHER",
] as const;
const schema = z.object({ failureCode: z.enum(failureCodes) });
type Action = "pickup" | "out-for-delivery" | "delivered" | "failed";
export function nextDeliveryAction(
  status: DeliveryStatus,
): { action: Action; caption: string } | null {
  switch (status) {
    case "ASSIGNED":
      return { action: "pickup", caption: "Mark picked up" };
    case "PICKED_UP":
      return { action: "out-for-delivery", caption: "Mark out for delivery" };
    case "OUT_FOR_DELIVERY":
      return { action: "delivered", caption: "Mark delivered" };
    default:
      return null;
  }
}
export function DeliveryActions({
  delivery,
  onUpdated,
}: {
  delivery: Delivery;
  onUpdated?: (delivery: Delivery) => void;
}) {
  const client = useQueryClient();
  const form = useForm<{ failureCode: (typeof failureCodes)[number] }>({
    resolver: zodResolver(schema),
    defaultValues: { failureCode: "CUSTOMER_UNAVAILABLE" },
  });
  const mutation = useMutation({
    retry: false,
    mutationFn: ({
      action,
      failureCode,
    }: {
      action: Action;
      failureCode?: string;
    }) => api.delivery.transition(delivery.id, action, failureCode),
    onSuccess: async (value) => {
      client.setQueryData(["agent", "delivery", delivery.id], value);
      await client.invalidateQueries({ queryKey: ["agent"] });
      toast.success("Delivery updated.");
      onUpdated?.(value);
    },
    onError: async () => {
      await client.invalidateQueries({ queryKey: ["agent"] });
    },
  });
  const current =
    mutation.data && mutation.data.version >= delivery.version
      ? mutation.data
      : delivery;
  const next = nextDeliveryAction(current.status);
  const denied =
    mutation.error instanceof ApiError &&
    [403, 404].includes(mutation.error.status);
  return (
    <div className="stack">
      {mutation.error && <ErrorState error={mutation.error} />}
      {mutation.error instanceof ApiError && mutation.error.status === 409 && (
        <Alert>
          The delivery state changed. Refresh the details before attempting the
          next action.
        </Alert>
      )}
      {mutation.isSuccess && <Alert tone="info">Delivery updated.</Alert>}
      {denied ? (
        <Alert>
          This delivery is no longer available to your account. Return to your
          assigned deliveries.
        </Alert>
      ) : next ? (
        <>
          <div>
            <Button
              pending={mutation.isPending}
              onClick={() => mutation.mutate({ action: next.action })}
            >
              {next.caption}
            </Button>
          </div>
          <form
            noValidate
            onSubmit={form.handleSubmit((value) =>
              mutation.mutate({
                action: "failed",
                failureCode: value.failureCode,
              }),
            )}
          >
            <Field
              label="Failure reason"
              error={form.formState.errors.failureCode?.message}
            >
              <Select
                {...form.register("failureCode")}
                disabled={mutation.isPending}
              >
                {failureCodes.map((code) => (
                  <option key={code} value={code}>
                    {label(code)}
                  </option>
                ))}
              </Select>
            </Field>
            <Button type="submit" variant="danger" pending={mutation.isPending}>
              Report failure
            </Button>
          </form>
        </>
      ) : (
        <p className="muted">
          {current.status === "CANCEL_REQUESTED"
            ? "Cancellation is being processed. No further delivery action is available."
            : "No further delivery action is available for this status."}
        </p>
      )}
    </div>
  );
}
