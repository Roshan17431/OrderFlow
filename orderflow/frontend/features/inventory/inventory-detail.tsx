"use client";
import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { date } from "@/lib/formatting";
import { useAuth } from "@/providers/auth-provider";
import {
  Alert,
  Button,
  Card,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  Pagination,
  StatCard,
  Table,
} from "@/components/ui";
import { findVendorProduct } from "@/features/vendor/product-lookup";
import { AdjustmentForm } from "./adjustment-form";
export function InventoryDetail({ productId }: { productId: string }) {
  const { user } = useAuth();
  const [page, setPage] = useState(0);
  const product = useQuery({
    queryKey: ["vendor", "product", user?.id, productId],
    queryFn: () => findVendorProduct(productId, user!.id),
    enabled: !!user,
    staleTime: 30_000,
    retry: false,
  });
  const stock = useQuery({
    queryKey: ["inventory", "stock", productId],
    queryFn: () => api.inventory.get(productId),
    retry: false,
  });
  const history = useQuery({
    queryKey: ["inventory", "history", productId, page],
    queryFn: () => api.inventory.history(productId, { page, size: 20 }),
    enabled: stock.isSuccess,
    retry: false,
  });
  const refresh = async () => {
    const [stockResult, historyResult] = await Promise.all([
      stock.refetch(),
      stock.data ? history.refetch() : Promise.resolve(null),
    ]);
    return stockResult.isSuccess && historyResult?.isSuccess === true;
  };
  return (
    <div className="stack">
      <PageHeader
        title={
          product.data ? `${product.data.name} inventory` : "Product inventory"
        }
        description={`Product ${productId}`}
        actions={
          <>
            <Link href="/vendor/inventory" className="btn btn-secondary">
              Back to inventory
            </Link>
            <Button variant="secondary" onClick={() => void refresh()}>
              Refresh
            </Button>
          </>
        }
      />
      {stock.isPending ? (
        <LoadingState label="Loading product stock" />
      ) : stock.isError ? (
        <ErrorState error={stock.error} retry={() => void stock.refetch()} />
      ) : (
        <>
          {product.isError && (
            <ErrorState
              error={product.error}
              retry={() => void product.refetch()}
            />
          )}
          <div className="stat-grid">
            <StatCard label="On hand" value={stock.data.onHandQuantity} />
            <StatCard label="Reserved" value={stock.data.reservedQuantity} />
            <StatCard label="Available" value={stock.data.availableQuantity} />
            <StatCard
              label="Inventory version"
              value={stock.data.version}
              description={`Updated ${date(stock.data.updatedAt)}`}
            />
          </div>
          {BigInt(stock.data.onHandQuantity) <
            BigInt(stock.data.reservedQuantity) && (
            <Alert>
              On-hand stock is below the reserved quantity. Review adjustments
              and pending reservations.
            </Alert>
          )}
          <div className="split">
            <Card title="Adjustment history">
              {history.isPending ? (
                <LoadingState label="Loading adjustment history" />
              ) : history.isError ? (
                <ErrorState
                  error={history.error}
                  retry={() => void history.refetch()}
                />
              ) : (
                <>
                  {history.data.items.length ? (
                    <Table
                      headers={[
                        "Recorded",
                        "Change",
                        "Result on hand / reserved",
                        "Reason / reference",
                        "Actor",
                      ]}
                    >
                      <>
                        {history.data.items.map((item) => (
                          <tr key={item.id}>
                            <td>{date(item.createdAt)}</td>
                            <td>
                              {item.quantityDelta > 0 ? "+" : ""}
                              {item.quantityDelta}
                            </td>
                            <td>
                              {item.resultingOnHand} / {item.resultingReserved}
                            </td>
                            <td>
                              {item.reason}
                              {item.reference && (
                                <p className="small muted">{item.reference}</p>
                              )}
                              <p className="small muted">
                                Support: {item.correlationId}
                              </p>
                            </td>
                            <td className="small">{item.actorUserId}</td>
                          </tr>
                        ))}
                      </>
                    </Table>
                  ) : (
                    <EmptyState
                      title="No stock adjustments"
                      description="Confirmed adjustments will appear here."
                    />
                  )}
                  <Pagination
                    page={page}
                    totalPages={history.data.totalPages}
                    totalElements={history.data.totalElements}
                    onPageChange={setPage}
                  />
                </>
              )}
            </Card>
            <Card title="Adjust stock">
              <AdjustmentForm stock={stock.data} onRefresh={refresh} />
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
