import type { APIRequestContext } from "@playwright/test";

const requestMethods = new Set(["get", "post", "put", "patch", "delete", "head", "fetch"]);

// Playwright transport exceptions include raw Authorization/cookie call logs.
// Keep only a safe method name in test reports and never follow API redirects.
export function safeApi(api: APIRequestContext): APIRequestContext {
  return new Proxy(api, {
    get(target, property) {
      const value = Reflect.get(target, property, target);
      if (typeof value !== "function") return value;
      if (!requestMethods.has(String(property))) return value.bind(target);
      return async (url: unknown, options: Record<string, unknown> = {}) => {
        try {
          return await value.call(target, url, { ...options, maxRedirects: 0 });
        } catch {
          throw new Error(`Verification API ${String(property).toUpperCase()} request failed; private transport details omitted.`);
        }
      };
    },
  });
}
