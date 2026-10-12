import { OrderDetailPage } from "@/features/orders/order-detail-page";
export default async function Page({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = await params;
  return <OrderDetailPage key={orderId} orderId={orderId} />;
}
