import { request, type APIRequestContext, type Page } from "@playwright/test";
import { randomBytes } from "node:crypto";
import { execFileSync } from "node:child_process";
import path from "node:path";
import type { Role, Authentication } from "../../types/contracts";
export const gateway =
  process.env.ORDERFLOW_GATEWAY_URL ?? "http://localhost:8080";
export const composeRoot = path.resolve(import.meta.dirname, "../../..");
export interface FixtureUser {
  email: string;
  password: string;
  displayName: string;
  userId: string;
  api: APIRequestContext;
}
export async function roleUser(role: Role, run: string): Promise<FixtureUser> {
  if (
    process.env.ORDERFLOW_REAL_E2E !== "true" ||
    !["localhost", "127.0.0.1"].includes(new URL(gateway).hostname)
  )
    throw new Error(
      "Real E2E fixture creation requires explicit local-only opt-in.",
    );
  const api = await request.newContext({ baseURL: gateway, timeout: 20000 });
  const email = `phase9-${role.toLowerCase()}-${run}@example.test`,
    password = randomBytes(20).toString("hex"),
    displayName = `Phase 9 ${role}`;
  const csrf = (await (await api.get("/api/auth/csrf")).json()) as {
    token: string;
  };
  const registered = await api.post("/api/auth/register", {
    headers: { "X-XSRF-TOKEN": csrf.token },
    data: { email, password, displayName },
  });
  if (registered.status() !== 201)
    throw new Error(`Fixture registration failed: HTTP ${registered.status()}`);
  const body = (await registered.json()) as Authentication;
  const userId = body.user.id;
  if (!/^[0-9a-f-]{36}$/i.test(userId))
    throw new Error("Invalid fixture identity");
  if (role !== "CUSTOMER")
    execFileSync(
      "docker",
      [
        "compose",
        "exec",
        "-T",
        "postgres",
        "sh",
        "-c",
        'psql -U "$POSTGRES_USER" -d "$1" -v ON_ERROR_STOP=1 -c "$2"',
        "sh",
        process.env.AUTH_POSTGRES_DB ?? "orderflow_auth",
        `INSERT INTO user_roles(user_id,role) VALUES('${userId}'::uuid,'${role}') ON CONFLICT DO NOTHING`,
      ],
      { cwd: composeRoot, stdio: "pipe" },
    );
  const csrf2 = (await (await api.get("/api/auth/csrf")).json()) as {
    token: string;
  };
  const logged = await api.post("/api/auth/login", {
    headers: { "X-XSRF-TOKEN": csrf2.token },
    data: { email, password },
  });
  if (logged.status() !== 200)
    throw new Error(`Fixture login failed: HTTP ${logged.status()}`);
  const current = (await logged.json()) as Authentication;
  const csrf3 = (await (await api.get("/api/auth/csrf")).json()) as {
    token: string;
  };
  const authenticated = await request.newContext({
    baseURL: gateway,
    timeout: 20000,
    extraHTTPHeaders: {
      Authorization: `Bearer ${current.accessToken}`,
      "X-XSRF-TOKEN": csrf3.token,
    },
  });
  await api.dispose();
  return { email, password, displayName, userId, api: authenticated };
}
export async function login(page: Page, user: FixtureUser) {
  await page.goto("/login");
  await page.getByLabel("Email address").fill(user.email);
  await page.getByLabel("Password", { exact: true }).fill(user.password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
}
export function complete(
  kind: "payment.succeeded" | "refund.succeeded",
  id: string,
) {
  execFileSync(
    process.execPath,
    [
      path.join(composeRoot, "scripts/complete-simulator-payment.mjs"),
      kind,
      id,
    ],
    {
      cwd: composeRoot,
      env: { ...process.env, ORDERFLOW_SIMULATOR_HELPER: "true" },
      stdio: "pipe",
      timeout: 20000,
    },
  );
}
