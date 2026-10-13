import { request } from "@playwright/test";
import { readFileSync, lstatSync } from "node:fs";
import { cloudTarget } from "./cloud-target";
import { login, type FixtureUser } from "./real-fixtures";
import type { Role, Authentication } from "../../types/contracts";
import { safeApi } from "./safe-api";
export { login };
const target = cloudTarget(process.env);
export const gateway = target.origin;
export async function roleUser(role: Role): Promise<FixtureUser> {
  const file = process.env.ORDERFLOW_CLOUD_ACCOUNTS_FILE;
  if (!file || lstatSync(file).isSymbolicLink() || (lstatSync(file).mode & 0o077) !== 0)
    throw new Error("A private operator-provided account file (0600) is required.");
  const values = JSON.parse(readFileSync(file, "utf8")) as Record<string, { email: string; password: string }>;
  const account = values[role];
  if (!account || !account.email.endsWith("@example.test") || !account.password)
    throw new Error("Operator test accounts must use fake example.test addresses and protected passwords.");
  const api = safeApi(await request.newContext({ baseURL: gateway, timeout: 20000, ignoreHTTPSErrors: target.ignoreHTTPSErrors }));
  const csrf = await api.get("/api/auth/csrf");
  if (!csrf.ok()) throw new Error(`Cloud CSRF failed: HTTP ${csrf.status()}`);
  const result = await api.post("/api/auth/login", {
    headers: { "X-XSRF-TOKEN": ((await csrf.json()) as { token: string }).token }, data: account,
  });
  if (!result.ok()) throw new Error(`Cloud role login failed: HTTP ${result.status()}`);
  const auth = (await result.json()) as Authentication;
  if (!auth.user.roles.includes(role)) throw new Error("Operator account does not have its intended role.");
  const token = ((await (await api.get("/api/auth/csrf")).json()) as { token: string }).token;
  const authenticated = safeApi(await request.newContext({ baseURL: gateway, timeout: 20000,
    ignoreHTTPSErrors: target.ignoreHTTPSErrors, storageState: await api.storageState(),
    extraHTTPHeaders: { Authorization: `Bearer ${auth.accessToken}`, "X-XSRF-TOKEN": token },
  }));
  await api.dispose();
  return { ...account, displayName: auth.user.displayName, userId: auth.user.id, api: authenticated };
}
