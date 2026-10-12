"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Bell } from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/providers/auth-provider";
import { api } from "@/lib/api";
import { connectNotifications } from "@/lib/sse";
import { relativeDate } from "@/lib/formatting";
export function NotificationBell() {
  const { user, token, status } = useAuth();
  const cache = useQueryClient();
  const [connection, setConnection] = useState<
    "live" | "reconnecting" | "polling"
  >("polling");
  const unread = useQuery({
    queryKey: ["unread", user?.id],
    queryFn: api.notifications.unread,
    enabled: status === "authenticated",
    refetchInterval: 30000,
  });
  const recent = useQuery({
    queryKey: ["notifications", user?.id, "recent"],
    queryFn: () => api.notifications.list({ page: 0, size: 5 }),
    enabled: status === "authenticated",
    refetchInterval: 30000,
  });
  useEffect(() => {
    if (status !== "authenticated" || !token) return;
    const abort = new AbortController();
    void connectNotifications(
      abort.signal,
      () => {
        void cache.invalidateQueries({ queryKey: ["notifications", user?.id] });
        void cache.invalidateQueries({ queryKey: ["unread", user?.id] });
      },
      setConnection,
    );
    return () => abort.abort();
  }, [status, token, user?.id, cache]);
  if (status !== "authenticated") return null;
  return (
    <details className="notification-bell">
      <summary
        aria-label={`Notifications${unread.data?.count ? `, ${unread.data.count} unread` : ""}`}
      >
        <Bell size={20} />
        {unread.data && unread.data.count > 0 && (
          <span className="unread-badge">
            {unread.data.count > 99 ? "99+" : unread.data.count}
          </span>
        )}
      </summary>
      <div className="notification-popover">
        <div className="row justify-between">
          <strong>Notifications</strong>
          <span className="small muted">
            {connection === "live" ? "Live updates" : "Checking periodically"}
          </span>
        </div>
        {recent.isLoading ? (
          <p className="small muted">Loading updates…</p>
        ) : recent.isError ? (
          <p className="small">Notifications temporarily unavailable.</p>
        ) : recent.data?.items.length ? (
          recent.data.items.map((n) => (
            <Link
              href="/notifications"
              key={n.id}
              className={`bell-item ${n.read ? "" : "unread"}`}
            >
              <strong>{n.title}</strong>
              <span>{relativeDate(n.createdAt)}</span>
            </Link>
          ))
        ) : (
          <p className="small muted mt-4">You’re all caught up.</p>
        )}
        <Link className="btn btn-secondary w-full" href="/notifications">
          View all notifications
        </Link>
      </div>
    </details>
  );
}
