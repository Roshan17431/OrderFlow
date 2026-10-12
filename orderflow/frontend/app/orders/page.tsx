import { Suspense } from "react";
import { OrdersPage } from "@/features/orders/orders-page";
import { LoadingState } from "@/components/ui";
export default function Page() {
  return (
    <Suspense fallback={<LoadingState label="Loading orders" />}>
      <OrdersPage />
    </Suspense>
  );
}
