import { PaymentDetailPage } from "@/features/admin/payments";
export default async function Page({
  params,
}: {
  params: Promise<{ paymentId: string }>;
}) {
  const { paymentId } = await params;
  return <PaymentDetailPage paymentId={paymentId} />;
}
