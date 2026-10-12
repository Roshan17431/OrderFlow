"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/providers/auth-provider";
import {
  PageHeader,
  Card,
  StatusBadge,
  Button,
  ConfirmDialog,
  ErrorState,
} from "@/components/ui";
import { date } from "@/lib/formatting";
export function Account() {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [confirm, setConfirm] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  async function all() {
    setPending(true);
    try {
      await logout(true);
      router.push("/login");
    } catch (e) {
      setError(e);
    } finally {
      setPending(false);
      setConfirm(false);
    }
  }
  return (
    <>
      <PageHeader
        title="Your account"
        description="Your profile and security, in one place."
      />
      {error && <ErrorState error={error} />}
      <div className="grid grid-2">
        <Card title="Account information">
          <dl>
            <dt className="muted small">Display name</dt>
            <dd className="mb-4">{user?.displayName}</dd>
            <dt className="muted small">Email address</dt>
            <dd className="mb-4">{user?.email}</dd>
            <dt className="muted small">Member since</dt>
            <dd className="mb-4">{date(user?.createdAt)}</dd>
            <dt className="muted small">Roles</dt>
            <dd className="row">
              {user?.roles.map((role) => (
                <StatusBadge key={role} status={role} />
              ))}
            </dd>
          </dl>
        </Card>
        <Card title="Session security">
          <p className="muted">
            Sign out all sessions to revoke existing access and refresh sessions
            for your account.
          </p>
          <Button variant="danger" onClick={() => setConfirm(true)}>
            Sign out all devices
          </Button>
        </Card>
      </div>
      <ConfirmDialog
        open={confirm}
        onClose={() => setConfirm(false)}
        onConfirm={all}
        title="Sign out all devices?"
        description="You will need to sign in again on every device."
        pending={pending}
        confirmLabel="Sign out all"
      />
    </>
  );
}
