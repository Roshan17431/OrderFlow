import { request, getPage, query } from "./http";
import type * as T from "@/types/contracts";
const id = (value: string) => encodeURIComponent(value);
export const api = {
  auth: {
    login: (body: { email: string; password: string }) =>
      request<T.Authentication>("/api/auth/login", {
        method: "POST",
        body,
        auth: false,
      }),
    register: (body: {
      email: string;
      password: string;
      displayName: string;
    }) =>
      request<T.Authentication>("/api/auth/register", {
        method: "POST",
        body,
        auth: false,
      }),
    me: () => request<T.User>("/api/auth/me"),
    logout: (all = false) =>
      request<void>(`/api/auth/${all ? "logout-all" : "logout"}`, {
        method: "POST",
      }),
    exchange: (code: string) =>
      request<T.Authentication>("/api/auth/oauth2/exchange", {
        method: "POST",
        body: { code },
        auth: false,
      }),
  },
  products: {
    list: (p: T.QueryParams = {}) =>
      getPage<T.Product>("/api/products" + query(p), { auth: false }),
    get: (value: string) =>
      request<T.Product>("/api/products/" + id(value), { auth: false }),
    categories: async () =>
      (
        await request<{ items: T.Category[] }>("/api/categories", {
          auth: false,
        })
      ).items,
  },
  vendor: {
    list: (p: T.QueryParams = {}) =>
      getPage<T.VendorProduct>("/api/vendor/products" + query(p)),
    create: (body: T.ProductInput) =>
      request<T.VendorProduct>("/api/vendor/products", {
        method: "POST",
        body,
      }),
    update: (value: string, body: T.ProductUpdate) =>
      request<T.VendorProduct>("/api/vendor/products/" + id(value), {
        method: "PUT",
        body,
      }),
    status: (
      value: string,
      action: "activate" | "deactivate" | "archive",
      version: number,
    ) =>
      request<T.VendorProduct>(`/api/vendor/products/${id(value)}/${action}`, {
        method: "PATCH",
        body: { version },
      }),
  },
  cart: {
    get: () => request<T.Cart>("/api/cart"),
    add: (productId: string, quantity: number) =>
      request<T.Cart>("/api/cart/items", {
        method: "POST",
        body: { productId, quantity },
      }),
    update: (value: string, quantity: number) =>
      request<T.Cart>("/api/cart/items/" + id(value), {
        method: "PATCH",
        body: { quantity },
      }),
    remove: (value: string) =>
      request<T.Cart>("/api/cart/items/" + id(value), { method: "DELETE" }),
    clear: () => request<void>("/api/cart", { method: "DELETE" }),
    validate: () =>
      request<T.CartValidation>("/api/cart/validate", { method: "POST" }),
  },
  inventory: {
    get: (value: string) =>
      request<T.Stock>(`/api/inventory/products/${id(value)}`),
    adjust: (value: string, body: T.StockInput) =>
      request<T.Stock>(`/api/inventory/products/${id(value)}/adjustments`, {
        method: "POST",
        body,
      }),
    history: (value: string, p: T.QueryParams = {}) =>
      getPage<T.Adjustment>(
        `/api/inventory/products/${id(value)}/adjustments` + query(p),
      ),
  },
  orders: {
    list: (p: T.QueryParams = {}) => getPage<T.Order>("/api/orders" + query(p)),
    get: (value: string) => request<T.Order>("/api/orders/" + id(value)),
    history: (value: string) =>
      request<T.OrderHistory[]>(`/api/orders/${id(value)}/history`),
    create: (
      body: { expectedCartVersion: number; shippingAddress: T.Address },
      key: string,
    ) =>
      request<T.Order>("/api/orders", {
        method: "POST",
        body,
        idempotencyKey: key,
      }),
    cancel: (value: string, reason: string, key: string) =>
      request<T.Order>(`/api/orders/${id(value)}/cancel`, {
        method: "POST",
        body: { reason },
        idempotencyKey: key,
      }),
  },
  payments: {
    byOrder: (value: string) =>
      request<T.Payment>(`/api/payments/orders/${id(value)}`),
    get: (value: string) => request<T.Payment>(`/api/payments/${id(value)}`),
    confirm: (value: string, paymentMethodToken: string, key: string) =>
      request<T.Payment>(`/api/payments/orders/${id(value)}/confirm`, {
        method: "POST",
        body: { paymentMethodToken },
        idempotencyKey: key,
      }),
  },
  delivery: {
    byOrder: (value: string) =>
      request<T.Delivery>(`/api/deliveries/orders/${id(value)}`),
    get: (value: string) => request<T.Delivery>(`/api/deliveries/${id(value)}`),
    agent: () => request<T.Agent>("/api/delivery/agents/me"),
    availability: (availability: "OFFLINE" | "AVAILABLE") =>
      request<T.Agent>("/api/delivery/agents/me/availability", {
        method: "PUT",
        body: { availability },
      }),
    list: (p: T.QueryParams = {}) =>
      getPage<T.Delivery>("/api/delivery/agents/me/deliveries" + query(p)),
    assigned: (value: string) =>
      request<T.Delivery>(`/api/delivery/agents/me/deliveries/${id(value)}`),
    transition: (
      value: string,
      action: "pickup" | "out-for-delivery" | "delivered" | "failed",
      failureCode?: string,
    ) =>
      request<T.Delivery>(
        `/api/delivery/agents/me/deliveries/${id(value)}/${action}`,
        { method: "POST", body: failureCode ? { failureCode } : undefined },
      ),
  },
  notifications: {
    list: (p: T.QueryParams = {}) =>
      getPage<T.Notification>("/api/notifications" + query(p)),
    unread: () => request<{ count: number }>("/api/notifications/unread-count"),
    read: (value: string) =>
      request<T.Notification>(`/api/notifications/${id(value)}/read`, {
        method: "PATCH",
      }),
    readAll: () =>
      request<{ count: number }>("/api/notifications/read-all", {
        method: "PATCH",
      }),
    preferences: () => request<T.Preferences>("/api/notifications/preferences"),
    savePreferences: (body: T.Preferences) =>
      request<T.Preferences>("/api/notifications/preferences", {
        method: "PUT",
        body,
      }),
  },
  admin: {
    categories: (p: T.QueryParams = {}) =>
      getPage<T.Category>("/api/admin/categories" + query(p)),
    createCategory: (body: {
      name: string;
      slug: string;
      description?: string;
    }) =>
      request<T.Category>("/api/admin/categories", { method: "POST", body }),
    updateCategory: (
      value: string,
      body: {
        name: string;
        slug: string;
        description?: string;
        version: number;
      },
    ) =>
      request<T.Category>(`/api/admin/categories/${id(value)}`, {
        method: "PUT",
        body,
      }),
    categoryStatus: (
      value: string,
      action: "activate" | "deactivate",
      version: number,
    ) =>
      request<T.Category>(`/api/admin/categories/${id(value)}/${action}`, {
        method: "PATCH",
        body: { version },
      }),
    orders: (p: T.QueryParams = {}) =>
      getPage<T.Order>("/api/admin/orders" + query(p)),
    payments: (p: T.QueryParams = {}) =>
      getPage<T.Payment>("/api/admin/payments" + query(p)),
    payment: (value: string) =>
      request<T.PaymentDetail>(`/api/admin/payments/${id(value)}`),
    refunds: (value: string, p: T.QueryParams = {}) =>
      getPage<T.Refund>(`/api/admin/payments/${id(value)}/refunds` + query(p)),
    refund: (
      value: string,
      body: { amount: T.Money; reason: string },
      key: string,
    ) =>
      request<T.Refund>(`/api/admin/payments/${id(value)}/refunds`, {
        method: "POST",
        body,
        idempotencyKey: key,
      }),
    assign: (value: string, deliveryAgentId: string) =>
      request<T.Delivery>(`/api/admin/deliveries/${id(value)}/assign`, {
        method: "POST",
        body: { deliveryAgentId },
      }),
    deliveryHistory: (value: string, p: T.QueryParams = {}) =>
      getPage<T.DeliveryHistory>(
        `/api/admin/deliveries/${id(value)}/history` + query(p),
      ),
  },
  operations: {
    overview: () => request<T.Overview>("/api/admin/operations/overview"),
    reports: (p: T.QueryParams = {}) =>
      getPage<T.DailyReport>("/api/admin/operations/reports/daily" + query(p)),
    incidents: (p: T.QueryParams = {}) =>
      getPage<T.Incident>("/api/admin/operations/incidents" + query(p)),
    resolve: (value: string, resolutionNote: string) =>
      request<void>(`/api/admin/operations/incidents/${id(value)}/resolve`, {
        method: "PATCH",
        body: { resolutionNote },
      }),
    jobs: () => request<T.Job[]>("/api/admin/operations/jobs"),
  },
};
