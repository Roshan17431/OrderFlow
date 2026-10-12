import { Suspense } from "react";
import { OAuthResult } from "@/features/auth/oauth-result";
import { LoadingState } from "@/components/ui";
export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <OAuthResult />
    </Suspense>
  );
}
