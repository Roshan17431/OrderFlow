"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useAuth } from "@/providers/auth-provider";
import {
  Alert,
  Card,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
} from "@/components/ui";
import { ProductForm } from "./product-form";
import { findVendorProduct } from "./product-lookup";
export function ProductEditor({ productId }: { productId?: string }) {
  const { user } = useAuth();
  const router = useRouter();
  const client = useQueryClient();
  const categories = useQuery({
    queryKey: ["products", "categories"],
    queryFn: api.products.categories,
    retry: false,
  });
  const product = useQuery({
    queryKey: ["vendor", "product", user?.id, productId],
    queryFn: () => findVendorProduct(productId!, user!.id),
    enabled: !!user && !!productId,
    staleTime: 30_000,
    retry: false,
  });
  const loading = categories.isPending || (!!productId && product.isPending);
  return (
    <div className="stack">
      <PageHeader
        title={productId ? "Edit product" : "Create product"}
        description={
          productId
            ? "Review product details and save with the current version."
            : "Start a new product as a draft, then activate it when it is ready."
        }
        actions={
          <Link href="/vendor/products" className="btn btn-secondary">
            Back to products
          </Link>
        }
      />
      {loading ? (
        <LoadingState label="Loading product form" />
      ) : categories.isError ? (
        <ErrorState
          error={categories.error}
          retry={() => void categories.refetch()}
        />
      ) : productId && product.isError ? (
        <ErrorState
          error={product.error}
          retry={() => void product.refetch()}
        />
      ) : (
        <Card>
          {product.data && (
            <p>
              <StatusBadge status={product.data.status} />
            </p>
          )}
          {!categories.data?.length && !productId && (
            <Alert>
              No active categories are available. A category must be created or
              activated by an administrator before adding a product.
            </Alert>
          )}
          <ProductForm
            key={
              product.data
                ? `${product.data.id}:${product.data.version}`
                : "new"
            }
            product={product.data}
            categories={categories.data ?? []}
            onReload={() => void product.refetch()}
            onSaved={(value) => {
              client.setQueryData(
                ["vendor", "product", user?.id, value.id],
                value,
              );
              router.push(`/vendor/products/${value.id}/edit`);
            }}
          />
        </Card>
      )}
    </div>
  );
}
