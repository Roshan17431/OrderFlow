import { describe, expect, it } from "vitest";
import {
  catalogFilters,
  catalogQuery,
  validatePrices,
} from "@/features/catalog/filters";
import { shippingSchema, CheckoutAttempt } from "@/features/checkout/model";
import {
  canCancelOrder,
  orderPollingInterval,
  paymentPollingInterval,
  paymentCanConfirm,
  financialMessage,
} from "@/features/orders/state";

describe("catalog URL contract", () => {
  it("keeps supported search and price sorting while bounding invalid pagination", () => {
    const filters = catalogFilters(
      new URLSearchParams(
        "q= tea &category=pantry&minPrice=1.25&maxPrice=10&sort=price,asc&page=-5&vendor=secret",
      ),
    );
    expect(catalogQuery(filters)).toEqual({
      q: "tea",
      category: "pantry",
      minPrice: "1.25",
      maxPrice: "10",
      sort: "price,asc",
      page: 0,
      size: 12,
    });
  });
  it("rejects inverted and negative prices before a catalog request", () => {
    expect(validatePrices("20", "5")).toBeTruthy();
    expect(validatePrices("-1", "")).toBeTruthy();
    expect(validatePrices("0.01", "4.25")).toBeNull();
  });
  it("normalizes unsupported sorts and non-integer pages", () => {
    expect(
      catalogFilters(new URLSearchParams("sort=rating&page=1.5")).sort,
    ).toBe("createdAt,desc");
    expect(catalogFilters(new URLSearchParams("page=1.5")).page).toBe(0);
  });
});
const address = {
  recipientName: "R Lee",
  addressLine1: "23 Green Lane",
  addressLine2: "",
  city: "Pune",
  stateOrRegion: "Maharashtra",
  postalCode: "411001",
  countryCode: "IN",
  deliveryInstructions: "",
};
describe("shipping and checkout idempotency", () => {
  it("rejects blank, overlong, control-character and malformed country input", () => {
    expect(
      shippingSchema.safeParse({ ...address, recipientName: "   " }).success,
    ).toBe(false);
    expect(
      shippingSchema.safeParse({ ...address, addressLine1: "x".repeat(201) })
        .success,
    ).toBe(false);
    expect(
      shippingSchema.safeParse({
        ...address,
        deliveryInstructions: "hello\u0000",
      }).success,
    ).toBe(false);
    expect(
      shippingSchema.safeParse({ ...address, countryCode: "india" }).success,
    ).toBe(false);
    expect(
      shippingSchema.parse({ ...address, countryCode: "in" }).countryCode,
    ).toBe("IN");
  });
  it("reuses the same body and key after an uncertain identical retry", () => {
    const attempt = new CheckoutAttempt();
    const original = attempt.prepare({
      expectedCartVersion: 7,
      shippingAddress: address,
    });
    const retry = attempt.prepare({
      expectedCartVersion: 7,
      shippingAddress: { ...address },
    });
    expect(retry.key).toBe(original.key);
    expect(retry.body).toEqual(original.body);
    expect(
      attempt.prepare({ expectedCartVersion: 8, shippingAddress: address }).key,
    ).not.toBe(original.key);
  });
  it("starts a new idempotency attempt only when explicitly reset or the address changes", () => {
    const attempt = new CheckoutAttempt();
    const first = attempt.prepare({
      expectedCartVersion: 7,
      shippingAddress: address,
    });
    expect(
      attempt.prepare({
        expectedCartVersion: 7,
        shippingAddress: { ...address, city: "Mumbai" },
      }).key,
    ).not.toBe(first.key);
    const second = attempt.prepare({
      expectedCartVersion: 7,
      shippingAddress: address,
    });
    attempt.reset();
    expect(attempt.prepare(second.body).key).not.toBe(second.key);
  });
});
describe("authoritative asynchronous states", () => {
  it.each(["INVENTORY_PENDING", "INVENTORY_RESERVED", "PAYMENT_PENDING"])(
    "allows cancellation from %s",
    (status) => expect(canCancelOrder(status)).toBe(true),
  );
  it.each([
    "PAID",
    "CANCEL_REQUESTED",
    "INVENTORY_REJECTED",
    "DELIVERED",
    "REFUNDED",
    "PAYMENT_FAILED",
  ])("disallows cancellation from %s", (status) =>
    expect(canCancelOrder(status)).toBe(false),
  );
  it("stops polling after two minutes and on transport failures", () => {
    expect(orderPollingInterval("INVENTORY_PENDING", 0, 119999, false)).toBe(
      2000,
    );
    expect(orderPollingInterval("PAYMENT_PENDING", 0, 120000, false)).toBe(
      false,
    );
    expect(orderPollingInterval("INVENTORY_PENDING", 0, 2000, true)).toBe(
      false,
    );
    expect(orderPollingInterval("CANCELLED", 0, 2000, false)).toBe(false);
    expect(
      orderPollingInterval("PAID", 0, 2000, false, "DELIVERY_PENDING"),
    ).toBe(2000);
    expect(orderPollingInterval("PAID", 0, 2000, false, "DELIVERED")).toBe(
      false,
    );
  });
  it("bounds payment polling for missing or processing payments and stops on terminal or failed requests", () => {
    expect(paymentPollingInterval(undefined, 0, 2000, false)).toBe(2000);
    expect(paymentPollingInterval("PROCESSING", 0, 119999, false)).toBe(2000);
    expect(paymentPollingInterval("REQUIRES_ACTION", 0, 120000, false)).toBe(
      false,
    );
    expect(paymentPollingInterval("SUCCEEDED", 0, 2000, false)).toBe(false);
    expect(paymentPollingInterval("PENDING", 0, 2000, true)).toBe(false);
  });
  it("requires a real pending simulator payment, live deadline, and payment-ready order", () => {
    const payment = {
      status: "PENDING",
      provider: "SIMULATOR",
      paymentDeadline: "2026-10-12T10:00:00Z",
    };
    expect(
      paymentCanConfirm(
        "PAYMENT_PENDING",
        payment,
        Date.parse("2026-10-12T09:00:00Z"),
      ),
    ).toBe(true);
    expect(
      paymentCanConfirm(
        "INVENTORY_PENDING",
        payment,
        Date.parse("2026-10-12T09:00:00Z"),
      ),
    ).toBe(false);
    expect(
      paymentCanConfirm(
        "PAYMENT_PENDING",
        { ...payment, status: "PROCESSING" },
        0,
      ),
    ).toBe(false);
    expect(
      paymentCanConfirm(
        "PAYMENT_PENDING",
        payment,
        Date.parse("2026-10-12T11:00:00Z"),
      ),
    ).toBe(false);
    expect(paymentCanConfirm("PAYMENT_PENDING", undefined, 0)).toBe(false);
  });
  it("explains inventory rejection without asserting payment success", () => {
    expect(financialMessage("INVENTORY_REJECTED")).toMatch(/inventory/i);
    expect(financialMessage("INVENTORY_PENDING")).toMatch(/reserv/i);
  });
});
