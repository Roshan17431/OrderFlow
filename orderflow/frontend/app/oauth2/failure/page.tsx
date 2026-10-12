import Link from "next/link";
import { EmptyState } from "@/components/ui";
export default function Page() {
  return (
    <EmptyState
      title="Sign-in didn’t complete"
      description="Your provider sign-in was cancelled or could not be verified. Try again or use your email account."
      action={
        <Link href="/login" className="btn btn-primary">
          Return to sign in
        </Link>
      }
    />
  );
}
