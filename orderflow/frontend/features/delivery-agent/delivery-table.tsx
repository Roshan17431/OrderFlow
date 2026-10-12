"use client";
import Link from "next/link";
import { EmptyState, Pagination, StatusBadge, Table } from "@/components/ui";
import { date } from "@/lib/formatting";
import type { Delivery, Page } from "@/types/contracts";
export function DeliveryTable({
  data,
  onPageChange,
}: {
  data: Page<Delivery>;
  onPageChange: (page: number) => void;
}) {
  return (
    <>
      {data.items.length ? (
        <Table
          headers={[
            "Delivery / order",
            "Recipient",
            "Destination",
            "Status",
            "Assigned",
            "Action",
          ]}
        >
          <>
            {data.items.map((delivery) => (
              <tr key={delivery.id}>
                <td>
                  <span className="small">{delivery.id}</span>
                  <p className="small muted">Order {delivery.orderId}</p>
                </td>
                <td>{delivery.shippingAddress.recipientName}</td>
                <td>
                  {delivery.shippingAddress.city},{" "}
                  {delivery.shippingAddress.stateOrRegion}
                </td>
                <td>
                  <StatusBadge status={delivery.status} />
                </td>
                <td>{date(delivery.assignedAt)}</td>
                <td>
                  <Link
                    className="btn btn-secondary"
                    href={`/delivery-agent/deliveries/${delivery.id}`}
                  >
                    View delivery
                  </Link>
                </td>
              </tr>
            ))}
          </>
        </Table>
      ) : (
        <EmptyState
          title="No assigned deliveries"
          description="Deliveries assigned to your account will appear here."
        />
      )}
      <Pagination
        page={data.page}
        totalPages={data.totalPages}
        totalElements={data.totalElements}
        onPageChange={onPageChange}
      />
    </>
  );
}
