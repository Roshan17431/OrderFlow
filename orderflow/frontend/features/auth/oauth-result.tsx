"use client";
import { useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { api } from "@/lib/api";
import { session } from "@/lib/auth/session";
import { roleHome } from "@/lib/auth/redirects";
import { ErrorState, LoadingState, EmptyState } from "@/components/ui";
import type { Authentication } from "@/types/contracts";
export function OAuthResult() {
  const search = useSearchParams();
  const router = useRouter();
  const [code] = useState(() => search.get("code"));
  const flight = useRef<Promise<Authentication> | null>(null);
  const [error, setError] = useState<unknown>();
  useEffect(() => {
    let active = true;
    if (!code) return;
    if (!flight.current) {
      session.clear();
      flight.current = api.auth.exchange(code);
      window.history.replaceState(null, "", window.location.pathname);
    }
    const epoch = session.get().epoch;
    void flight.current
      .then((value) => {
        if (active && session.set(value, epoch))
          router.replace(roleHome(value.user.roles));
      })
      .catch((e) => {
        if (active) setError(e);
      });
    return () => {
      active = false;
    };
  }, [router, code]);
  if (error) return <ErrorState error={error} />;
  if (!code)
    return (
      <EmptyState
        title="OAuth sign-in could not be completed"
        description="The sign-in code is missing. Start again from the login page."
        action={
          <Link href="/login" className="btn btn-primary">
            Return to sign in
          </Link>
        }
      />
    );
  return <LoadingState label="Completing secure sign in" />;
}
