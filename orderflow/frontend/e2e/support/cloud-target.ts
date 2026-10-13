export function cloudTarget(env: Record<string, string | undefined>) {
  if (env.ORDERFLOW_CLOUD_E2E !== "true")
    throw new Error("Deployed E2E requires explicit ORDERFLOW_CLOUD_E2E=true authorization.");
  if (!["cloud-demo", "staging"].includes(env.ORDERFLOW_TARGET_ENVIRONMENT ?? ""))
    throw new Error("Deployed E2E requires an explicit demo/staging environment.");
  if (!/^\d{12}$/.test(env.AWS_ACCOUNT_ID ?? ""))
    throw new Error("Deployed E2E requires the intended AWS account ID.");
  const url = new URL(env.E2E_BASE_URL ?? "");
  if (url.protocol !== "https:" || url.username || url.password || url.search || url.hash || url.pathname !== "/")
    throw new Error("E2E_BASE_URL must be a clean HTTPS origin.");
  const local = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
  const simulation = env.ORDERFLOW_LOCAL_TLS_TEST === "true";
  if (local !== simulation)
    throw new Error("Isolated local TLS opt-in is permitted only for localhost targets.");
  return { origin: url.origin, ignoreHTTPSErrors: simulation, environment: env.ORDERFLOW_TARGET_ENVIRONMENT };
}
