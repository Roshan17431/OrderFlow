export function assertExclusivePendingOrder(
  page: { items: { id: string; fulfillmentStatus: string }[]; totalElements: number },
  orderId: string,
) {
  if (!Array.isArray(page.items) || !Number.isSafeInteger(page.totalElements) || page.totalElements !== page.items.length)
    throw new Error("The complete bounded delivery queue must be visible before fixture agent activation.");
  if (!page.items.some((row) => row.id === orderId && row.fulfillmentStatus === "DELIVERY_PENDING") ||
      page.items.some((row) => row.id !== orderId && row.fulfillmentStatus === "DELIVERY_PENDING"))
    throw new Error("Only the current synthetic order may be pending before fixture agent activation.");
}

export function assertFixtureAssignment(
  activeDeliveryId: string | null,
  delivery: { id: string; orderId: string },
  expectedDeliveryId: string,
  expectedOrderId: string,
) {
  if (activeDeliveryId !== expectedDeliveryId || delivery.id !== expectedDeliveryId || delivery.orderId !== expectedOrderId)
    throw new Error("Refusing delivery transitions for an assignment outside the current synthetic order.");
}
