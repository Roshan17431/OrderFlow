"use client";
import { useEffect, useRef, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { date, money } from "@/lib/formatting";
import { useAuth } from "@/providers/auth-provider";
import {
  Alert,
  Button,
  Card,
  ErrorState,
  Field,
  LoadingState,
  Select,
  StatusBadge,
} from "@/components/ui";
import type { Order } from "@/types/contracts";
import {
  paymentCanConfirm,
  paymentPollingInterval,
} from "@/features/orders/state";
type Choice = "success" | "declined" | "action";
const tokens: Record<Choice, string> = {
  success: "tok_success",
  declined: "tok_declined",
  action: "tok_requires_action",
};
const outcomeSchema = z.object({
  outcome: z
    .string()
    .refine(
      (value) => ["success", "declined", "action"].includes(value),
      "Select a simulator outcome",
    ),
});
export function PaymentPanel({
  order,
  onChange,
  pollingStartedAt,
}: {
  order: Order;
  onChange: () => void;
  pollingStartedAt: number;
}) {
  const { user } = useAuth();
  const cache = useQueryClient();
  const key = ["payment", user?.id, order.id];
  const form = useForm<{ outcome: string }>({
    resolver: zodResolver(outcomeSchema),
    defaultValues: { outcome: "" },
  });
  const selection = useWatch({ control: form.control, name: "outcome" });
  const attempt = useRef<{ choice: Choice; key: string } | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const [retryChoice, setRetryChoice] = useState<Choice | null>(null);
  const [clock, setClock] = useState(() => Date.now());
  const enabled =
    !!user && (!!order.paymentId || order.status === "PAYMENT_PENDING");
  const payment = useQuery({
    queryKey: key,
    queryFn: () => api.payments.byOrder(order.id),
    enabled,
    retry: false,
    refetchInterval: (query) => {
      const failed =
        !!query.state.error &&
        (!(query.state.error instanceof ApiError) ||
          query.state.error.status !== 404);
      return paymentPollingInterval(
        query.state.data?.status,
        pollingStartedAt,
        Date.now(),
        failed,
      );
    },
  });
  const deadlineValue = payment.data?.paymentDeadline;
  useEffect(() => {
    if (!deadlineValue) return;
    const deadline = Date.parse(deadlineValue);
    const delay = deadline - Date.now();
    if (!Number.isFinite(delay)) return;
    const timer = setTimeout(
      () => setClock(Date.now()),
      Math.max(0, Math.min(delay + 1, 2147483647)),
    );
    return () => clearTimeout(timer);
  }, [deadlineValue]);
  const confirm = useMutation({
    mutationFn: async (choice: Choice) => {
      if (!attempt.current || attempt.current.choice !== choice)
        attempt.current = { choice, key: crypto.randomUUID() };
      return api.payments.confirm(
        order.id,
        tokens[choice],
        attempt.current.key,
      );
    },
    onSuccess: (result) => {
      setUncertain(false);
      setRetryChoice(null);
      attempt.current = null;
      cache.setQueryData(key, result);
      void cache.invalidateQueries({ queryKey: key, refetchType: "none" });
      void cache.invalidateQueries({ queryKey: ["orders", user?.id] });
      onChange();
    },
    onError: (error) => {
      const isUncertain =
        !(error instanceof ApiError) ||
        error.status >= 500 ||
        error.code === "PAYMENT_IDEMPOTENCY_CONFLICT";
      setUncertain(isUncertain);
      setRetryChoice(isUncertain ? (attempt.current?.choice ?? null) : null);
      void payment.refetch();
      onChange();
    },
    onSettled: () => form.reset({ outcome: "" }),
  });
  if (!enabled)
    return (
      <Card title="Payment">
        <p className="muted">
          {["INVENTORY_PENDING", "INVENTORY_RESERVED"].includes(order.status)
            ? "Payment becomes available after inventory is reserved."
            : "No payment has been created for this order."}
        </p>
      </Card>
    );
  const missing =
    payment.error instanceof ApiError && payment.error.status === 404;
  const canConfirm = paymentCanConfirm(order.status, payment.data, clock);
  return (
    <Card title="Payment">
      {payment.isPending ? (
        <LoadingState label="Loading payment" />
      ) : payment.isError && !missing ? (
        <ErrorState
          error={payment.error}
          retry={() => void payment.refetch()}
        />
      ) : missing && !payment.data ? (
        <Alert tone="info">
          Your payment is still being prepared. Reload if it does not appear
          shortly.
        </Alert>
      ) : null}
      {payment.data && (
        <>
          <div className="row justify-between">
            <StatusBadge status={payment.data.status} />
            <strong>
              {money(payment.data.amount, payment.data.currency)}{" "}
              {payment.data.currency}
            </strong>
          </div>
          <p className="small muted" style={{ marginTop: 16 }}>
            Provider: {payment.data.provider}
            <br />
            Payment deadline: {date(payment.data.paymentDeadline)}
          </p>
          {payment.data.failureCode && (
            <Alert tone="error">
              Payment could not be completed:{" "}
              {payment.data.failureCode.replaceAll("_", " ").toLowerCase()}.
            </Alert>
          )}
          {payment.data.status === "PROCESSING" && (
            <Alert tone="info">
              The simulator accepted this attempt. Payment stays processing
              until a trusted backend webhook confirms the outcome.
            </Alert>
          )}
          {payment.data.status === "REQUIRES_ACTION" && (
            <Alert>
              Authentication is required by this simulator outcome. Complete the
              simulator event using trusted backend tooling, then reload.
            </Alert>
          )}
          {payment.data.status === "SUCCEEDED" && (
            <Alert tone="info">
              Payment succeeded. Your order and delivery update asynchronously.
            </Alert>
          )}
          {payment.data.status === "FAILED" && (
            <p>
              Payment was declined or failed. This order cannot be charged
              through this form again.
            </p>
          )}
          {["REFUNDED", "PARTIALLY_REFUNDED", "REFUND_PENDING"].includes(
            payment.data.status,
          ) && (
            <p>
              Refunded amount:{" "}
              {money(payment.data.refundedAmount, payment.data.currency)}
            </p>
          )}
          {Date.parse(payment.data.paymentDeadline) <= clock &&
            ["PENDING", "REQUIRES_ACTION", "PROCESSING"].includes(
              payment.data.status,
            ) && (
              <Alert>
                The payment deadline has passed. Reload to check the backend’s
                final state.
              </Alert>
            )}
          {canConfirm && (
            <>
              <p className="eyebrow">Local payment simulator</p>
              <p className="small muted">
                These are test outcomes. No card or banking details are
                collected.
              </p>
              {uncertain ? (
                <Alert>
                  The outcome of the last confirmation is uncertain. Retry that
                  same attempt to recover the result.
                </Alert>
              ) : null}
              <form
                onSubmit={form.handleSubmit((values) =>
                  confirm.mutate(values.outcome as Choice),
                )}
                noValidate
              >
                <Field
                  label="Simulator outcome"
                  htmlFor="simulator-outcome"
                  error={form.formState.errors.outcome?.message}
                >
                  <Select
                    id="simulator-outcome"
                    {...form.register("outcome")}
                    aria-invalid={!!form.formState.errors.outcome}
                    disabled={confirm.isPending || uncertain}
                    required
                  >
                    <option value="">Choose a test outcome</option>
                    <option value="success">Simulate success</option>
                    <option value="declined">Simulate decline</option>
                    <option value="action">
                      Simulate authentication required
                    </option>
                  </Select>
                </Field>
                {!uncertain && (
                  <Button
                    type="submit"
                    pending={confirm.isPending}
                    disabled={!selection}
                  >
                    Confirm simulated payment
                  </Button>
                )}
              </form>
              {uncertain && retryChoice && (
                <Button
                  pending={confirm.isPending}
                  onClick={() => confirm.mutate(retryChoice)}
                >
                  Retry same payment
                </Button>
              )}
            </>
          )}
          {payment.data.provider === "SIMULATOR" && (
            <details style={{ marginTop: 20 }}>
              <summary className="small">
                How simulator completion works
              </summary>
              <p className="small muted" style={{ marginTop: 12 }}>
                Success and authentication outcomes require the signed simulator
                webhook from trusted backend tooling. A customer browser cannot
                finalize that event. See{" "}
                <code>frontend/docs/payment-simulator.md</code> for the local
                developer workflow.
              </p>
            </details>
          )}
        </>
      )}
      {confirm.error && <ErrorState error={confirm.error} />}
      <Button
        style={{ marginTop: 16 }}
        variant="ghost"
        disabled={payment.isFetching || confirm.isPending}
        onClick={() => {
          void payment.refetch();
          onChange();
        }}
      >
        Reload payment
      </Button>
    </Card>
  );
}
