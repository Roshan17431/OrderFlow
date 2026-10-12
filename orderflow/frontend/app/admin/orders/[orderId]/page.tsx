import { AdminOrderDetail } from "@/features/admin/orders";
export default async function Page({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = await params;
  return <AdminOrderDetail orderId={orderId} />;
}
