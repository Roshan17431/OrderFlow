"use client";
import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import {
  Alert,
  Button,
  EmptyState,
  ErrorState,
  Field,
  Input,
  LoadingState,
  PageHeader,
  Pagination,
  Select,
} from "@/components/ui";
import {
  catalogFilters,
  catalogQuery,
  catalogUrl,
  productSorts,
  validatePrices,
  type CatalogFilters,
} from "./filters";
import { ProductCard } from "./product-card";

function Filters({
  initial,
  categories,
  onApply,
}: {
  initial: CatalogFilters;
  categories: { id: string; name: string; slug: string }[];
  onApply: (filters: CatalogFilters) => void;
}) {
  const [values, setValues] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  function change(key: keyof CatalogFilters, value: string) {
    setValues((previous) => ({ ...previous, [key]: value }));
    setError(null);
  }
  return (
    <>
      <form
        className="filters"
        onSubmit={(event) => {
          event.preventDefault();
          const issue = validatePrices(values.minPrice, values.maxPrice);
          setError(issue);
          if (!issue) onApply({ ...values, q: values.q.trim(), page: 0 });
        }}
        noValidate
      >
        <Field label="Search products">
          <Input
            type="search"
            value={values.q}
            onChange={(event) => change("q", event.target.value)}
            placeholder="Search the collection"
          />
        </Field>
        <Field label="Category">
          <Select
            value={values.category}
            onChange={(event) => change("category", event.target.value)}
          >
            <option value="">All categories</option>
            {values.category &&
              !categories.some(
                (category) => category.slug === values.category,
              ) && <option value={values.category}>{values.category}</option>}
            {categories.map((category) => (
              <option value={category.slug} key={category.id}>
                {category.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Minimum price">
          <Input
            value={values.minPrice}
            onChange={(event) => change("minPrice", event.target.value)}
            inputMode="decimal"
            placeholder="0.00"
          />
        </Field>
        <Field label="Maximum price">
          <Input
            value={values.maxPrice}
            onChange={(event) => change("maxPrice", event.target.value)}
            inputMode="decimal"
            placeholder="Any"
          />
        </Field>
        <Field label="Sort">
          <Select
            value={values.sort}
            onChange={(event) => change("sort", event.target.value)}
          >
            {productSorts.map(([value, caption]) => (
              <option key={value} value={value}>
                {caption}
              </option>
            ))}
          </Select>
        </Field>
        <Button type="submit">Apply filters</Button>
      </form>
      {error && (
        <p className="field-error" role="alert">
          {error}
        </p>
      )}
    </>
  );
}
export function CatalogPage() {
  const params = useSearchParams();
  const router = useRouter();
  const filters = catalogFilters(new URLSearchParams(params.toString()));
  const invalid = validatePrices(filters.minPrice, filters.maxPrice);
  const products = useQuery({
    queryKey: ["catalog", catalogQuery(filters)],
    queryFn: () => api.products.list(catalogQuery(filters)),
    enabled: !invalid,
    retry: 1,
  });
  const categories = useQuery({
    queryKey: ["categories", "public"],
    queryFn: () => api.products.categories(),
    retry: 1,
  });
  return (
    <>
      <PageHeader
        title="The collection"
        eyebrow="Explore OrderFlow"
        description="Discover products from independent vendors. Make your everyday a little better."
      />
      <Filters
        key={params.toString()}
        initial={filters}
        categories={categories.data ?? []}
        onApply={(next) => router.push(catalogUrl(next))}
      />
      {categories.isError && (
        <ErrorState
          error={categories.error}
          retry={() => void categories.refetch()}
        />
      )}
      <div className="row justify-between" style={{ marginBottom: 24 }}>
        <p className="muted small" style={{ margin: 0 }}>
          {products.data ? `${products.data.totalElements} products` : ""}
          {products.isFetching && !products.isPending ? " · Refreshing…" : ""}
        </p>
        {(filters.q ||
          filters.category ||
          filters.minPrice ||
          filters.maxPrice) && (
          <Button variant="ghost" onClick={() => router.push("/products")}>
            Clear filters
          </Button>
        )}
      </div>
      {invalid ? (
        <Alert tone="error">{invalid}</Alert>
      ) : products.isPending ? (
        <LoadingState label="Loading products" />
      ) : products.isError ? (
        <ErrorState
          error={products.error}
          retry={() => void products.refetch()}
        />
      ) : products.data?.items.length ? (
        <>
          <div className="product-grid">
            {products.data.items.map((product) => (
              <ProductCard product={product} key={product.id} />
            ))}
          </div>
          <Pagination
            page={products.data.page}
            totalPages={products.data.totalPages}
            totalElements={products.data.totalElements}
            onPageChange={(page) =>
              router.push(catalogUrl({ ...filters, page }))
            }
          />
        </>
      ) : (
        <EmptyState
          title="No products found"
          description="Try a different search or expand your filters."
          action={
            <Button
              variant="secondary"
              onClick={() => router.push("/products")}
            >
              Reset filters
            </Button>
          }
        />
      )}
    </>
  );
}
