import Decimal from "decimal.js";
import { format, formatDistanceToNow, isValid } from "date-fns";
import type { Money } from "@/types/contracts";
export function money(
  value: Money | null | undefined,
  currency = "USD",
): string {
  if (value == null) return "—";
  try {
    const text = new Decimal(value).toFixed(2);
    const [whole, fraction] = text.split(".");
    const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    const symbol =
      new Intl.NumberFormat("en", { style: "currency", currency })
        .formatToParts(0)
        .find((p) => p.type === "currency")?.value ?? currency;
    return `${symbol} ${grouped}.${fraction}`;
  } catch {
    return "—";
  }
}
export const remaining = (amount: Money, refunded: Money) =>
  Decimal.max(new Decimal(amount).minus(refunded), 0).toFixed(2);
export function date(value?: string | null) {
  if (!value) return "—";
  const d = new Date(value);
  return isValid(d) ? format(d, "dd MMM yyyy, HH:mm") : "—";
}
export function relativeDate(value: string) {
  const d = new Date(value);
  return isValid(d) ? formatDistanceToNow(d, { addSuffix: true }) : "—";
}
export function label(value: string) {
  return value
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/^./, (c) => c.toUpperCase());
}
