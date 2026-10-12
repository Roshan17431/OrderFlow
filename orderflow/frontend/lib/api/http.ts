import { parse } from "lossless-json";
import { session } from "@/lib/auth/session";
import type {
  Authentication,
  ApiErrorBody,
  Page,
  QueryParams,
} from "@/types/contracts";
export const API_BASE = (
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"
).replace(/\/$/, "");
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public correlationId?: string,
    public fieldErrors: { field: string; message: string }[] = [],
    public retryAfter?: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}
export function normalizePage<T>(data: unknown): Page<T> {
  if (!data || typeof data !== "object")
    throw new Error("Invalid page response");
  const v = data as Record<string, unknown>;
  const items = v.items ?? v.content;
  if (
    !Array.isArray(items) ||
    typeof v.totalElements !== "number" ||
    typeof v.totalPages !== "number"
  )
    throw new Error("Invalid page response");
  return {
    items: items as T[],
    page: Number(v.page),
    size: Number(v.size),
    totalElements: v.totalElements,
    totalPages: v.totalPages,
  };
}
export function query(params: QueryParams = {}) {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v != null && v !== "") p.set(k, String(v));
  });
  return p.size ? "?" + p.toString() : "";
}
export function parseJson(text: string): unknown {
  return parse(text, undefined, (number: string) =>
    number.includes(".") || number.replace("-", "").length > 15
      ? number
      : Number(number),
  );
}
export interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
  signal?: AbortSignal;
  idempotencyKey?: string;
  timeout?: number;
}
const blockedUntil = new Map<string, number>();
let csrfFlight: Promise<string> | null = null;
let refreshFlight: Promise<Authentication> | null = null;
// Serialize every refresh-cookie write across tabs; retain ordering within a tab
// on browsers that do not offer Web Locks. Hold the lock through response parsing.
let authQueue: Promise<void> = Promise.resolve();
const cookieWrites = new Set([
  "/api/auth/login",
  "/api/auth/register",
  "/api/auth/oauth2/exchange",
  "/api/auth/refresh",
  "/api/auth/logout",
  "/api/auth/logout-all",
]);
function withAuthLock<T>(action: () => Promise<T>): Promise<T> {
  const run = async (): Promise<T> => {
    if (typeof navigator !== "undefined" && navigator.locks)
      return await navigator.locks.request("orderflow-auth-cookies", action);
    return await action();
  };
  const result = authQueue.then(run, run);
  authQueue = result.then(
    () => {},
    () => {},
  );
  return result;
}
function attempt<T>(path: string, options: RequestOptions) {
  return options.method === "POST" && cookieWrites.has(path)
    ? withAuthLock(() => raw<T>(path, options))
    : raw<T>(path, options);
}
function csrfCookie() {
  if (typeof document === "undefined") return null;
  return document.cookie
    .split(";")
    .map((x) => x.trim())
    .find((x) => x.startsWith("XSRF-TOKEN="))
    ?.slice(11);
}
export async function csrfToken() {
  const cookie = csrfCookie();
  if (cookie) return decodeURIComponent(cookie);
  if (!csrfFlight)
    csrfFlight = raw<{ token: string }>("/api/auth/csrf", { auth: false })
      .then((x) => x.token)
      .finally(() => {
        csrfFlight = null;
      });
  return csrfFlight;
}
async function raw<T>(path: string, options: RequestOptions = {}): Promise<T> {
  if (!path.startsWith("/api/") || path.includes("://") || path.includes("\\"))
    throw new Error("Invalid gateway path");
  const now = Date.now();
  for (const [key, end] of blockedUntil)
    if (end <= now) blockedUntil.delete(key);
  const blocked = blockedUntil.get(path);
  if (blocked && blocked > now)
    throw new ApiError(
      429,
      "RATE_LIMITED",
      "Please wait before trying this request again.",
      undefined,
      [],
      Math.ceil((blocked - now) / 1000),
    );
  const method = options.method ?? "GET";
  const controller = new AbortController();
  const timeout = setTimeout(
    () => controller.abort(),
    options.timeout ?? 20000,
  );
  const abort = () => controller.abort(options.signal?.reason);
  options.signal?.addEventListener("abort", abort, { once: true });
  if (options.signal?.aborted) abort();
  try {
    const headers = new Headers({
      Accept: "application/json",
      "X-Correlation-ID": crypto.randomUUID(),
    });
    const token = session.get().token;
    if (options.auth !== false && token)
      headers.set("Authorization", `Bearer ${token}`);
    if (options.body !== undefined)
      headers.set("Content-Type", "application/json");
    if (options.idempotencyKey)
      headers.set("Idempotency-Key", options.idempotencyKey);
    if (!["GET", "HEAD", "OPTIONS"].includes(method))
      headers.set("X-XSRF-TOKEN", await csrfToken());
    const response = await fetch(API_BASE + path, {
      method,
      headers,
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: "include",
      cache: "no-store",
      signal: controller.signal,
    });
    const text = await response.text();
    let data: unknown;
    try {
      data = text ? parseJson(text) : undefined;
    } catch {
      throw new ApiError(
        response.ok ? 502 : response.status,
        "INVALID_RESPONSE",
        "The service returned an unreadable response. Its result may be uncertain; retry the same attempt.",
        response.headers.get("X-Correlation-ID") ?? undefined,
      );
    }
    if (response.ok && response.status !== 204 && data == null)
      throw new ApiError(
        502,
        "INVALID_RESPONSE",
        "The service returned an empty response. Its result may be uncertain; retry the same attempt.",
        response.headers.get("X-Correlation-ID") ?? undefined,
      );
    if (!response.ok) {
      const e = (data ?? {}) as Partial<ApiErrorBody>;
      const retry = response.headers.get("Retry-After");
      const seconds = retry
        ? Number.isFinite(Number(retry))
          ? Number(retry)
          : Math.max(0, Math.ceil((Date.parse(retry) - Date.now()) / 1000))
        : undefined;
      if (response.status === 429 && seconds != null && seconds > 0)
        blockedUntil.set(path, Date.now() + seconds * 1000);
      throw new ApiError(
        response.status,
        e.code ?? "REQUEST_FAILED",
        e.message ?? "The request could not be completed.",
        e.correlationId ??
          response.headers.get("X-Correlation-ID") ??
          undefined,
        e.fieldErrors ?? [],
        seconds,
      );
    }
    return data as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (options.signal?.aborted) throw error;
    throw new ApiError(
      503,
      controller.signal.aborted ? "REQUEST_TIMEOUT" : "GATEWAY_UNAVAILABLE",
      controller.signal.aborted
        ? "The request timed out. Its result may be uncertain; retry the same attempt."
        : "Cannot reach OrderFlow. Please retry when the service is available.",
    );
  } finally {
    clearTimeout(timeout);
    options.signal?.removeEventListener("abort", abort);
  }
}
export async function refreshSession() {
  if (!refreshFlight) {
    const epoch = session.get().epoch;
    refreshFlight = withAuthLock(async () => {
      const value = await raw<Authentication>("/api/auth/refresh", {
        method: "POST",
        auth: false,
      });
      if (!session.set(value, epoch))
        throw new ApiError(
          401,
          "SESSION_CHANGED",
          "Your session has changed. Please sign in again.",
        );
      return value;
    })
      .catch((error) => {
        if (session.get().epoch === epoch) session.clear();
        throw error;
      })
      .finally(() => {
        refreshFlight = null;
      });
  }
  return refreshFlight;
}
export async function request<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const epoch = session.get().epoch;
  const token = session.get().token;
  const isLogout =
    path === "/api/auth/logout" || path === "/api/auth/logout-all";
  try {
    const value = await attempt<T>(path, options);
    if (options.auth !== false && !isLogout && epoch !== session.get().epoch)
      throw new ApiError(401, "SESSION_CHANGED", "Your session has changed.");
    return value;
  } catch (error) {
    if (
      !(error instanceof ApiError) ||
      error.status !== 401 ||
      options.auth === false ||
      epoch !== session.get().epoch
    )
      throw error;
    const current = session.get().token;
    if (!current || current === token) await refreshSession();
    if (epoch !== session.get().epoch)
      throw new ApiError(
        401,
        "SESSION_CHANGED",
        "Your account changed. Please review this action before submitting again.",
      );
    try {
      const value = await attempt<T>(path, options);
      if (!isLogout && epoch !== session.get().epoch)
        throw new ApiError(401, "SESSION_CHANGED", "Your session has changed.");
      return value;
    } catch (retryError) {
      if (
        retryError instanceof ApiError &&
        retryError.status === 401 &&
        epoch === session.get().epoch
      )
        session.clear();
      throw retryError;
    }
  }
}
export const getPage = async <T>(path: string, options?: RequestOptions) =>
  normalizePage<T>(await request(path, options));
