import type { OrderStatus } from "@/types/contracts";
export const orderStatuses: OrderStatus[] = [
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
];
export function canCancelOrder(status: string) {
  return [
    "INVENTORY_PENDING",
    "INVENTORY_RESERVED",
    "PAYMENT_PENDING",
  ].includes(status);
}
export function orderPollingInterval(
  status: string | undefined,
  startedAt: number,
  now: number,
  failed: boolean,
  fulfillmentStatus?: string,
): 2000 | false {
  const inProgress = [
    "INVENTORY_PENDING",
    "INVENTORY_RESERVED",
    "PAYMENT_PENDING",
    "CANCEL_REQUESTED",
  ].includes(status ?? "");
  const fulfillmentPending =
    ["PAID", "PARTIALLY_REFUNDED", "REFUNDED"].includes(status ?? "") &&
    !!fulfillmentStatus &&
    !["DELIVERED", "DELIVERY_FAILED", "DELIVERY_CANCELLED"].includes(
      fulfillmentStatus,
    );
  return !failed &&
    now - startedAt < 120000 &&
    (inProgress || fulfillmentPending)
    ? 2000
    : false;
}
export function paymentPollingInterval(
  status: string | undefined,
  startedAt: number,
  now: number,
  failed: boolean,
): 2000 | false {
  return !failed &&
    now - startedAt < 120000 &&
    (!status ||
      [
        "PENDING",
        "REQUIRES_ACTION",
        "PROCESSING",
        "CANCEL_REQUESTED",
        "REFUND_PENDING",
      ].includes(status))
    ? 2000
    : false;
}
export function paymentCanConfirm(
  orderStatus: string,
  payment:
    | { status: string; provider: string; paymentDeadline: string }
    | undefined,
  now = Date.now(),
) {
  return (
    orderStatus === "PAYMENT_PENDING" &&
    payment?.status === "PENDING" &&
    payment.provider === "SIMULATOR" &&
    Date.parse(payment.paymentDeadline) > now
  );
}
export function financialMessage(status: string) {
  const messages: Record<string, string> = {
    INVENTORY_PENDING:
      "Your order has been accepted. Inventory reservation is in progress.",
    INVENTORY_RESERVED: "Inventory is reserved. We are preparing your payment.",
    INVENTORY_REJECTED:
      "Inventory could not be reserved. This order cannot proceed. Review your cart before placing a new order.",
    PAYMENT_PENDING:
      "Your order is waiting for payment. Confirm only when its payment is ready.",
    PAID: "Payment has been confirmed by the backend. Delivery progress appears separately below.",
    PAYMENT_FAILED:
      "Payment failed. Review the failure details before placing a new order.",
    CANCEL_REQUESTED:
      "Your cancellation is being processed. Inventory and payment cancellation may take a moment.",
    CANCELLED: "Your order was cancelled.",
    PARTIALLY_REFUNDED:
      "Part of your payment has been refunded. Delivery status is shown separately.",
    REFUNDED:
      "Your payment has been refunded. Delivery status is shown separately.",
  };
  return messages[status] ?? "Reload to see the latest order state.";
}
