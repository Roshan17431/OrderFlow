import { AgentDeliveryDetail } from "@/features/delivery-agent/delivery-detail";
export default async function Page({
  params,
}: {
  params: Promise<{ deliveryId: string }>;
}) {
  const { deliveryId } = await params;
  return <AgentDeliveryDetail deliveryId={deliveryId} />;
}
