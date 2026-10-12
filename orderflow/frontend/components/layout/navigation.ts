import type { Role } from "@/types/contracts";
export const customerLinks = [
  { href: "/products", label: "Shop" },
  { href: "/cart", label: "Cart" },
  { href: "/orders", label: "Orders" },
  { href: "/notifications", label: "Notifications" },
  { href: "/settings/notifications", label: "Preferences" },
  { href: "/account", label: "Account" },
];
export const workspaces = {
  VENDOR: [
    { href: "/vendor", label: "Overview" },
    { href: "/vendor/products", label: "Products" },
    { href: "/vendor/inventory", label: "Inventory" },
  ],
  DELIVERY_AGENT: [
    { href: "/delivery-agent", label: "Overview" },
    { href: "/delivery-agent/deliveries", label: "My deliveries" },
  ],
  ADMIN: [
    { href: "/admin", label: "Overview" },
    { href: "/admin/categories", label: "Categories" },
    { href: "/admin/orders", label: "Orders" },
    { href: "/admin/payments", label: "Payments & refunds" },
    { href: "/admin/deliveries", label: "Deliveries" },
    { href: "/admin/operations", label: "Operations" },
    { href: "/admin/operations/reports", label: "Daily reports" },
    { href: "/admin/operations/incidents", label: "Incidents" },
    { href: "/admin/operations/jobs", label: "Scheduled jobs" },
  ],
};
export function navigation(roles: Role[]) {
  return [
    ...customerLinks,
    ...(roles.includes("ADMIN")
      ? [{ href: "/admin", label: "Admin workspace" }]
      : []),
    ...(roles.includes("VENDOR")
      ? [{ href: "/vendor", label: "Vendor workspace" }]
      : []),
    ...(roles.includes("DELIVERY_AGENT")
      ? [{ href: "/delivery-agent", label: "Delivery workspace" }]
      : []),
  ];
}
