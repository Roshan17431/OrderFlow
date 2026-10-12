import { z } from "zod";
import type { Address } from "@/types/contracts";
const safeText = (name: string, max: number) =>
  z
    .string()
    .trim()
    .min(1, `${name} is required`)
    .max(max, `${name} must be ${max} characters or fewer`)
    .regex(/^[^\p{Cc}\p{Cf}]+$/u, `${name} contains unsupported characters`);
const optionalText = (name: string, max: number) =>
  z
    .string()
    .trim()
    .max(max, `${name} must be ${max} characters or fewer`)
    .regex(/^[^\p{Cc}\p{Cf}]*$/u, `${name} contains unsupported characters`);
export const shippingSchema = z.object({
  recipientName: safeText("Recipient name", 120),
  addressLine1: safeText("Address line 1", 200),
  addressLine2: optionalText("Address line 2", 200),
  city: safeText("City", 120),
  stateOrRegion: safeText("State or region", 120),
  postalCode: safeText("Postal code", 32),
  countryCode: z
    .string()
    .trim()
    .toUpperCase()
    .regex(/^[A-Z]{2}$/, "Enter a two-letter country code, such as IN"),
  deliveryInstructions: optionalText("Delivery instructions", 300),
});
export type ShippingFields = z.infer<typeof shippingSchema>;
export interface CheckoutBody {
  expectedCartVersion: number;
  shippingAddress: Address;
}
export interface Attempt<T> {
  key: string;
  body: T;
}
export class CheckoutAttempt {
  private current: Attempt<CheckoutBody> | null = null;
  prepare(body: CheckoutBody): Attempt<CheckoutBody> {
    if (
      !this.current ||
      JSON.stringify(this.current.body) !== JSON.stringify(body)
    )
      this.current = { key: crypto.randomUUID(), body: structuredClone(body) };
    return this.current;
  }
  get() {
    return this.current;
  }
  reset() {
    this.current = null;
  }
}
