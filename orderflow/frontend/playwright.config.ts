import { defineConfig, devices } from "@playwright/test";
// Playwright's failure DOM snapshots include password input values. Real test
// credentials stay in memory; keep only masked screenshots and safe step logs.
if (process.env.ORDERFLOW_REAL_E2E === "true" || process.env.ORDERFLOW_CLOUD_E2E === "true")
  process.env.PLAYWRIGHT_NO_COPY_PROMPT = "1";
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90000,
  expect: { timeout: 30000 },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    actionTimeout: 20000,
    navigationTimeout: 30000,
    baseURL: process.env.E2E_BASE_URL ?? process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "fixtures",
      testMatch: "fixtures.spec.ts",
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "real-backend",
      testMatch: "real-backend.spec.ts",
      use: { ...devices["Desktop Chrome"], trace: "off", video: "off" },
    },
    {
      name: "cloud-backend",
      testMatch: "cloud-backend.spec.ts",
      use: { ...devices["Desktop Chrome"], trace: "off", video: "off" },
    },
  ],
  webServer: process.env.PLAYWRIGHT_EXTERNAL_SERVER || process.env.E2E_BASE_URL
    ? undefined
    : {
        command: "npm run dev",
        url: "http://localhost:3000",
        reuseExistingServer: !process.env.CI,
        timeout: 120000,
      },
});
