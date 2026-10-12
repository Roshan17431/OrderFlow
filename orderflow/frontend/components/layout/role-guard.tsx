"use client";
import { useEffect, Fragment, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/providers/auth-provider";
import type { Role } from "@/types/contracts";
import { EmptyState, LoadingState } from "@/components/ui";
export function RoleGuard({
  children,
  roles,
}: {
  children: ReactNode;
  roles?: Role[];
}) {
  const { user, status, epoch } = useAuth();
  const router = useRouter();
  const path = usePathname();
  useEffect(() => {
    if (status === "anonymous")
      router.replace(`/login?returnTo=${encodeURIComponent(path)}`);
  }, [status, path, router]);
  if (status !== "authenticated")
    return <LoadingState label="Restoring your session" />;
  if (roles && !roles.some((role) => user?.roles.includes(role)))
    return (
      <EmptyState
        title="This area needs a different role"
        description="Your account does not have permission to use this workspace. Backend permissions also protect every request."
      />
    );
  // Drop private observer results and local form state on an identity change,
  // including resources whose query keys contain only a delivery/product ID.
  return <Fragment key={epoch}>{children}</Fragment>;
}
