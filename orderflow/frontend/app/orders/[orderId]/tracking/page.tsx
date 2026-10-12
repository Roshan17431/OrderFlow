import { TrackingPage } from "@/features/orders/tracking-page";
export default async function Page({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = await params;
  return <TrackingPage key={orderId} orderId={orderId} />;
}
