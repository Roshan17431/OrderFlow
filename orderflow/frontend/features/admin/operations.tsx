"use client";
import Link from "next/link";
import { useState } from "react";
import Decimal from "decimal.js";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { date, label } from "@/lib/formatting";
import {
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
  StatCard,
  StatusBadge,
  Table,
  Textarea,
} from "@/components/ui";
import type { Incident, Money, QueryParams } from "@/types/contracts";
import { instantInput, useAdmin } from "./common";
function volume(value: Money) {
  try {
    return new Decimal(value).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  } catch {
    return "—";
  }
}
export function AdminOverview({
  operations = false,
}: {
  operations?: boolean;
}) {
  const { userId, enabled } = useAdmin();
  const overview = useQuery({
    queryKey: ["admin", userId, "overview"],
    queryFn: () => api.operations.overview(),
    enabled,
  });
  const snapshot = useQuery({
    queryKey: ["admin", userId, "dailyRefund", overview.data?.date],
    queryFn: () =>
      api.operations.reports({
        from: overview.data!.date,
        to: overview.data!.date,
        page: 0,
        size: 1,
      }),
    enabled: enabled && !!overview.data?.date,
  });
  if (overview.isPending) return <LoadingState />;
  if (overview.isError)
    return (
      <ErrorState
        error={overview.error}
        retry={() => void overview.refetch()}
      />
    );
  const value = overview.data;
  const metrics = [
    ["Orders created", value.ordersCreated],
    ["Successful payments", value.paymentsSucceeded],
    ["Failed payments", value.paymentsFailed],
    ["Payment volume", volume(value.paymentVolume)],
    ["Inventory reservation failures", value.inventoryReservationFailures],
    ["Pending assignments", value.deliveriesPendingAssignment],
    ["Deliveries completed", value.deliveriesCompleted],
    ["Deliveries failed", value.deliveriesFailed],
    ["Open incidents", value.openIncidents],
    ["Notification failures", value.notificationsFailed],
  ] as const;
  const counts = [
    {
      name: "Payments",
      Succeeded: value.paymentsSucceeded,
      Failed: value.paymentsFailed,
    },
    {
      name: "Deliveries",
      Succeeded: value.deliveriesCompleted,
      Failed: value.deliveriesFailed,
    },
  ];
  return (
    <div className="stack">
      <PageHeader
        eyebrow={value.date}
        title={operations ? "Operations overview" : "Administration"}
        description="Daily business activity from the operational read models."
        actions={
          <Button
            variant="secondary"
            pending={overview.isFetching || snapshot.isFetching}
            onClick={() => {
              void overview.refetch();
              void snapshot.refetch();
            }}
          >
            Refresh overview
          </Button>
        }
      />
      <div className="stat-grid">
        {metrics.map(([caption, count]) => (
          <StatCard key={caption} label={caption} value={count} />
        ))}
        <StatCard
          label="Refund volume (daily report)"
          value={
            snapshot.isPending
              ? "Loading…"
              : snapshot.isError
                ? "Unavailable"
                : snapshot.data.items[0]
                  ? volume(snapshot.data.items[0].refundVolume)
                  : "—"
          }
          description={
            snapshot.isSuccess && !snapshot.data.items.length
              ? "No report for this date"
              : snapshot.data?.items[0]
                ? `Snapshot status: ${label(snapshot.data.items[0].status)}`
                : undefined
          }
        />
      </div>
      {snapshot.isError && (
        <ErrorState
          error={snapshot.error}
          retry={() => void snapshot.refetch()}
        />
      )}
      <p className="small muted">
        Volumes are exact aggregate amounts. Currency is not supplied by the
        operations API. Daily reports are snapshots and may be unavailable for
        the current date.
      </p>
      <div className="split">
        <Card title="Payment and delivery outcomes">
          <div
            role="img"
            aria-label="Successful and failed payment and delivery counts"
          >
            <ResponsiveContainer
              width="100%"
              height={280}
              minWidth={240}
              initialDimension={{ width: 480, height: 280 }}
            >
              <BarChart data={counts}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="name" />
                <YAxis allowDecimals={false} />
                <Tooltip />
                <Legend />
                <Bar dataKey="Succeeded" fill="#4d633a" radius={[4, 4, 0, 0]} />
                <Bar dataKey="Failed" fill="#b56552" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card title="Operations workspace">
          <div className="stack">
            <Link
              className="btn btn-secondary"
              href="/admin/operations/reports"
            >
              Explore daily reports
            </Link>
            <Link
              className="btn btn-secondary"
              href="/admin/operations/incidents"
            >
              Review operational incidents
            </Link>
            <Link className="btn btn-secondary" href="/admin/operations/jobs">
              Inspect scheduled jobs
            </Link>
            <Link className="btn btn-secondary" href="/admin/deliveries">
              Inspect delivery assignments
            </Link>
          </div>
        </Card>
      </div>
    </div>
  );
}
const reportSchema = z
  .object({
    from: z.string(),
    to: z.string(),
    sort: z.enum(["reportDate,desc", "reportDate,asc"]),
  })
  .refine((value) => !value.from || !value.to || value.from <= value.to, {
    message: "End date must be on or after start date",
    path: ["to"],
  });
export function ReportsPage() {
  const { userId, enabled } = useAdmin();
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<QueryParams>({
    sort: "reportDate,desc",
  });
  const form = useForm<z.infer<typeof reportSchema>>({
    resolver: zodResolver(reportSchema),
    defaultValues: { from: "", to: "", sort: "reportDate,desc" },
  });
  const query = useQuery({
    queryKey: ["admin", userId, "reports", filters, page],
    queryFn: () => api.operations.reports({ ...filters, page, size: 20 }),
    enabled,
  });
  const data = query.data?.items
    .slice()
    .sort((a, b) => a.reportDate.localeCompare(b.reportDate));
  return (
    <div className="stack">
      <PageHeader
        title="Daily reports"
        description="Generated snapshots with exact totals and activity trends."
      />
      <Card>
        <form
          onSubmit={form.handleSubmit((value) => {
            setFilters({
              ...value,
              from: value.from || undefined,
              to: value.to || undefined,
            });
            setPage(0);
          })}
          noValidate
        >
          <div className="form-grid">
            <Field label="From date">
              <Input type="date" {...form.register("from")} />
            </Field>
            <Field label="To date" error={form.formState.errors.to?.message}>
              <Input type="date" {...form.register("to")} />
            </Field>
            <Field label="Sort reports">
              <Select {...form.register("sort")}>
                <option value="reportDate,desc">Newest first</option>
                <option value="reportDate,asc">Oldest first</option>
              </Select>
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
          title="No daily reports"
          description="Reports appear after scheduled generation. Try another date range."
        />
      ) : (
        <>
          <Card title="Activity over the displayed report page">
            <div
              role="img"
              aria-label="Daily orders, successful payments, and completed deliveries"
            >
              <ResponsiveContainer
                width="100%"
                height={300}
                minWidth={240}
                initialDimension={{ width: 600, height: 300 }}
              >
                <LineChart data={data}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="reportDate" />
                  <YAxis allowDecimals={false} />
                  <Tooltip />
                  <Legend />
                  <Line
                    name="Orders"
                    type="monotone"
                    dataKey="ordersCreated"
                    stroke="#4d633a"
                    strokeWidth={2}
                  />
                  <Line
                    name="Successful payments"
                    type="monotone"
                    dataKey="paymentsSucceeded"
                    stroke="#758fac"
                    strokeWidth={2}
                  />
                  <Line
                    name="Completed deliveries"
                    type="monotone"
                    dataKey="deliveriesCompleted"
                    stroke="#b88653"
                    strokeWidth={2}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </Card>
          <Table
            headers={[
              "Date / snapshot",
              "Orders",
              "Payments succeeded / failed",
              "Payment volume",
              "Refund volume",
              "Inventory failures",
              "Deliveries completed / failed",
              "Notifications delivered / failed",
            ]}
          >
            {query.data.items.map((report) => (
              <tr key={report.reportDate}>
                <td>
                  {report.reportDate}
                  <p>
                    <StatusBadge status={report.status} />
                  </p>
                  <p className="small muted">
                    Generated {date(report.generatedAt)}
                    <br />
                    Finalized {date(report.finalizedAt)} · v{report.version}
                  </p>
                </td>
                <td>{report.ordersCreated}</td>
                <td>
                  {report.paymentsSucceeded} / {report.paymentsFailed}
                </td>
                <td>{volume(report.paymentVolume)}</td>
                <td>{volume(report.refundVolume)}</td>
                <td>{report.inventoryReservationFailures}</td>
                <td>
                  {report.deliveriesCompleted} / {report.deliveriesFailed}
                </td>
                <td>
                  {report.notificationsDelivered} / {report.notificationsFailed}
                </td>
              </tr>
            ))}
          </Table>
          <p className="small muted">
            Currency is not supplied by the reports API.
          </p>
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
const resolutionSchema = z.object({
  resolutionNote: z
    .string()
    .trim()
    .min(1, "Enter a resolution note")
    .max(300, "Use at most 300 characters"),
});
const incidentSchema = z
  .object({
    type: z.string().trim(),
    severity: z.string().trim(),
    resolved: z.enum(["", "false", "true"]),
    from: z.string(),
    to: z.string(),
  })
  .refine((value) => !value.from || !value.to || value.from <= value.to, {
    message: "End date must be on or after start date",
    path: ["to"],
  });
export function IncidentsPage() {
  const { userId, enabled } = useAdmin();
  const cache = useQueryClient();
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<QueryParams>({});
  const [selected, setSelected] = useState<Incident | null>(null);
  const filterForm = useForm<z.infer<typeof incidentSchema>>({
    resolver: zodResolver(incidentSchema),
    defaultValues: { type: "", severity: "", resolved: "", from: "", to: "" },
  });
  const form = useForm<z.infer<typeof resolutionSchema>>({
    resolver: zodResolver(resolutionSchema),
    defaultValues: { resolutionNote: "" },
  });
  const query = useQuery({
    queryKey: ["admin", userId, "incidents", filters, page],
    queryFn: () => api.operations.incidents({ ...filters, page, size: 20 }),
    enabled,
  });
  const resolve = useMutation({
    retry: false,
    mutationFn: (note: string) => api.operations.resolve(selected!.id, note),
    onSuccess: () => {
      setSelected(null);
      toast.success("Incident resolved");
      void cache.invalidateQueries({
        queryKey: ["admin", userId, "incidents"],
      });
      void cache.invalidateQueries({ queryKey: ["admin", userId, "overview"] });
    },
    onError: () =>
      void cache.invalidateQueries({
        queryKey: ["admin", userId, "incidents"],
      }),
  });
  return (
    <div className="stack">
      <PageHeader
        title="Operational incidents"
        description="Review service failures and record resolution notes."
      />
      <Card>
        <form
          onSubmit={filterForm.handleSubmit((value) => {
            setFilters({
              type: value.type || undefined,
              severity: value.severity || undefined,
              resolved:
                value.resolved === "" ? undefined : value.resolved === "true",
              from: instantInput(value.from),
              to: instantInput(value.to, true),
            });
            setPage(0);
          })}
          noValidate
        >
          <div className="form-grid">
            <Field label="Incident type">
              <Input
                {...filterForm.register("type")}
                placeholder="Exact incident type"
              />
            </Field>
            <Field label="Severity">
              <Input
                {...filterForm.register("severity")}
                placeholder="Exact severity code"
              />
            </Field>
            <Field label="Resolution status">
              <Select {...filterForm.register("resolved")}>
                <option value="">All incidents</option>
                <option value="false">Open</option>
                <option value="true">Resolved</option>
              </Select>
            </Field>
            <Field label="Occurred from (UTC)">
              <Input type="date" {...filterForm.register("from")} />
            </Field>
            <Field
              label="Occurred to (UTC)"
              error={filterForm.formState.errors.to?.message}
            >
              <Input type="date" {...filterForm.register("to")} />
            </Field>
          </div>
          <Button type="submit">Apply filters</Button>
        </form>
      </Card>
      {selected && (
        <Card title={`Resolve ${label(selected.incident_type)}`}>
          <p>
            {selected.id} · {selected.reason_code}
          </p>
          <form
            onSubmit={form.handleSubmit((value) =>
              resolve.mutate(value.resolutionNote),
            )}
            noValidate
          >
            <Field
              label="Resolution note"
              error={form.formState.errors.resolutionNote?.message}
            >
              <Textarea
                {...form.register("resolutionNote")}
                maxLength={300}
                disabled={resolve.isPending}
              />
            </Field>
            <p className="small muted">
              Maximum 300 characters. This action records resolution of the
              incident.
            </p>
            {resolve.isError && <ErrorState error={resolve.error} />}
            <div className="row">
              <Button type="submit" pending={resolve.isPending}>
                Confirm resolution
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={resolve.isPending}
                onClick={() => setSelected(null)}
              >
                Close resolution
              </Button>
            </div>
          </form>
        </Card>
      )}
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => void query.refetch()} />
      ) : query.data.items.length === 0 ? (
        <EmptyState title="No matching incidents" />
      ) : (
        <>
          <Table
            headers={[
              "Incident",
              "Severity",
              "Source service",
              "Reason",
              "Occurred",
              "Resolution",
              "Action",
            ]}
          >
            {query.data.items.map((incident) => (
              <tr key={incident.id}>
                <td>
                  {label(incident.incident_type)}
                  <p className="small muted">{incident.id}</p>
                </td>
                <td>
                  <StatusBadge status={incident.severity} />
                </td>
                <td>{incident.source_service}</td>
                <td>{incident.reason_code}</td>
                <td>{date(incident.occurred_at)}</td>
                <td>
                  {incident.resolved ? "Resolved" : "Open"}
                  {incident.resolved && (
                    <p className="small muted">
                      {date(incident.resolved_at)}
                      <br />
                      {incident.resolution_note}
                    </p>
                  )}
                </td>
                <td>
                  {!incident.resolved && (
                    <Button
                      variant="secondary"
                      onClick={() => {
                        form.reset({ resolutionNote: "" });
                        resolve.reset();
                        setSelected(incident);
                      }}
                    >
                      Resolve incident
                    </Button>
                  )}
                </td>
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
export function JobsPage() {
  const { userId, enabled } = useAdmin();
  const query = useQuery({
    queryKey: ["admin", userId, "jobs"],
    queryFn: () => api.operations.jobs(),
    enabled,
  });
  return (
    <div className="stack">
      <PageHeader
        title="Scheduled jobs"
        description="Read the last recorded run of each background job."
        actions={
          <Button
            variant="secondary"
            pending={query.isFetching}
            onClick={() => void query.refetch()}
          >
            Refresh jobs
          </Button>
        }
      />
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => void query.refetch()} />
      ) : query.data.length === 0 ? (
        <EmptyState title="No recorded scheduled jobs" />
      ) : (
        <Table
          headers={[
            "Job",
            "Last status",
            "Started",
            "Completed",
            "Duration",
            "Failures",
          ]}
        >
          {query.data.map((job) => (
            <tr key={job.jobName}>
              <td>{job.jobName}</td>
              <td>
                {job.lastStatus ? (
                  <StatusBadge status={job.lastStatus} />
                ) : (
                  "Not run"
                )}
              </td>
              <td>{date(job.lastStartedAt)}</td>
              <td>{date(job.lastCompletedAt)}</td>
              <td>
                {job.lastDurationMs == null
                  ? "—"
                  : `${job.lastDurationMs.toLocaleString()} ms`}
              </td>
              <td>{job.failureCount}</td>
            </tr>
          ))}
        </Table>
      )}
    </div>
  );
}
