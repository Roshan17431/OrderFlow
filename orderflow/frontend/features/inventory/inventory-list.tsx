"use client";
import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { api } from "@/lib/api";
import { date } from "@/lib/formatting";
import { useAuth } from "@/providers/auth-provider";
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Field,
  Input,
  LoadingState,
  PageHeader,
  Pagination,
  Table,
} from "@/components/ui";
import type { VendorProduct } from "@/types/contracts";
const lookup = z.object({
  productId: z.string().trim().uuid("Enter a valid product identifier."),
});
function ProductStockRow({ product }: { product: VendorProduct }) {
  const { user } = useAuth();
  const client = useQueryClient();
  const stock = useQuery({
    queryKey: ["inventory", "stock", product.id],
    queryFn: () => api.inventory.get(product.id),
    retry: false,
  });
  return (
    <tr>
      <td>
        {product.name}
        <p className="small muted">{product.sku}</p>
      </td>
      {stock.isPending ? (
        <td colSpan={5}>
          <span role="status">Loading stock…</span>
        </td>
      ) : stock.isError ? (
        <td colSpan={5}>
          <ErrorState error={stock.error} retry={() => void stock.refetch()} />
        </td>
      ) : (
        <>
          <td>{stock.data.onHandQuantity}</td>
          <td>{stock.data.reservedQuantity}</td>
          <td>{stock.data.availableQuantity}</td>
          <td>{stock.data.version}</td>
          <td>{date(stock.data.updatedAt)}</td>
        </>
      )}
      <td>
        <Link
          className="btn btn-secondary"
          href={`/vendor/inventory/${product.id}`}
          onClick={() =>
            client.setQueryData(
              ["vendor", "product", user?.id, product.id],
              product,
            )
          }
        >
          Manage stock
        </Link>
      </td>
    </tr>
  );
}
export function InventoryList() {
  const { user } = useAuth();
  const router = useRouter();
  const [page, setPage] = useState(0);
  const products = useQuery({
    queryKey: ["vendor", "products", user?.id, page, 20, "createdAt,desc"],
    queryFn: () =>
      api.vendor.list({
        vendorId: user!.id,
        page,
        size: 20,
        sort: "createdAt,desc",
      }),
    enabled: !!user,
    retry: false,
  });
  const form = useForm<{ productId: string }>({
    resolver: zodResolver(lookup),
    defaultValues: { productId: "" },
  });
  return (
    <div className="stack">
      <PageHeader
        title="Inventory"
        description="Confirmed stock quantities for products belonging to your account."
      />
      <Card title="Open a product directly">
        <form
          noValidate
          className="row"
          onSubmit={form.handleSubmit(({ productId }) =>
            router.push(`/vendor/inventory/${encodeURIComponent(productId)}`),
          )}
        >
          <div style={{ flex: 1 }}>
            <Field
              label="Product identifier"
              error={form.formState.errors.productId?.message}
            >
              <Input
                {...form.register("productId")}
                placeholder="Product UUID"
              />
            </Field>
          </div>
          <Button type="submit">Open inventory</Button>
        </form>
      </Card>
      {products.isPending ? (
        <LoadingState label="Loading inventory products" />
      ) : products.isError ? (
        <ErrorState
          error={products.error}
          retry={() => void products.refetch()}
        />
      ) : (
        <>
          {products.data.items.length ? (
            <Table
              headers={[
                "Product / SKU",
                "On hand",
                "Reserved",
                "Available",
                "Version",
                "Updated",
                "Action",
              ]}
            >
              <>
                {products.data.items.map((product) => (
                  <ProductStockRow product={product} key={product.id} />
                ))}
              </>
            </Table>
          ) : (
            <EmptyState
              title="No products on this page"
              description="Add a product before managing its stock."
              action={
                <Link className="btn btn-primary" href="/vendor/products/new">
                  Create a product
                </Link>
              }
            />
          )}
          <Pagination
            page={page}
            totalPages={products.data.totalPages}
            totalElements={products.data.totalElements}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}
