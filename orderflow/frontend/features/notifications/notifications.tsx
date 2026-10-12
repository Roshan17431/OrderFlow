"use client";
import { useState } from "react";
import Link from "next/link";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useAuth } from "@/providers/auth-provider";
import {
  PageHeader,
  Card,
  Button,
  Select,
  StatusBadge,
  EmptyState,
  ErrorState,
  LoadingState,
  Pagination,
} from "@/components/ui";
import { relativeDate, label } from "@/lib/formatting";
export function Notifications() {
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState("all");
  const cache = useQueryClient();
  const list = useQuery({
    queryKey: ["notifications", user?.id, page, filter],
    queryFn: () =>
      api.notifications.list({
        page,
        size: 20,
        read: filter === "all" ? undefined : filter === "read",
      }),
    refetchInterval: 30000,
  });
  const unread = useQuery({
    queryKey: ["unread", user?.id],
    queryFn: api.notifications.unread,
    refetchInterval: 30000,
  });
  const mutation = useMutation({
    mutationFn: async (id: string) =>
      id === "all"
        ? await api.notifications.readAll()
        : await api.notifications.read(id),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["notifications", user?.id] });
      void cache.invalidateQueries({ queryKey: ["unread", user?.id] });
    },
  });
  return (
    <>
      <PageHeader
        title="Your notifications"
        description={`${unread.data?.count ?? "—"} unread · Updates from your OrderFlow journey.`}
        actions={
          <>
            <Link href="/settings/notifications" className="btn btn-secondary">
              Preferences
            </Link>
            <Button
              pending={mutation.isPending}
              disabled={!unread.data?.count}
              onClick={() => mutation.mutate("all")}
            >
              Mark all as read
            </Button>
          </>
        }
      />
      <div className="row mb-6">
        <label htmlFor="notification-filter">Show</label>
        <Select
          id="notification-filter"
          className="max-w-48"
          value={filter}
          onChange={(e) => {
            setFilter(e.target.value);
            setPage(0);
          }}
        >
          <option value="all">All notifications</option>
          <option value="unread">Unread</option>
          <option value="read">Read</option>
        </Select>
      </div>
      {mutation.error && <ErrorState error={mutation.error} />}{" "}
      {list.isLoading ? (
        <LoadingState />
      ) : list.error ? (
        <ErrorState error={list.error} retry={() => void list.refetch()} />
      ) : !list.data?.items.length ? (
        <EmptyState
          title="You’re all caught up"
          description="Your order and delivery updates will appear here."
        />
      ) : (
        <>
          <Card className="p-0">
            {list.data.items.map((n) => (
              <article
                key={n.id}
                className={`notification-row ${n.read ? "" : "unread"}`}
              >
                <div className="row justify-between">
                  <h2 className="text-base mb-2">{n.title}</h2>
                  <StatusBadge status={n.read ? "READ" : "UNREAD"} />
                </div>
                <p>{n.message}</p>
                <div className="row justify-between">
                  <span className="small muted">
                    {label(n.type)} · {relativeDate(n.createdAt)}
                  </span>
                  {!n.read && (
                    <Button
                      variant="secondary"
                      pending={mutation.isPending}
                      onClick={() => mutation.mutate(n.id)}
                    >
                      Mark as read
                    </Button>
                  )}
                </div>
              </article>
            ))}
          </Card>
          <Pagination
            page={page}
            totalPages={list.data.totalPages}
            totalElements={list.data.totalElements}
            onPageChange={setPage}
          />
        </>
      )}
    </>
  );
}
