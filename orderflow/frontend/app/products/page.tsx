import { Suspense } from "react";
import { CatalogPage } from "@/features/catalog/catalog-page";
import { LoadingState } from "@/components/ui";
export default function Page() {
  return (
    <Suspense fallback={<LoadingState label="Loading catalog" />}>
      <CatalogPage />
    </Suspense>
  );
}
