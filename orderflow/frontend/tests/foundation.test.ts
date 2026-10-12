import { describe, expect, it } from "vitest";
import { money, remaining } from "@/lib/formatting";
import { safeReturnUrl, roleHome } from "@/lib/auth/redirects";
import { normalizePage } from "@/lib/api/http";
import { lineAmount } from "@/features/cart/cart-summary";
describe("exact money", () => {
  it("subtracts decimal balances without floating point errors", () =>
    expect(remaining("0.30", "0.10")).toBe("0.20"));
  it("preserves large exact values in displays", () =>
    expect(money("99999999999999999.99", "USD")).toContain(
      "99,999,999,999,999,999.99",
    ));
  it("retains cents for the maximum supported price times maximum cart quantity", () =>
    expect(lineAmount("99999999999999999.99", 99)).toBe(
      "9899999999999999999.01",
    ));
});
describe("safe navigation", () => {
  it.each([
    "https://evil.test",
    "//evil.test",
    "/\\evil.test",
    "javascript:evil",
    "/%2f%2fevil.test",
    "/path/..//evil.test",
  ])("rejects external return %s", (value) =>
    expect(safeReturnUrl(value)).toBe("/products"),
  );
  it("keeps safe path", () =>
    expect(safeReturnUrl("/orders?status=PAID")).toBe("/orders?status=PAID"));
  it("maps roles without arbitrary URLs", () =>
    expect(roleHome(["ADMIN"])).toBe("/admin"));
});
describe("service pagination", () => {
  it("maps items without dropping totals", () =>
    expect(
      normalizePage({
        items: [1],
        page: 0,
        size: 20,
        totalElements: 31,
        totalPages: 2,
      }).items,
    ).toEqual([1]));
  it("maps content from payments and notifications", () =>
    expect(
      normalizePage({
        content: [2],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }).items,
    ).toEqual([2]));
  it("rejects malformed responses", () =>
    expect(() => normalizePage({})).toThrow());
});
