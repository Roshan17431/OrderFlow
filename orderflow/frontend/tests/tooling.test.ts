import { it, expect } from "vitest";
import { spawnSync } from "node:child_process";
import path from "node:path";
const helper = path.resolve(
  import.meta.dirname,
  "../../scripts/complete-simulator-payment.mjs",
);
it("trusted simulator helper is disabled by default", () => {
  const r = spawnSync(
    process.execPath,
    [helper, "payment.succeeded", "00000000-0000-4000-8000-000000000001"],
    {
      env: { ...process.env, ORDERFLOW_SIMULATOR_HELPER: "false" },
      encoding: "utf8",
    },
  );
  expect(r.status).toBe(1);
  expect(r.stderr).toContain("Disabled");
});
it("trusted simulator helper refuses a remote gateway before reading secrets", () => {
  const r = spawnSync(
    process.execPath,
    [helper, "payment.succeeded", "00000000-0000-4000-8000-000000000001"],
    {
      env: {
        ...process.env,
        ORDERFLOW_SIMULATOR_HELPER: "true",
        ORDERFLOW_GATEWAY_URL: "https://example.com",
      },
      encoding: "utf8",
    },
  );
  expect(r.status).toBe(1);
  expect(r.stderr).toContain("local gateway");
});
