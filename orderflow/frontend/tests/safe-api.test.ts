import { expect, it, vi } from "vitest";
import type { APIRequestContext } from "@playwright/test";
import { safeApi } from "../e2e/support/safe-api";

it("does not expose private Playwright call-log headers after a transport failure", async () => {
  const api = { get: vi.fn().mockRejectedValue(new Error("Authorization: Bearer PRIVATE_FIXTURE_TOKEN; cookie=PRIVATE_FIXTURE_COOKIE")) };
  const context = safeApi(api as unknown as APIRequestContext);
  try {
    await context.get("/api/orders");
    throw new Error("Expected transport failure");
  } catch (error) {
    expect(String(error)).toContain("Verification API GET request failed");
    expect(String(error)).not.toContain("PRIVATE_FIXTURE");
    expect((error as Error).cause).toBeUndefined();
  }
});

it("keeps credential-bearing requests on the selected origin without following redirects", async () => {
  const response = { status: () => 200 };
  const api = { post: vi.fn().mockResolvedValue(response), dispose: vi.fn().mockResolvedValue(undefined) };
  const context = safeApi(api as unknown as APIRequestContext);
  expect(await context.post("/api/auth/login", { data: { password: "PRIVATE_FIXTURE_PASSWORD" }, maxRedirects: 5 })).toBe(response);
  expect(api.post.mock.calls[0][1]).toEqual({ data: { password: "PRIVATE_FIXTURE_PASSWORD" }, maxRedirects: 0 });
  await context.dispose();
  expect(api.dispose).toHaveBeenCalledOnce();
});
