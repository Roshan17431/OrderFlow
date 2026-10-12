import type { Role } from "@/types/contracts";
export function safeReturnUrl(
  value: string | null | undefined,
  fallback = "/products",
) {
  if (
    !value ||
    !value.startsWith("/") ||
    value.startsWith("//") ||
    /[\\\r\n]/.test(value)
  )
    return fallback;
  try {
    const decoded = decodeURIComponent(value);
    if (decoded.startsWith("//") || /[\\\r\n]/.test(decoded)) return fallback;
    const u = new URL(value, "http://orderflow.local");
    return u.origin === "http://orderflow.local" && !u.pathname.startsWith("//")
      ? u.pathname + u.search + u.hash
      : fallback;
  } catch {
    return fallback;
  }
}
export function roleHome(roles: Role[]) {
  return roles.includes("ADMIN")
    ? "/admin"
    : roles.includes("VENDOR")
      ? "/vendor"
      : roles.includes("DELIVERY_AGENT")
        ? "/delivery-agent"
        : "/products";
}
