import { test, expect } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { fixtures, productId, orderId } from "./support/fixtures";
const browserErrors = new WeakMap<import("@playwright/test").Page, string[]>();
test.beforeEach(({ page }) => {
  const errors: string[] = [];
  browserErrors.set(page, errors);
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("console", (m) => {
    if (
      m.type() === "error" &&
      /hydration|hydrated|does not match/i.test(m.text())
    )
      errors.push(m.text());
  });
});
test.afterEach(({ page }) => expect(browserErrors.get(page)).toEqual([]));
test("fixture storefront, category filter and actual product navigation", async ({
  page,
}) => {
  await fixtures(page);
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await expect(page.getByText("Everyday desk lamp").first()).toBeVisible();
  await page.goto("/products?category=desk-essentials&sort=price,asc");
  await expect(page.getByText("Everyday desk lamp").first()).toBeVisible();
  await page.goto("/products/" + productId);
  await expect(
    page.getByRole("heading", { name: "Everyday desk lamp" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Sign in to add to cart" }),
  ).toBeVisible();
  mkdirSync("output/playwright", { recursive: true });
  await page.goto("/");
  await page.screenshot({
    path: "output/playwright/storefront-desktop.png",
    fullPage: true,
  });
});
test("fixture login logout and memory-only authentication", async ({
  page,
}) => {
  await fixtures(page);
  await page.goto("/login");
  await page.getByLabel("Email address").fill("reader@example.test");
  await page
    .getByLabel("Password", { exact: true })
    .fill("A-secure-fixture-password");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL(/products/);
  expect(
    await page.evaluate(() => ({
      local: Object.keys(localStorage),
      session: Object.keys(sessionStorage),
    })),
  ).toEqual({ local: [], session: [] });
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(page).toHaveURL(/login/);
});
test("fixture customer cannot enter admin workspace", async ({ page }) => {
  await fixtures(page, "CUSTOMER");
  await page.goto("/admin");
  await expect(
    page.getByText("This area needs a different role"),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Operations overview" }),
  ).not.toBeVisible();
});
test("fixture cart mutations validation and notifications", async ({
  page,
}) => {
  await fixtures(page, "CUSTOMER");
  await page.goto("/products/" + productId);
  await page.getByRole("button", { name: "Add to cart" }).click();
  await expect(
    page.getByText("Added to your cart", { exact: false }).first(),
  ).toBeVisible();
  await page.goto("/cart");
  await expect(page.getByText("Everyday desk lamp").first()).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Validate and checkout" }),
  ).toBeVisible();
  await page.goto("/notifications");
  await expect(
    page.getByRole("heading", { name: "Order received" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Mark all as read" }).click();
  await expect(page.getByText("0 unread", { exact: false })).toBeVisible();
});
test("fixture simulator remains processing without privileged callback", async ({
  page,
}) => {
  await fixtures(page, "CUSTOMER");
  await page.goto("/orders/" + orderId);
  await page.getByLabel("Simulator outcome").selectOption("success");
  await page.getByRole("button", { name: "Confirm simulated payment" }).click();
  await expect(
    page.getByText("Processing", { exact: true }).first(),
  ).toBeVisible();
  await expect(page.getByText("Paid", { exact: true })).toHaveCount(0);
  await page.goto(`/orders/${orderId}/tracking`);
  await expect(
    page.getByRole("heading", { name: "Delivery is not yet requested" }),
  ).toBeVisible();
});
test("fixture vendor product and inventory forms", async ({ page }) => {
  await fixtures(page, "VENDOR");
  await page.goto("/vendor/products");
  await expect(page.getByText("Everyday desk lamp").first()).toBeVisible();
  await page.goto("/vendor/products/new");
  await expect(page.getByLabel("SKU")).toBeVisible();
  await expect(page.getByLabel("Price", { exact: true })).toBeVisible();
  await page.goto("/vendor/inventory/" + productId);
  await expect(page.getByLabel("Quantity change")).toBeVisible();
  await page.getByLabel("Quantity change").fill("5");
  await page.getByLabel("Adjustment reason").fill("Restock");
  await page.getByRole("button", { name: "Adjust stock" }).click();
  await expect(page.getByText("15", { exact: true }).first()).toBeVisible();
});
test("fixture delivery transitions reflect confirmed server states", async ({
  page,
}) => {
  await fixtures(page, "DELIVERY_AGENT");
  await page.goto(
    "/delivery-agent/deliveries/70000000-0000-4000-8000-000000000001",
  );
  await expect(page.getByText("123 Test Street")).toBeVisible();
  await page.getByRole("button", { name: /picked up|pickup|pick up/i }).click();
  await expect(
    page.getByText("Picked up", { exact: true }).first(),
  ).toBeVisible();
});
test("fixture admin views real contract shapes and operational jobs", async ({
  page,
}) => {
  await fixtures(page, "ADMIN");
  await page.goto("/admin");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await page.goto("/admin/categories");
  await expect(page.getByText("Desk essentials").first()).toBeVisible();
  await page.goto("/admin/orders");
  await expect(
    page.locator("tbody").getByText("Payment pending").first(),
  ).toBeVisible();
  await page.goto("/admin/payments");
  await expect(page.getByText("SIMULATOR").first()).toBeVisible();
  await page.goto("/admin/operations/jobs");
  await expect(page.getByText("dailyReport")).toBeVisible();
});
test("fixture mobile layout and keyboard navigation remain usable", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await fixtures(page);
  await page.goto("/products");
  await expect(page.getByText("Everyday desk lamp").first()).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.keyboard.press("Tab");
  await expect(
    page.getByRole("link", { name: "Skip to content" }),
  ).toBeFocused();
  await page.keyboard.press("Tab");
  mkdirSync("output/playwright", { recursive: true });
  await page.screenshot({
    path: "output/playwright/storefront-mobile.png",
    fullPage: true,
  });
});
test("fixture gateway failure has visible retry guidance", async ({ page }) => {
  await fixtures(page);
  await page.route("http://localhost:8080/api/products**", (route) =>
    route.request().method() === "OPTIONS"
      ? route.fulfill({
          status: 204,
          headers: {
            "Access-Control-Allow-Origin": "http://localhost:3000",
            "Access-Control-Allow-Credentials": "true",
            "Access-Control-Allow-Headers":
              "Authorization,Content-Type,X-XSRF-TOKEN,X-Correlation-ID,Idempotency-Key",
            "Access-Control-Allow-Methods": "GET,OPTIONS",
          },
        })
      : route.fulfill({
          status: 503,
          headers: {
            "Access-Control-Allow-Origin": "http://localhost:3000",
            "Access-Control-Allow-Credentials": "true",
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            code: "SERVICE_UNAVAILABLE",
            message: "Catalog temporarily unavailable",
            correlationId: "browser-fixture-trace",
          }),
        }),
  );
  await page.goto("/products");
  await expect(page.getByText("Catalog temporarily unavailable")).toBeVisible();
  await expect(page.getByRole("button", { name: "Try again" })).toBeVisible();
});
