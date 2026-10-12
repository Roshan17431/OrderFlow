"use client";
import {
  useEffect,
  useRef,
  useId,
  cloneElement,
  isValidElement,
  type ReactNode,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from "react";
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Inbox,
  Loader2,
  X,
} from "lucide-react";
import { ApiError } from "@/lib/api/http";
import { label } from "@/lib/formatting";
export function Button({
  children,
  pending,
  variant = "primary",
  className = "",
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  pending?: boolean;
  variant?: "primary" | "secondary" | "ghost" | "danger";
}) {
  return (
    <button
      className={`btn btn-${variant} ${className}`}
      disabled={disabled || pending}
      aria-busy={pending || undefined}
      {...props}
    >
      {pending && <Loader2 size={16} className="spin" />}
      {children}
    </button>
  );
}
export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`input ${props.className ?? ""}`} />;
}
export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={`input ${props.className ?? ""}`} />;
}
export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      {...props}
      className={`input textarea ${props.className ?? ""}`}
    />
  );
}
export function Field({
  label: caption,
  error,
  children,
  htmlFor,
}: {
  label: string;
  error?: string;
  children: ReactNode;
  htmlFor?: string;
}) {
  const generatedId = useId();
  const control = isValidElement<{
    id?: string;
    "aria-describedby"?: string;
    "aria-invalid"?: boolean;
  }>(children)
    ? children
    : null;
  const id = htmlFor ?? control?.props.id ?? generatedId;
  const errorId = id + "-error";
  const describedBy =
    [control?.props["aria-describedby"], error ? errorId : undefined]
      .filter(Boolean)
      .join(" ") || undefined;
  return (
    <div className="field">
      <label htmlFor={id}>
        <span>{caption}</span>
      </label>
      {control
        ? cloneElement(control, {
            id,
            "aria-describedby": describedBy,
            "aria-invalid":
              control.props["aria-invalid"] ?? (error ? true : undefined),
          })
        : children}
      {error && (
        <p id={errorId} className="field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
export function Card({
  children,
  className = "",
  title,
}: {
  children: ReactNode;
  className?: string;
  title?: string;
}) {
  return (
    <section className={`card ${className}`}>
      {title && <h2>{title}</h2>}
      {children}
    </section>
  );
}
export function PageHeader({
  title,
  description,
  actions,
  eyebrow,
  level = 1,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
  eyebrow?: string;
  level?: 1 | 2;
}) {
  const Heading = level === 2 ? "h2" : "h1";
  return (
    <div className="page-header">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <Heading>{title}</Heading>
        {description && <p className="muted">{description}</p>}
      </div>
      {actions && <div className="row">{actions}</div>}
    </div>
  );
}
export function StatCard({
  label: caption,
  value,
  description,
}: {
  label: string;
  value: ReactNode;
  description?: string;
}) {
  return (
    <Card>
      <p className="muted small">{caption}</p>
      <div className="stat-value">{value}</div>
      {description && <p className="small muted">{description}</p>}
    </Card>
  );
}
export function StatusBadge({ status }: { status: string }) {
  const tone = /FAILED|REJECTED|INACTIVE|ARCHIVED/.test(status)
    ? "negative"
    : /SUCCEEDED|PAID|DELIVERED|ACTIVE|AVAILABLE/.test(status)
      ? "positive"
      : /PENDING|PROCESSING|REQUESTED|ACTION|BUSY/.test(status)
        ? "warning"
        : "neutral";
  return (
    <span className={`badge badge-${tone}`}>
      <span aria-hidden="true" className="badge-dot" />
      {label(status)}
    </span>
  );
}
export function EmptyState({
  title = "Nothing here yet",
  description,
  action,
}: {
  title?: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <Inbox size={32} aria-hidden="true" />
      <h2>{title}</h2>
      {description && <p className="muted">{description}</p>}
      {action}
    </div>
  );
}
export function ErrorState({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  const e = error instanceof ApiError ? error : null;
  return (
    <div className="error-state" role="alert">
      <AlertCircle size={22} />
      <div>
        <h2>
          {e?.status === 403
            ? "Permission denied"
            : e?.status === 404
              ? "Not found"
              : "We couldn’t complete that request"}
        </h2>
        <p>{e?.message ?? "Something went wrong. Please try again."}</p>
        {e?.retryAfter != null && <p>Try again in {e.retryAfter} seconds.</p>}
        {e?.fieldErrors.map((x) => (
          <p key={x.field}>
            {x.field}: {x.message}
          </p>
        ))}
        {e?.correlationId && (
          <p className="small">Support reference: {e.correlationId}</p>
        )}
        {retry && (
          <Button variant="secondary" onClick={retry}>
            Try again
          </Button>
        )}
      </div>
    </div>
  );
}
export function LoadingState({
  label: caption = "Loading",
}: {
  label?: string;
}) {
  return (
    <div role="status" aria-label={caption} className="loading-state">
      <span className="skeleton" />
      <span className="skeleton" />
      <span className="skeleton" />
      <span className="sr-only">{caption}</span>
    </div>
  );
}
export const Skeleton = ({ className = "" }: { className?: string }) => (
  <div className={`skeleton ${className}`} aria-hidden="true" />
);
export function Pagination({
  page,
  totalPages,
  onPageChange,
  totalElements,
}: {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  totalElements?: number;
}) {
  return (
    <nav className="pagination" aria-label="Pagination">
      <p className="small muted">
        {totalElements != null ? `${totalElements} results · ` : ""}Page{" "}
        {totalPages ? page + 1 : 0} of {totalPages}
      </p>
      <div className="row">
        <Button
          variant="secondary"
          aria-label="Previous page"
          disabled={page === 0}
          onClick={() => onPageChange(page - 1)}
        >
          <ArrowLeft size={16} />
        </Button>
        <Button
          variant="secondary"
          aria-label="Next page"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          <ArrowRight size={16} />
        </Button>
      </div>
    </nav>
  );
}
export function Table({
  headers,
  children,
}: {
  headers: string[];
  children: ReactNode;
}) {
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            {headers.map((h) => (
              <th key={h} scope="col">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}
export function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title,
  description,
  pending,
  confirmLabel = "Confirm",
}: {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void | Promise<void>;
  title: string;
  description?: string;
  pending?: boolean;
  confirmLabel?: string;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (open && !d?.open) d?.showModal();
    else if (!open && d?.open) d.close();
  }, [open]);
  return (
    <dialog
      ref={ref}
      className="dialog"
      onCancel={(event) => {
        event.preventDefault();
        if (!pending) onClose();
      }}
      aria-label={title}
    >
      <div className="row justify-between">
        <h2>{title}</h2>
        <Button
          variant="ghost"
          aria-label="Close dialog"
          disabled={pending}
          onClick={onClose}
        >
          <X size={18} />
        </Button>
      </div>
      {description && <p>{description}</p>}
      <div className="row dialog-actions">
        <Button variant="secondary" disabled={pending} onClick={onClose}>
          Go back
        </Button>
        <Button
          variant="danger"
          pending={pending}
          onClick={() => void onConfirm()}
        >
          {confirmLabel}
        </Button>
      </div>
    </dialog>
  );
}
export function Alert({
  children,
  tone = "warning",
}: {
  children: ReactNode;
  tone?: "warning" | "info" | "error";
}) {
  return (
    <div
      className={`alert alert-${tone}`}
      role={tone === "error" ? "alert" : "status"}
    >
      {children}
    </div>
  );
}
