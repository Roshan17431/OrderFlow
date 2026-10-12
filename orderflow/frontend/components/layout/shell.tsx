"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Box, ShoppingBag, Menu, LogOut, ArrowUpRight } from "lucide-react";
import { useAuth } from "@/providers/auth-provider";
import { Button } from "@/components/ui";
import { RoleGuard } from "./role-guard";
import { navigation, workspaces } from "./navigation";
import type { ReactNode } from "react";
import { toast } from "sonner";
import { NotificationBell } from "@/features/notifications/notification-bell";
export function Shell({ children }: { children: ReactNode }) {
  const path = usePathname();
  const { user, status, logout } = useAuth();
  const router = useRouter();
  const role = path.startsWith("/admin")
    ? "ADMIN"
    : path.startsWith("/vendor")
      ? "VENDOR"
      : path.startsWith("/delivery-agent")
        ? "DELIVERY_AGENT"
        : null;
  const needsAuth =
    role ||
    [
      "/cart",
      "/checkout",
      "/orders",
      "/notifications",
      "/settings",
      "/account",
    ].some((p) => path === p || path.startsWith(p + "/"));
  const links = role ? workspaces[role] : navigation(user?.roles ?? []);
  const brand = (
    <Link href="/" className="brand">
      <Box size={25} />
      <span>
        OrderFlow<span className="brand-dot">.</span>
      </span>
    </Link>
  );
  async function signOut() {
    try {
      await logout();
      router.push("/login");
    } catch {
      toast.error(
        "Signed out locally. Server logout could not be confirmed; retry when connected.",
      );
      router.push("/login");
    }
  }
  const mobile = (
    <details className="mobile-menu">
      <summary aria-label="Open navigation">
        <Menu size={22} />
      </summary>
      <nav>
        {links.map((link) => (
          <Link key={link.href} href={link.href}>
            {link.label}
          </Link>
        ))}
      </nav>
    </details>
  );
  return (
    <>
      {role ? (
        <div className="dashboard-shell">
          <aside className="sidebar">
            {brand}
            <p className="sidebar-caption">
              {role === "ADMIN"
                ? "Administration"
                : role === "VENDOR"
                  ? "Vendor studio"
                  : "Delivery workspace"}
            </p>
            <nav>
              {links.map((link) => (
                <Link
                  key={link.href}
                  href={link.href}
                  className={path === link.href ? "active" : ""}
                >
                  {link.label}
                </Link>
              ))}
            </nav>
            <Link href="/products" className="sidebar-bottom">
              Back to storefront <ArrowUpRight size={16} />
            </Link>
          </aside>
          <div className="dashboard-content">
            <header className="dashboard-topbar">
              {mobile}
              <span className="muted">Your workspace, connected.</span>
              <div className="row">
                <NotificationBell />
                <span className="small">{user?.displayName}</span>
                <Button
                  variant="ghost"
                  onClick={() => void signOut()}
                  aria-label="Sign out"
                >
                  <LogOut size={18} />
                </Button>
              </div>
            </header>
            <main id="main" className="dashboard-main">
              <RoleGuard
                roles={role === "VENDOR" ? ["VENDOR", "ADMIN"] : [role]}
              >
                {children}
              </RoleGuard>
            </main>
          </div>
        </div>
      ) : (
        <>
          <div className="assurance-strip">
            Thoughtful finds. A seamless experience.
          </div>
          <header className="site-header">
            <div className="container header-inner">
              {brand}
              <nav className="desktop-links">
                <Link href="/">Home</Link>
                <Link href="/products">Shop the collection</Link>
                {status === "authenticated" && (
                  <>
                    <Link href="/orders">My orders</Link>
                    {navigation(user?.roles ?? [])
                      .filter((link) => link.label.endsWith("workspace"))
                      .map((link) => (
                        <Link key={link.href} href={link.href}>
                          {link.label}
                        </Link>
                      ))}
                  </>
                )}
              </nav>
              <div className="row">
                {status === "authenticated" ? (
                  <>
                    <Link className="small account-link" href="/account">
                      {user?.displayName}
                    </Link>
                    <NotificationBell />
                    <Button
                      variant="ghost"
                      onClick={() => void signOut()}
                      aria-label="Sign out"
                    >
                      <LogOut size={18} />
                    </Button>
                  </>
                ) : (
                  <Link href="/login" className="small">
                    Sign in
                  </Link>
                )}
                <Link
                  href="/cart"
                  className="cart-link"
                  aria-label="Shopping cart"
                >
                  <ShoppingBag size={21} />
                </Link>
                {mobile}
              </div>
            </div>
          </header>
          <main id="main" className="container site-main">
            {needsAuth ? <RoleGuard>{children}</RoleGuard> : children}
          </main>
          <footer className="site-footer">
            <div className="container">
              <div>
                {brand}
                <p className="muted small">
                  Good things, from discovery to your door.
                </p>
              </div>
              <nav>
                <Link href="/products">Collection</Link>
                <Link href="/orders">Your orders</Link>
                <Link href="/settings/notifications">Preferences</Link>
              </nav>
              <p className="small muted">
                OrderFlow · Portfolio commerce experience
                <br />
                Payments and messages use simulators.
              </p>
            </div>
          </footer>
        </>
      )}
    </>
  );
}
