"use client";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Package, Warehouse, Plus } from "lucide-react";
import { useAuth } from "@/providers/auth-provider";
import { api } from "@/lib/api";
import {
  Card,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  StatCard,
  StatusBadge,
  Table,
} from "@/components/ui";
import { date, money } from "@/lib/formatting";
export function VendorDashboard() {
  const { user } = useAuth();
  const products = useQuery({
    queryKey: ["vendor", "products", user?.id, 0, 20, "createdAt,desc"],
    queryFn: () =>
      api.vendor.list({
        vendorId: user!.id,
        page: 0,
        size: 20,
        sort: "createdAt,desc",
      }),
    enabled: !!user,
    retry: false,
  });
  return (
    <div className="stack">
      <PageHeader
        title="Vendor overview"
        description="Manage your collection and keep inventory ready for orders."
        eyebrow="Your storefront"
        actions={
          <Link href="/vendor/products/new" className="btn btn-primary">
            <Plus size={16} />
            New product
          </Link>
        }
      />
      {products.isPending ? (
        <LoadingState label="Loading vendor overview" />
      ) : products.isError ? (
        <ErrorState
          error={products.error}
          retry={() => void products.refetch()}
        />
      ) : (
        <>
          <div className="stat-grid">
            <StatCard
              label="Total products"
              value={products.data.totalElements}
              description="All products belonging to your account"
            />
            <StatCard
              label="Active on this page"
              value={
                products.data.items.filter((p) => p.status === "ACTIVE").length
              }
              description="First page of up to 20 products"
            />
            <StatCard
              label="Inactive on this page"
              value={
                products.data.items.filter((p) => p.status === "INACTIVE")
                  .length
              }
              description="First page of up to 20 products"
            />
            <StatCard
              label="Drafts on this page"
              value={
                products.data.items.filter((p) => p.status === "DRAFT").length
              }
              description="First page of up to 20 products"
            />
          </div>
          <div className="grid grid-2">
            <Card title="Your collection">
              <Package size={24} />
              <p className="muted">
                Create products, review details, and manage their catalogue
                status.
              </p>
              <Link className="btn btn-secondary" href="/vendor/products">
                Manage products
              </Link>
            </Card>
            <Card title="Stock management">
              <Warehouse size={24} />
              <p className="muted">
                Review available and reserved quantities, record adjustments and
                see their history.
              </p>
              <Link className="btn btn-secondary" href="/vendor/inventory">
                Manage inventory
              </Link>
            </Card>
          </div>
          <Card title="Recent products">
            {products.data.items.length ? (
              <Table headers={["Product", "Price", "Status", "Last updated"]}>
                <>
                  {products.data.items.slice(0, 5).map((product) => (
                    <tr key={product.id}>
                      <td>
                        <Link href={`/vendor/products/${product.id}/edit`}>
                          {product.name}
                        </Link>
                        <p className="small muted">{product.sku}</p>
                      </td>
                      <td>{money(product.priceAmount, product.currency)}</td>
                      <td>
                        <StatusBadge status={product.status} />
                      </td>
                      <td>{date(product.updatedAt)}</td>
                    </tr>
                  ))}
                </>
              </Table>
            ) : (
              <EmptyState
                title="Build your first collection"
                description="Products you create will appear here."
                action={
                  <Link href="/vendor/products/new" className="btn btn-primary">
                    Create a product
                  </Link>
                }
              />
            )}
          </Card>
        </>
      )}
    </div>
  );
}
