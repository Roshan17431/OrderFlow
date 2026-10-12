"use client";
import Link from "next/link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { invalidateCatalog } from "@/lib/catalog-cache";
import { ApiError } from "@/lib/api/http";
import { date, money, label } from "@/lib/formatting";
import { useAuth } from "@/providers/auth-provider";
import {
  Alert,
  Button,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Field,
  LoadingState,
  PageHeader,
  Pagination,
  Select,
  StatusBadge,
  Table,
} from "@/components/ui";
import type { VendorProduct } from "@/types/contracts";
type Action = "activate" | "deactivate" | "archive";
export function VendorProducts() {
  const { user } = useAuth();
  const client = useQueryClient();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState("createdAt,desc");
  const [target, setTarget] = useState<{
    product: VendorProduct;
    action: Action;
  } | null>(null);
  const products = useQuery({
    queryKey: ["vendor", "products", user?.id, page, size, sort],
    queryFn: () => api.vendor.list({ vendorId: user!.id, page, size, sort }),
    enabled: !!user,
    retry: false,
  });
  const mutation = useMutation({
    retry: false,
    mutationFn: ({
      product,
      action,
    }: {
      product: VendorProduct;
      action: Action;
    }) => api.vendor.status(product.id, action, product.version),
    onSuccess: async (value) => {
      setTarget(null);
      client.setQueryData(["vendor", "product", user?.id, value.id], value);
      await Promise.all([
        client.invalidateQueries({ queryKey: ["vendor"] }),
        invalidateCatalog(client),
      ]);
      toast.success(`Product ${label(value.status).toLowerCase()}.`);
    },
    onError: async (error) => {
      setTarget(null);
      if (error instanceof ApiError && [403, 404, 409].includes(error.status))
        await client.invalidateQueries({ queryKey: ["vendor"] });
    },
  });
  const open = (product: VendorProduct, action: Action) => {
    mutation.reset();
    setTarget({ product, action });
  };
  return (
    <div className="stack">
      <PageHeader
        title="Your products"
        description="Review and manage products belonging to your account."
        actions={
          <Link href="/vendor/products/new" className="btn btn-primary">
            <Plus size={16} />
            New product
          </Link>
        }
      />
      <div className="row">
        <Field label="Sort products">
          <Select
            value={sort}
            onChange={(event) => {
              setSort(event.target.value);
              setPage(0);
            }}
          >
            <option value="createdAt,desc">Newest first</option>
            <option value="createdAt,asc">Oldest first</option>
            <option value="name,asc">Name A–Z</option>
            <option value="price,asc">Price low to high</option>
            <option value="price,desc">Price high to low</option>
          </Select>
        </Field>
        <Field label="Products per page">
          <Select
            value={size}
            onChange={(event) => {
              setSize(Number(event.target.value));
              setPage(0);
            }}
          >
            <option value={20}>20</option>
            <option value={50}>50</option>
            <option value={100}>100</option>
          </Select>
        </Field>
      </div>
      {mutation.error && (
        <ErrorState
          error={mutation.error}
          retry={() => void products.refetch()}
        />
      )}
      {mutation.error instanceof ApiError && mutation.error.status === 409 && (
        <Alert>
          The product changed or the action is no longer allowed. Review the
          refreshed product before trying again.
        </Alert>
      )}
      {products.isPending ? (
        <LoadingState label="Loading vendor products" />
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
                "Category",
                "Price",
                "Status",
                "Updated",
                "Actions",
              ]}
            >
              <>
                {products.data.items.map((product) => (
                  <tr key={product.id}>
                    <td>
                      <Link
                        href={`/vendor/products/${product.id}/edit`}
                        onClick={() =>
                          client.setQueryData(
                            ["vendor", "product", user?.id, product.id],
                            product,
                          )
                        }
                      >
                        {product.name}
                      </Link>
                      <p className="small muted">{product.sku}</p>
                    </td>
                    <td>{product.category.name}</td>
                    <td>{money(product.priceAmount, product.currency)}</td>
                    <td>
                      <StatusBadge status={product.status} />
                    </td>
                    <td>{date(product.updatedAt)}</td>
                    <td>
                      <div className="row">
                        <Link
                          className="btn btn-secondary"
                          href={`/vendor/products/${product.id}/edit`}
                          onClick={() =>
                            client.setQueryData(
                              ["vendor", "product", user?.id, product.id],
                              product,
                            )
                          }
                        >
                          {product.status === "ARCHIVED" ? "View" : "Edit"}
                        </Link>
                        <Link
                          className="btn btn-ghost"
                          href={`/vendor/inventory/${product.id}`}
                          onClick={() =>
                            client.setQueryData(
                              ["vendor", "product", user?.id, product.id],
                              product,
                            )
                          }
                        >
                          Stock
                        </Link>
                        {product.status !== "ARCHIVED" && (
                          <>
                            {product.status === "ACTIVE" ? (
                              <Button
                                variant="secondary"
                                disabled={mutation.isPending}
                                aria-label={`Deactivate ${product.name}`}
                                onClick={() => open(product, "deactivate")}
                              >
                                Deactivate
                              </Button>
                            ) : (
                              <Button
                                variant="secondary"
                                disabled={mutation.isPending}
                                aria-label={`Activate ${product.name}`}
                                onClick={() => open(product, "activate")}
                              >
                                Activate
                              </Button>
                            )}
                            <Button
                              variant="ghost"
                              disabled={mutation.isPending}
                              aria-label={`Archive ${product.name}`}
                              onClick={() => open(product, "archive")}
                            >
                              Archive
                            </Button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </>
            </Table>
          ) : (
            <EmptyState
              title="No products on this page"
              description="Create a product to begin your collection."
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
      <ConfirmDialog
        open={!!target}
        onClose={() => setTarget(null)}
        onConfirm={() => {
          if (target) mutation.mutate(target);
        }}
        title={
          target
            ? `${label(target.action)} ${target.product.name}?`
            : "Change product status"
        }
        description={
          target?.action === "archive"
            ? "Archiving permanently ends this product’s active lifecycle. It cannot be edited or reactivated."
            : target?.action === "deactivate"
              ? "This product will no longer be visible in the public catalogue."
              : "The product will become available in the public catalogue if its category is active."
        }
        pending={mutation.isPending}
        confirmLabel={target ? `${label(target.action)} product` : "Confirm"}
      />
    </div>
  );
}
