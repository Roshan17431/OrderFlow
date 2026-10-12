import { DeliveriesPage } from "@/features/admin/deliveries";
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ orderId?: string; deliveryId?: string }>;
}) {
  const { orderId, deliveryId } = await searchParams;
  return (
    <DeliveriesPage initialOrderId={orderId} initialDeliveryId={deliveryId} />
  );
}
