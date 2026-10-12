"use client";
import { ErrorState } from "@/components/ui";
export default function ErrorPage({
  error,
  reset,
}: {
  error: Error;
  reset: () => void;
}) {
  return <ErrorState error={error} retry={reset} />;
}
