"use client";
import Link from "next/link";
import { useState } from "react";
import Decimal from "decimal.js";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { date, label, money, remaining } from "@/lib/formatting";
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
import type { Payment, QueryParams, Refund } from "@/types/contracts";
import {
  instantInput,
  paymentStatuses,
  refundReasons,
  StatusOptions,
  useAdmin,
} from "./common";
const filterSchema = z
  .object({
    orderId: z.union([z.literal(""), z.uuid("Enter a valid order UUID")]),
    customerId: z.union([z.literal(""), z.uuid("Enter a valid customer UUID")]),
    status: z.string(),
    provider: z.string().trim(),
    currency: z
      .string()
      .trim()
      .refine(
        (value) => !value || /^[A-Z]{3}$/.test(value),
        "Use a three-letter currency code",
      ),
    createdFrom: z.string(),
    createdTo: z.string(),
  })
  .refine(
    (value) =>
      !value.createdFrom ||
      !value.createdTo ||
      value.createdFrom <= value.createdTo,
    { message: "End date must be on or after start date", path: ["createdTo"] },
  );
type Filters = z.infer<typeof filterSchema>;
export function PaymentsPage() {
  const { userId, enabled } = useAdmin();
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<QueryParams>({});
  const form = useForm<Filters>({
    resolver: zodResolver(filterSchema),
    defaultValues: {
      orderId: "",
      customerId: "",
      status: "",
      provider: "",
      currency: "",
      createdFrom: "",
      createdTo: "",
    },
  });
  const query = useQuery({
    queryKey: ["admin", userId, "payments", filters, page],
    queryFn: () =>
      api.admin.payments({
        ...filters,
        page,
        size: 20,
        sort: "createdAt",
        direction: "DESC",
      }),
    enabled,
  });
  return (
    <div className="stack">
      <PageHeader
        title="Payments & refunds"
        description="Inspect provider outcomes and manage supported refunds."
      />
      <Card>
        <form
          onSubmit={form.handleSubmit((values) => {
            setFilters({
              ...values,
              orderId: values.orderId || undefined,
              customerId: values.customerId || undefined,
              createdFrom: instantInput(values.createdFrom),
              createdTo: instantInput(values.createdTo, true),
            });
            setPage(0);
          })}
          noValidate
        >
          <div className="form-grid">
            <Field
              label="Order ID"
              error={form.formState.errors.orderId?.message}
            >
              <Input {...form.register("orderId")} placeholder="Order UUID" />
            </Field>
            <Field
              label="Customer ID"
              error={form.formState.errors.customerId?.message}
            >
              <Input
                {...form.register("customerId")}
                placeholder="Customer UUID"
              />
            </Field>
            <Field label="Payment status">
              <Select {...form.register("status")}>
                <option value="">All statuses</option>
                <StatusOptions values={paymentStatuses} />
              </Select>
            </Field>
            <Field label="Provider">
              <Input
                {...form.register("provider")}
                placeholder="Provider code"
              />
            </Field>
            <Field
              label="Currency"
              error={form.formState.errors.currency?.message}
            >
              <Input
                {...form.register("currency")}
                maxLength={3}
                placeholder="USD"
              />
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
          </div>
          <Button type="submit">Apply filters</Button>
        </form>
      </Card>
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => void query.refetch()} />
      ) : query.data.items.length === 0 ? (
        <EmptyState
          title="No matching payments"
          description="Try a different status or date range."
        />
      ) : (
        <>
          <Table
            headers={[
              "Payment",
              "Order",
              "Status",
              "Amount",
              "Refunded",
              "Provider",
              "Created",
            ]}
          >
            {query.data.items.map((payment) => (
              <tr key={payment.id}>
                <td>
                  <Link href={`/admin/payments/${payment.id}`}>
                    {payment.id}
                  </Link>
                </td>
                <td>
                  <Link href={`/admin/orders/${payment.orderId}`}>
                    {payment.orderId}
                  </Link>
                </td>
                <td>
                  <StatusBadge status={payment.status} />
                </td>
                <td>{money(payment.amount, payment.currency)}</td>
                <td>{money(payment.refundedAmount, payment.currency)}</td>
                <td>{payment.provider}</td>
                <td>{date(payment.createdAt)}</td>
              </tr>
            ))}
          </Table>
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
export function RefundForm({
  payment,
  onCreated,
  refunds = [],
}: {
  payment: Payment;
  refunds?: Refund[];
  onCreated?: (value: Refund) => void;
}) {
  const { userId } = useAdmin();
  const cache = useQueryClient();
  const balance = remaining(payment.amount, payment.refundedAmount);
  const [result, setResult] = useState<Refund | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const [attempt, setAttempt] = useState<{
    key: string;
    body: { amount: string; reason: string };
  } | null>(null);
  const schema = z.object({
    amount: z
      .string()
      .trim()
      .regex(
        /^\d{1,17}(?:\.\d{1,2})?$/,
        "Enter a positive amount with at most two decimal places",
      )
      .refine((value) => {
        try {
          return new Decimal(value).gte("0.01");
        } catch {
          return false;
        }
      }, "Enter a positive refund amount")
      .refine((value) => {
        try {
          return new Decimal(value).lte(balance);
        } catch {
          return false;
        }
      }, `Amount exceeds the remaining balance of ${balance}`),
    reason: z.enum(refundReasons, { error: "Choose a refund reason" }),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { amount: "", reason: undefined },
  });
  const mutation = useMutation({
    retry: false,
    mutationFn: (value: {
      key: string;
      body: { amount: string; reason: string };
    }) => api.admin.refund(payment.id, value.body, value.key),
    onSuccess: (value) => {
      setUncertain(false);
      setResult(value);
      void cache.invalidateQueries({ queryKey: ["admin", userId] });
      void cache.invalidateQueries({ queryKey: ["orders", userId] });
      onCreated?.(value);
      toast.success("Refund request accepted");
    },
    onError: (error) => {
      const ambiguous =
        !(error instanceof ApiError) ||
        error.status >= 500 ||
        error.status === 408 ||
        error.status === 429 ||
        error.code === "REFUND_IDEMPOTENCY_CONFLICT";
      setUncertain(ambiguous);
      if (!ambiguous) setAttempt(null);
      void cache.invalidateQueries({
        queryKey: ["admin", userId, "payment", payment.id],
      });
    },
  });
  if (result) {
    const current = refunds.find((refund) => refund.id === result.id) ?? result;
    const canRequest =
      ["SUCCEEDED", "PARTIALLY_REFUNDED"].includes(payment.status) &&
      new Decimal(balance).gt(0) &&
      !["PENDING", "PROCESSING"].includes(current.status);
    return (
      <Alert tone="info">
        <p>
          Refund {current.id}: <StatusBadge status={current.status} />
        </p>
        <p>
          {money(current.amount, current.currency)} · {label(current.reason)}
        </p>
        {current.status === "PROCESSING" || current.status === "PENDING" ? (
          <p>
            Waiting for provider confirmation. The payment and refund history
            update from the backend.
          </p>
        ) : null}
        {canRequest && (
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              setResult(null);
              setAttempt(null);
              setUncertain(false);
              mutation.reset();
              form.reset({ amount: "", reason: undefined });
            }}
          >
            Start another refund
          </Button>
        )}
      </Alert>
    );
  }
  if (payment.status === "REFUND_PENDING")
    return (
      <Alert>
        Waiting for provider confirmation of the pending refund. Refresh the
        payment to see the confirmed result.
      </Alert>
    );
  if (
    !["SUCCEEDED", "PARTIALLY_REFUNDED"].includes(payment.status) ||
    new Decimal(balance).lte(0)
  )
    return (
      <Alert tone="info">
        Refunds are available only for successful payments with a remaining
        balance.
      </Alert>
    );
  return (
    <form
      onSubmit={form.handleSubmit((value) => {
        const current = attempt ?? {
          key: crypto.randomUUID(),
          body: {
            amount: new Decimal(value.amount).toFixed(2),
            reason: value.reason,
          },
        };
        setAttempt(current);
        mutation.mutate(current);
      })}
      noValidate
    >
      <p>
        Remaining refundable balance:{" "}
        <strong>{money(balance, payment.currency)}</strong>
      </p>
      <Field
        label="Refund amount"
        error={form.formState.errors.amount?.message}
      >
        <Input
          {...form.register("amount")}
          inputMode="decimal"
          disabled={mutation.isPending || uncertain}
          autoComplete="off"
        />
      </Field>
      <Button
        type="button"
        variant="secondary"
        disabled={mutation.isPending || uncertain}
        onClick={() =>
          form.setValue("amount", balance, { shouldValidate: true })
        }
      >
        Use full remaining amount
      </Button>
      <Field
        label="Refund reason"
        error={form.formState.errors.reason?.message}
      >
        <Select
          {...form.register("reason")}
          disabled={mutation.isPending || uncertain}
        >
          <option value="">Choose a reason</option>
          <StatusOptions values={refundReasons} />
        </Select>
      </Field>
      {mutation.isError && <ErrorState error={mutation.error} />}{" "}
      {uncertain && (
        <Alert>
          The result is uncertain. Retry this same refund with its existing
          idempotency key, or refresh the payment to check the recorded result.
          Keep this form open until the request is confirmed.
        </Alert>
      )}
      <Button
        type={uncertain ? "button" : "submit"}
        pending={mutation.isPending}
        onClick={
          uncertain
            ? () => {
                if (attempt) mutation.mutate(attempt);
              }
            : undefined
        }
      >
        {uncertain ? "Retry same refund" : "Request refund"}
      </Button>
    </form>
  );
}
function RefundHistory({
  paymentId,
  revision,
}: {
  paymentId: string;
  revision: string;
}) {
  const { userId, enabled } = useAdmin();
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ["admin", userId, "refunds", paymentId, revision, page],
    queryFn: () => api.admin.refunds(paymentId, { page, size: 20 }),
    enabled,
  });
  return (
    <Card title="Refund history">
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => void query.refetch()} />
      ) : query.data.items.length === 0 ? (
        <EmptyState title="No refund requests" />
      ) : (
        <>
          <Table
            headers={[
              "Refund",
              "Amount",
              "Status",
              "Reason",
              "Failure",
              "Created",
              "Confirmed",
            ]}
          >
            {query.data.items.map((refund) => (
              <tr key={refund.id}>
                <td>{refund.id}</td>
                <td>{money(refund.amount, refund.currency)}</td>
                <td>
                  <StatusBadge status={refund.status} />
                </td>
                <td>{label(refund.reason)}</td>
                <td>{refund.failureCode ?? "—"}</td>
                <td>{date(refund.createdAt)}</td>
                <td>{date(refund.succeededAt ?? refund.failedAt)}</td>
              </tr>
            ))}
          </Table>
          <Pagination
            page={page}
            totalPages={query.data.totalPages}
            totalElements={query.data.totalElements}
            onPageChange={setPage}
          />
        </>
      )}
    </Card>
  );
}
export function PaymentDetailPage({ paymentId }: { paymentId: string }) {
  const { userId, enabled } = useAdmin();
  const [pollUntil, setPollUntil] = useState(() => Date.now() + 60000);
  const query = useQuery({
    queryKey: ["admin", userId, "payment", paymentId],
    queryFn: () => api.admin.payment(paymentId),
    enabled,
    refetchInterval: (query) =>
      Date.now() < pollUntil &&
      ["PENDING", "PROCESSING", "REFUND_PENDING", "CANCEL_REQUESTED"].includes(
        query.state.data?.payment.status ?? "",
      )
        ? 3000
        : false,
  });
  if (query.isPending) return <LoadingState />;
  if (query.isError)
    return (
      <ErrorState error={query.error} retry={() => void query.refetch()} />
    );
  const { payment, attempts } = query.data;
  return (
    <div className="stack">
      <PageHeader
        title="Payment details"
        description={payment.id}
        actions={
          <>
            <Link className="btn btn-secondary" href="/admin/payments">
              All payments
            </Link>
            <Button
              variant="secondary"
              pending={query.isFetching}
              onClick={() => void query.refetch()}
            >
              Refresh payment
            </Button>
          </>
        }
      />
      <div className="split">
        <Card title="Payment">
          <div className="summary-line">
            <span>Status</span>
            <StatusBadge status={payment.status} />
          </div>
          <div className="summary-line">
            <span>Amount</span>
            <strong>{money(payment.amount, payment.currency)}</strong>
          </div>
          <div className="summary-line">
            <span>Confirmed refunded amount</span>
            <span>{money(payment.refundedAmount, payment.currency)}</span>
          </div>
          <div className="summary-line">
            <span>Remaining refundable balance</span>
            <span>
              {money(
                remaining(payment.amount, payment.refundedAmount),
                payment.currency,
              )}
            </span>
          </div>
          <p>
            Order:{" "}
            <Link href={`/admin/orders/${payment.orderId}`}>
              {payment.orderId}
            </Link>
          </p>
          <p>Provider: {payment.provider}</p>
          <p>Failure code: {payment.failureCode ?? "—"}</p>
          <p className="small muted">
            Created {date(payment.createdAt)} · Updated{" "}
            {date(payment.updatedAt)}
            <br />
            Payment deadline {date(payment.paymentDeadline)}
            <br />
            Succeeded {date(payment.succeededAt)} · Failed{" "}
            {date(payment.failedAt)} · Cancelled {date(payment.cancelledAt)}
          </p>
        </Card>
        <Card title="Request a refund">
          <RefundForm
            payment={payment}
            refunds={query.data.refunds}
            onCreated={() => {
              setPollUntil(Date.now() + 60000);
              void query.refetch();
            }}
          />
        </Card>
      </div>
      <Card title="Payment attempts">
        {attempts.length ? (
          <Table
            headers={[
              "Attempt",
              "Status",
              "Failure code",
              "Created",
              "Completed",
            ]}
          >
            {attempts.map((attempt) => (
              <tr key={attempt.attemptNumber}>
                <td>{attempt.attemptNumber}</td>
                <td>
                  <StatusBadge status={attempt.status} />
                </td>
                <td>{attempt.failureCode ?? "—"}</td>
                <td>{date(attempt.createdAt)}</td>
                <td>{date(attempt.completedAt)}</td>
              </tr>
            ))}
          </Table>
        ) : (
          <EmptyState title="No payment attempts" />
        )}
      </Card>
      <RefundHistory paymentId={paymentId} revision={payment.updatedAt} />
    </div>
  );
}
