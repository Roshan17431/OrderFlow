import { describe, expect, it } from "vitest";
import { assertExclusivePendingOrder, assertFixtureAssignment } from "../e2e/support/delivery-guard";

describe("synthetic delivery verification ownership", () => {
  const own = { id: "own-order", fulfillmentStatus: "DELIVERY_PENDING" };
  it("permits the sole pending synthetic order", () => {
    expect(() => assertExclusivePendingOrder({ items: [own], totalElements: 1 }, own.id)).not.toThrow();
  });
  it("refuses unrelated pending work before agent activation", () => {
    expect(() => assertExclusivePendingOrder({ items: [own, { id: "other-order", fulfillmentStatus: "DELIVERY_PENDING" }], totalElements: 2 }, own.id)).toThrow();
  });
  it("refuses a queue whose full scope is unavailable", () => {
    expect(() => assertExclusivePendingOrder({ items: [own], totalElements: 101 }, own.id)).toThrow();
    expect(() => assertExclusivePendingOrder({ items: [], totalElements: 0 }, own.id)).toThrow();
  });
  it("refuses a different assignment even when an address could match", () => {
    expect(() => assertFixtureAssignment("other-delivery", { id: "other-delivery", orderId: "other-order" }, "own-delivery", own.id)).toThrow();
    expect(() => assertFixtureAssignment("own-delivery", { id: "own-delivery", orderId: "other-order" }, "own-delivery", own.id)).toThrow();
  });
  it("permits only the exact synthetic delivery and order", () => {
    expect(() => assertFixtureAssignment("own-delivery", { id: "own-delivery", orderId: own.id }, "own-delivery", own.id)).not.toThrow();
  });
});
