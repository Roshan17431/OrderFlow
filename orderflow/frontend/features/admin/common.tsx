"use client";
import { useAuth } from "@/providers/auth-provider";
import { label } from "@/lib/formatting";
import { Select } from "@/components/ui";
export function useAdmin() {
  const { user, status } = useAuth();
  return {
    userId: user?.id,
    enabled: status === "authenticated" && !!user?.roles.includes("ADMIN"),
  };
}
export function StatusOptions({ values }: { values: readonly string[] }) {
  return (
    <>
      {values.map((value) => (
        <option key={value} value={value}>
          {label(value)}
        </option>
      ))}
    </>
  );
}
export function StatusFilter({
  values,
  name = "status",
}: {
  values: readonly string[];
  name?: string;
}) {
  return (
    <Select name={name} defaultValue="">
      <option value="">All statuses</option>
      <StatusOptions values={values} />
    </Select>
  );
}
export const orderStatuses = [
  "INVENTORY_PENDING",
  "INVENTORY_RESERVED",
  "INVENTORY_REJECTED",
  "PAYMENT_PENDING",
  "PAID",
  "PAYMENT_FAILED",
  "CANCEL_REQUESTED",
  "CANCELLED",
  "PARTIALLY_REFUNDED",
  "REFUNDED",
] as const;
export const paymentStatuses = [
  "PENDING",
  "REQUIRES_ACTION",
  "PROCESSING",
  "SUCCEEDED",
  "FAILED",
  "CANCEL_REQUESTED",
  "CANCELLED",
  "REFUND_PENDING",
  "PARTIALLY_REFUNDED",
  "REFUNDED",
] as const;
export const refundReasons = [
  "CUSTOMER_REQUEST",
  "DUPLICATE_PAYMENT",
  "ORDER_CANCELLED",
  "SERVICE_FAILURE",
  "SYSTEM_COMPENSATION",
  "OTHER",
] as const;
export function instantInput(value: FormDataEntryValue | null, end = false) {
  if (!value) return undefined;
  return `${String(value)}T${end ? "23:59:59.999" : "00:00:00.000"}Z`;
}
export function optional(value: FormDataEntryValue | null) {
  return value ? String(value).trim() || undefined : undefined;
}
