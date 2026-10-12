import Link from "next/link";
import { EmptyState } from "@/components/ui";
export default function NotFound() {
  return (
    <EmptyState
      title="That page isn’t here"
      description="Let’s get you back to the collection."
      action={
        <Link className="btn btn-primary" href="/products">
          Browse products
        </Link>
      }
    />
  );
}
