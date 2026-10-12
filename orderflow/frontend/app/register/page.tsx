import { Suspense } from "react";
import { AuthForm } from "@/features/auth/auth-form";
import { LoadingState } from "@/components/ui";
export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <AuthForm mode="register" />
    </Suspense>
  );
}
