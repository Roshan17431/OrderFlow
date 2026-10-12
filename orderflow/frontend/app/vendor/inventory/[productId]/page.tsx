import { InventoryDetail } from "@/features/inventory/inventory-detail";
export default async function Page({
  params,
}: {
  params: Promise<{ productId: string }>;
}) {
  const { productId } = await params;
  return <InventoryDetail productId={productId} />;
}
