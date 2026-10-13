import { describe, it, expect, vi, afterEach } from "vitest";
import { cloudTarget } from "../e2e/support/cloud-target";
afterEach(() => { vi.unstubAllEnvs(); vi.resetModules(); });
describe("authorized deployed browser target", () => {
  const approved = { ORDERFLOW_CLOUD_E2E: "true", ORDERFLOW_TARGET_ENVIRONMENT: "cloud-demo", E2E_BASE_URL: "https://demo.example.test", AWS_ACCOUNT_ID: "123456789012" };
  it("requires explicit authorization environment and account", () => {
    for (const key of ["ORDERFLOW_CLOUD_E2E", "ORDERFLOW_TARGET_ENVIRONMENT", "AWS_ACCOUNT_ID"])
      expect(() => cloudTarget({ ...approved, [key]: "" })).toThrow();
  });
  it("rejects HTTP credentials URL paths and production targets", () => {
    for (const value of ["http://demo.example.test", "https://u:p@demo.example.test", "https://demo.example.test/api", "https://demo.example.test/?token=x"])
      expect(() => cloudTarget({ ...approved, E2E_BASE_URL: value })).toThrow();
    expect(() => cloudTarget({ ...approved, ORDERFLOW_TARGET_ENVIRONMENT: "production" })).toThrow();
  });
  it("permits local TLS only with explicit isolated simulation opt-in", () => {
    expect(() => cloudTarget({ ...approved, E2E_BASE_URL: "https://localhost:8443" })).toThrow();
    expect(cloudTarget({ ...approved, E2E_BASE_URL: "https://localhost:8443", ORDERFLOW_LOCAL_TLS_TEST: "true" }).ignoreHTTPSErrors).toBe(true);
    expect(() => cloudTarget({ ...approved, ORDERFLOW_LOCAL_TLS_TEST: "true" })).toThrow();
  });
  it("keeps deployed HTTPS certificate verification enabled", () => expect(cloudTarget(approved).ignoreHTTPSErrors).toBe(false));
});
it("an empty public API build value uses same-origin paths", async () => {
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "");
  vi.resetModules();
  expect((await import("../lib/api/http")).API_BASE).toBe("");
});
