import { test, expect, request } from "@playwright/test";
import { randomBytes } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { roleUser, login, complete, gateway } from "./support/real-fixtures";
import type {
  Authentication,
  Order,
  Payment,
  Refund,
  Stock,
  Agent,
} from "../types/contracts";
test("real gateway: all four roles complete commerce delivery and refund journey", async ({
  browser,
}) => {
  test.setTimeout(360000);
  const run = Date.now().toString(36);
  const [vendor, agent, admin] = await (async () => {
    const v = await roleUser("VENDOR", run);
    const a = await roleUser("DELIVERY_AGENT", run);
    const n = await roleUser("ADMIN", run);
    return [v, a, n] as const;
  })();
  const contexts = await Promise.all(
    Array.from({ length: 4 }, () => browser.newContext()),
  );
  const [customerPage, vendorPage, agentPage, adminPage] = await Promise.all(
    contexts.map((c) => c.newPage()),
  );
  const errors: string[] = [];
  for (const p of [customerPage, vendorPage, agentPage, adminPage]) {
    p.on("pageerror", (e) => errors.push(e.message));
    p.on("console", (m) => {
      if (
        m.type() === "error" &&
        /hydration|hydrated|does not match/i.test(m.text())
      )
        errors.push(m.text());
    });
  }
  let categoryId = "",
    productId = "",
    orderId = "",
    paymentId = "";
  try {
    await test.step("Admin creates an isolated category", async () => {
      console.info("Real E2E: Admin creates an isolated category");
      await login(adminPage, admin);
      await expect(adminPage).toHaveURL(/\/admin$/);
      await adminPage.goto("/admin/categories");
      await adminPage
        .getByRole("button", { name: "Create category", exact: true })
        .click();
      await adminPage
        .getByLabel("Category name")
        .fill(`Phase 9 collection ${run}`);
      await adminPage.getByLabel("Category slug").fill(`phase9-${run}`);
      await adminPage
        .getByLabel("Description", { exact: true })
        .fill("Isolated browser verification collection.");
      await adminPage
        .getByRole("button", { name: "Create category", exact: true })
        .last()
        .click();
      await expect(
        adminPage
          .getByRole("row")
          .filter({ hasText: `Phase 9 collection ${run}` }),
      ).toBeVisible();
      const response = await admin.api.get("/api/admin/categories?size=100");
      const data = (await response.json()) as {
        items: { id: string; slug: string }[];
      };
      categoryId = data.items.find((x) => x.slug === `phase9-${run}`)!.id;
    });
    await test.step("Vendor creates edits activates and stocks a real product", async () => {
      console.info(
        "Real E2E: Vendor creates edits activates and stocks a real product",
      );
      await login(vendorPage, vendor);
      await expect(vendorPage).toHaveURL(/\/vendor$/);
      await vendorPage.goto("/vendor/products/new");
      await vendorPage
        .getByLabel("Product name")
        .fill(`Phase 9 desk lamp ${run}`);
      await vendorPage
        .getByLabel("Description", { exact: true })
        .fill("A product created through the actual vendor frontend.");
      await vendorPage.getByLabel("SKU").fill(`P9-${run}`);
      await vendorPage.getByLabel("Slug").fill(`phase9-lamp-${run}`);
      await vendorPage
        .getByLabel("Category", { exact: true })
        .selectOption(categoryId);
      await vendorPage.getByLabel("Price", { exact: true }).fill("25.75");
      await vendorPage
        .getByRole("button", { name: "Create product", exact: true })
        .click();
      await expect(vendorPage).toHaveURL(
        /\/vendor\/products\/[0-9a-f-]+\/edit$/,
      );
      const response = await vendor.api.get(
        `/api/vendor/products?size=100&vendorId=${vendor.userId}`,
      );
      const data = (await response.json()) as {
        items: { id: string; sku: string }[];
      };
      productId = new URL(vendorPage.url()).pathname.split("/")[3];
      expect(data.items.some((x) => x.id === productId)).toBe(true);
      await vendorPage.goto(`/vendor/products/${productId}/edit`);
      await vendorPage
        .getByLabel("Description", { exact: true })
        .fill("Edited through the actual vendor frontend.");
      await vendorPage
        .getByRole("button", { name: "Save product", exact: true })
        .click();
      await expect(
        vendorPage.getByText("Product saved.", { exact: true }),
      ).toBeVisible();
      await vendorPage.goto("/vendor/products");
      const row = vendorPage
        .locator("tr")
        .filter({ hasText: `Phase 9 desk lamp ${run}` });
      await row
        .getByRole("button", {
          name: `Activate Phase 9 desk lamp ${run}`,
          exact: true,
        })
        .click();
      await vendorPage
        .getByRole("dialog")
        .getByRole("button", { name: "Activate product", exact: true })
        .click();
      await expect(row.getByText("Active", { exact: true })).toBeVisible();
      await vendorPage.goto(`/vendor/inventory/${productId}`);
      await vendorPage.getByLabel("Quantity change").fill("10");
      await vendorPage
        .getByLabel("Adjustment reason")
        .fill("Phase 9 isolated browser stock");
      await vendorPage
        .getByRole("button", { name: "Adjust stock", exact: true })
        .click();
      await expect(
        vendorPage.getByText("Stock adjusted.").first(),
      ).toBeVisible();
    });
    await test.step("Agent registers profile and remains offline until assigned work is ready", async () => {
      console.info(
        "Real E2E: Agent registers profile and remains offline until assigned work is ready",
      );
      await login(agentPage, agent);
      await expect(agentPage).toHaveURL(/\/delivery-agent$/);
      await agentPage
        .getByRole("button", { name: /available|register/i })
        .first()
        .click();
      await expect(
        agentPage.getByRole("textbox", {
          name: "Agent profile ID",
          exact: true,
        }),
      ).not.toHaveValue("");
      await agentPage.getByRole("button", { name: /go offline/i }).click();
    });
    const customerEmail = `phase9-customer-${run}@example.test`,
      password = randomBytes(20).toString("hex");
    let customerAuth: Authentication;
    await test.step("Customer registers, restores HttpOnly-cookie session, browses and mutates cart", async () => {
      console.info(
        "Real E2E: Customer registers, restores HttpOnly-cookie session, browses and mutates cart",
      );
      await customerPage.goto("/register");
      await customerPage.getByLabel("Display name").fill("Phase 9 customer");
      await customerPage.getByLabel("Email address").fill(customerEmail);
      await customerPage.getByLabel("Password", { exact: true }).fill(password);
      const registered = customerPage.waitForResponse(
        (r) =>
          r.url().endsWith("/api/auth/register") &&
          r.request().method() === "POST",
      );
      await customerPage
        .getByRole("button", { name: "Create account", exact: true })
        .click();
      customerAuth = (await (await registered).json()) as Authentication;
      await expect(customerPage).toHaveURL(/products/);
      const restored = customerPage.waitForResponse(
        (r) =>
          r.url().endsWith("/api/auth/refresh") &&
          r.request().method() === "POST",
      );
      await customerPage.reload();
      customerAuth = (await (await restored).json()) as Authentication;
      await expect(
        customerPage.getByRole("link", { name: "My orders", exact: true }),
      ).toBeVisible();
      const cookies = await contexts[0].cookies(gateway + "/api/auth/refresh");
      expect(
        cookies.find((c) => c.name === "ORDERFLOW_REFRESH")?.httpOnly,
      ).toBe(true);
      expect(
        await customerPage.evaluate(() => ({
          local: Object.keys(localStorage),
          session: Object.keys(sessionStorage),
        })),
      ).toEqual({ local: [], session: [] });
      await customerPage.goto(`/products/${productId}`);
      await expect(
        customerPage.getByRole("heading", { name: `Phase 9 desk lamp ${run}` }),
      ).toBeVisible();
      await customerPage
        .getByRole("button", { name: "Add to cart", exact: true })
        .click();
      await expect(
        customerPage.getByText("Added to your cart", { exact: false }).first(),
      ).toBeVisible();
      await customerPage.goto("/cart");
      await expect(
        customerPage
          .getByText(`Phase 9 desk lamp ${run}`, { exact: true })
          .first(),
      ).toBeVisible();
      await customerPage.getByLabel(/quantity/i).fill("2");
      const updatedCart = customerPage.waitForResponse(
        (r) =>
          r.url().includes("/api/cart/items/") &&
          r.request().method() === "PATCH",
      );
      await customerPage
        .getByRole("button", { name: /update quantity|update/i })
        .click();
      expect((await updatedCart).status()).toBe(200);
      await customerPage
        .getByRole("button", { name: "Validate and checkout", exact: true })
        .click();
      await expect(customerPage).toHaveURL(/checkout/);
    });
    await test.step("Customer creates an asynchronous idempotent order and confirms simulator", async () => {
      console.info(
        "Real E2E: Customer creates an asynchronous idempotent order and confirms simulator",
      );
      const fields = {
        "Recipient name": "Phase 9 customer",
        "Address line 1": "123 Browser Test Street",
        City: "Austin",
        "State or region": "Texas",
        "Postal code": "78701",
        "Country code": "US",
        "Delivery instructions": "Side entrance",
      };
      for (const [label, value] of Object.entries(fields))
        await customerPage.getByLabel(label, { exact: true }).fill(value);
      await customerPage
        .getByRole("button", { name: "Place order", exact: true })
        .click();
      // A cold local service can accept after the client's deadline. Exercise
      // the actual safe recovery control, retaining the original body/key.
      await expect
        .poll(
          async () =>
            /\/orders\/[0-9a-f-]+$/.test(
              new URL(customerPage.url()).pathname,
            ) ||
            (await customerPage
              .getByRole("button", { name: "Retry same order", exact: true })
              .isVisible()),
          { timeout: 45000 },
        )
        .toBe(true);
      const retry = customerPage.getByRole("button", {
        name: "Retry same order",
        exact: true,
      });
      if (await retry.isVisible()) await retry.click();
      await expect(customerPage).toHaveURL(/\/orders\/[0-9a-f-]+$/);
      orderId = new URL(customerPage.url()).pathname.split("/").pop()!;
      await expect(customerPage.getByLabel("Simulator outcome")).toBeVisible({
        timeout: 60000,
      });
      await customerPage
        .getByLabel("Simulator outcome")
        .selectOption("success");
      await customerPage
        .getByRole("button", { name: "Confirm simulated payment", exact: true })
        .click();
      await expect(
        customerPage.getByText("Processing", { exact: true }).first(),
      ).toBeVisible();
      const ctx = await request.newContext({
        baseURL: gateway,
        extraHTTPHeaders: {
          Authorization: `Bearer ${customerAuth.accessToken}`,
        },
      });
      const payment = (await (
        await ctx.get(`/api/payments/orders/${orderId}`)
      ).json()) as Payment;
      paymentId = payment.id;
      await ctx.dispose();
      complete("payment.succeeded", paymentId);
      await expect(
        customerPage.getByText("Paid", { exact: true }).first(),
      ).toBeVisible({ timeout: 60000 });
    });
    await test.step("Agent availability triggers assignment and browser completes actual delivery", async () => {
      console.info(
        "Real E2E: Agent availability triggers assignment and browser completes actual delivery",
      );
      await agentPage.goto("/delivery-agent");
      await agentPage
        .getByRole("button", { name: /become available/i })
        .click();
      await expect
        .poll(
          async () => {
            const r = await agent.api.get("/api/delivery/agents/me");
            return ((await r.json()) as Agent).activeDeliveryId;
          },
          { timeout: 60000 },
        )
        .not.toBeNull();
      const profile = (await (
        await agent.api.get("/api/delivery/agents/me")
      ).json()) as Agent;
      const deliveryId = profile.activeDeliveryId!;
      await agentPage.goto(`/delivery-agent/deliveries/${deliveryId}`);
      await expect(
        agentPage.getByText("123 Browser Test Street"),
      ).toBeVisible();
      for (const action of [
        /mark picked up|pick up|pickup/i,
        /out for delivery/i,
        /mark delivered|confirm delivered|delivered/i,
      ]) {
        await agentPage.getByRole("button", { name: action }).first().click();
      }
      await expect
        .poll(
          async () => {
            const r = await agent.api.get(
              `/api/delivery/agents/me/deliveries/${deliveryId}`,
            );
            return ((await r.json()) as { status: string }).status;
          },
          { timeout: 60000 },
        )
        .toBe("DELIVERED");
      await agentPage
        .getByRole("button", { name: "Refresh", exact: true })
        .click();
      await expect(
        agentPage.getByText("Delivered", { exact: true }).first(),
      ).toBeVisible();
      await expect
        .poll(
          async () => {
            const r = await vendor.api.get(
              `/api/inventory/products/${productId}`,
            );
            return ((await r.json()) as Stock).onHandQuantity;
          },
          { timeout: 60000 },
        )
        .toBe(8);
      await vendorPage
        .getByRole("button", { name: "Refresh", exact: true })
        .click();
      await expect(
        vendorPage.getByText("8", { exact: true }).first(),
      ).toBeVisible();
      await customerPage.goto(`/orders/${orderId}/tracking`);
      await expect(
        customerPage.getByText("Delivered", { exact: true }).first(),
      ).toBeVisible({ timeout: 60000 });
    });
    await test.step("Notifications, admin refund and operations reflect confirmed backend state", async () => {
      console.info(
        "Real E2E: Notifications, admin refund and operations reflect confirmed backend state",
      );
      const stream = customerPage.waitForResponse(
        (r) =>
          r.url().endsWith("/api/notifications/stream") && r.status() === 200,
      );
      await customerPage.goto("/notifications");
      const streamResponse = await stream;
      expect(streamResponse.headers()["content-type"]).toContain(
        "text/event-stream",
      );
      const streamHeaders = streamResponse.request().headers();
      expect(Boolean(streamHeaders.authorization?.startsWith("Bearer "))).toBe(
        true,
      );
      expect(new URL(streamResponse.url()).search).toBe("");
      await expect(
        customerPage.locator(".notification-row").first(),
      ).toBeVisible({ timeout: 60000 });
      const notificationsApi = await request.newContext({
        baseURL: gateway,
        extraHTTPHeaders: {
          Authorization: `Bearer ${customerAuth.accessToken}`,
        },
      });
      const beforeNotifications = (await (
        await notificationsApi.get("/api/notifications?size=100")
      ).json()) as { content: { id: string; read: boolean }[] };
      const marked = customerPage.waitForResponse(
        (r) =>
          r.url().endsWith("/api/notifications/read-all") &&
          r.request().method() === "PATCH",
      );
      await customerPage
        .getByRole("button", { name: "Mark all as read", exact: true })
        .click();
      expect((await marked).status()).toBe(200);
      const afterNotifications = (await (
        await notificationsApi.get("/api/notifications?size=100")
      ).json()) as { content: { id: string; read: boolean }[] };
      expect(beforeNotifications.content.length).toBeGreaterThan(0);
      expect(
        beforeNotifications.content.every(
          (n) =>
            afterNotifications.content.find((current) => current.id === n.id)
              ?.read,
        ),
      ).toBe(true);
      await expect(
        customerPage
          .locator(".notification-row")
          .getByText("Read", { exact: true })
          .first(),
      ).toBeVisible();
      await notificationsApi.dispose();
      await customerPage.goto("/settings/notifications");
      await customerPage
        .getByLabel("Enable simulated email", { exact: true })
        .check();
      await customerPage
        .getByLabel("Email destination", { exact: true })
        .fill(customerEmail);
      const preferencesSaved = customerPage.waitForResponse(
        (r) =>
          r.url().endsWith("/api/notifications/preferences") &&
          r.request().method() === "PUT",
      );
      await customerPage
        .getByRole("button", { name: "Save preferences", exact: true })
        .click();
      expect((await preferencesSaved).status()).toBe(200);
      await expect(
        customerPage.getByText("Notification preferences saved.", {
          exact: true,
        }),
      ).toBeVisible();
      await adminPage.goto(`/admin/payments/${paymentId}`);
      await adminPage.getByLabel("Refund amount").fill("2.00");
      await adminPage
        .getByLabel("Refund reason")
        .selectOption("CUSTOMER_REQUEST");
      await adminPage.getByRole("button", { name: /request refund/i }).click();
      await expect
        .poll(
          async () => {
            const r = await admin.api.get(
              `/api/admin/payments/${paymentId}/refunds`,
            );
            return ((await r.json()) as { content: Refund[] }).content.length;
          },
          { timeout: 30000 },
        )
        .toBe(1);
      const refunds = (await (
        await admin.api.get(`/api/admin/payments/${paymentId}/refunds`)
      ).json()) as { content: Refund[] };
      complete("refund.succeeded", refunds.content[0].id);
      await expect
        .poll(
          async () => {
            const r = await admin.api.get(`/api/orders/${orderId}`);
            return ((await r.json()) as Order).status;
          },
          { timeout: 60000 },
        )
        .toBe("PARTIALLY_REFUNDED");
      await customerPage.goto(`/orders/${orderId}`);
      await expect(
        customerPage.getByText("Partially refunded", { exact: true }).first(),
      ).toBeVisible();
      await expect(
        customerPage.getByText("Delivered", { exact: true }).first(),
      ).toBeVisible();
      await adminPage.goto("/admin/operations");
      await expect(adminPage.getByRole("heading", { level: 1 })).toBeVisible();
      expect((await vendor.api.get(`/api/orders/${orderId}`)).status()).toBe(
        404,
      );
      expect((await agent.api.get("/api/admin/orders")).status()).toBe(403);
    });
    await test.step("Customer login/logout clears private session state", async () => {
      console.info(
        "Real E2E: Customer login/logout clears private session state",
      );
      await customerPage
        .getByRole("button", { name: "Sign out", exact: true })
        .click();
      await expect(customerPage).toHaveURL(/login/);
      await customerPage.getByLabel("Email address").fill(customerEmail);
      await customerPage
        .getByLabel("Password", { exact: true })
        .fill(password + "invalid");
      await customerPage
        .getByRole("button", { name: "Sign in", exact: true })
        .click();
      await expect(customerPage.getByRole("alert")).toBeVisible();
      await expect(customerPage).toHaveURL(/login/);
      await customerPage.getByLabel("Password", { exact: true }).fill(password);
      await customerPage
        .getByRole("button", { name: "Sign in", exact: true })
        .click();
      await expect(customerPage).toHaveURL(/products/);
      await customerPage
        .getByRole("button", { name: "Sign out", exact: true })
        .click();
      await expect(customerPage).toHaveURL(/login/);
      expect(
        await customerPage.evaluate(() => ({
          local: Object.keys(localStorage),
          session: Object.keys(sessionStorage),
        })),
      ).toEqual({ local: [], session: [] });
    });
    expect(errors).toEqual([]);
    const recordedOrder = (await (
      await admin.api.get(`/api/orders/${orderId}`)
    ).json()) as Order;
    const recordedStock = (await (
      await vendor.api.get(`/api/inventory/products/${productId}`)
    ).json()) as Stock;
    mkdirSync("output/verification", { recursive: true });
    writeFileSync(
      "output/verification/real-journey.json",
      JSON.stringify(
        {
          run,
          categoryId,
          productId,
          orderId,
          paymentId,
          financialStatus: recordedOrder.status,
          fulfillmentStatus: recordedOrder.fulfillmentStatus,
          refundedAmount: recordedOrder.refundedAmount,
          onHandQuantity: recordedStock.onHandQuantity,
          unhandledBrowserErrors: errors,
        },
        null,
        2,
      ),
    );
    await test.step("Live role workspaces fit mobile viewports", async () => {
      mkdirSync("output/playwright", { recursive: true });
      for (const [name, p] of [
        ["vendor", vendorPage],
        ["agent", agentPage],
        ["admin", adminPage],
      ] as const) {
        await p.evaluate(() => {
          if (document.activeElement instanceof HTMLElement)
            document.activeElement.blur();
        });
        // Allow the chart geometry animation to settle before visual capture.
        if (name === "admin") await p.waitForTimeout(1700);
        await p.screenshot({
          path: `output/playwright/real-${name}-desktop.png`,
          fullPage: true,
        });
        await p.setViewportSize({ width: 390, height: 844 });
        await expect
          .poll(() =>
            p.evaluate(
              () => document.documentElement.scrollWidth <= window.innerWidth,
            ),
          )
          .toBe(true);
        await expect
          .poll(
            () =>
              p
                .locator(".recharts-surface")
                .evaluateAll((nodes) =>
                  nodes.every(
                    (node) =>
                      Number(node.getAttribute("width")) <=
                      node.parentElement!.getBoundingClientRect().width,
                  ),
                ),
            { timeout: 5000 },
          )
          .toBe(true);
        if (name === "admin") await p.waitForTimeout(1700);
        await p.screenshot({
          path: `output/playwright/real-${name}-mobile.png`,
          fullPage: true,
        });
      }
    });
    expect(errors).toEqual([]);
  } finally {
    // Return only this freshly created idle fixture agent offline, so failed runs
    // cannot receive a later run's automatically assigned delivery.
    const profileResponse = await agent.api.get("/api/delivery/agents/me");
    if (profileResponse.status() === 200) {
      const profile = (await profileResponse.json()) as Agent;
      if (
        profile.activeDeliveryId == null &&
        profile.availability === "AVAILABLE"
      ) {
        const csrf = (await (await agent.api.get("/api/auth/csrf")).json()) as {
          token: string;
        };
        await agent.api.put("/api/delivery/agents/me/availability", {
          headers: { "X-XSRF-TOKEN": csrf.token },
          data: { availability: "OFFLINE" },
        });
      }
    }
    await Promise.all([
      vendor.api.dispose(),
      agent.api.dispose(),
      admin.api.dispose(),
      ...contexts.map((c) => c.close()),
    ]);
  }
});
