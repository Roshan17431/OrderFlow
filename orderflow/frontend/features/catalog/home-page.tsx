"use client";
import Link from "next/link";
import { ArrowRight, Package, Search, ShoppingBag, Sprout } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useAuth } from "@/providers/auth-provider";
import { roleHome } from "@/lib/auth/redirects";
import {
  Button,
  EmptyState,
  ErrorState,
  Input,
  LoadingState,
  PageHeader,
} from "@/components/ui";
import { ProductCard } from "./product-card";

export function HomePage() {
  const { user } = useAuth();
  const customer = user?.roles.includes("CUSTOMER");
  const products = useQuery({
    queryKey: ["catalog", "recent"],
    queryFn: () =>
      api.products.list({ sort: "createdAt,desc", page: 0, size: 8 }),
    retry: 1,
  });
  const categories = useQuery({
    queryKey: ["categories", "public"],
    queryFn: () => api.products.categories(),
    retry: 1,
  });
  return (
    <>
      <section className="hero">
        <div>
          <p className="eyebrow">Discover your everyday</p>
          <h1>
            Good finds.
            <br />
            Simple living.
          </h1>
          <p className="muted">
            Explore the latest from our independent vendors. Find your next
            favorite, and follow every step from cart to doorstep.
          </p>
          <form
            action="/products"
            method="get"
            className="row"
            style={{ margin: "26px 0" }}
          >
            <label className="sr-only" htmlFor="home-search">
              Search products
            </label>
            <Input
              id="home-search"
              name="q"
              placeholder="What are you looking for?"
              style={{ flex: 1, minWidth: 180 }}
            />
            <Button type="submit" aria-label="Search products">
              <Search size={18} />
            </Button>
          </form>
          <div className="row">
            <Link href="/products" className="btn btn-primary">
              Explore the collection <ArrowRight size={16} />
            </Link>
            {!user && (
              <Link href="/register" className="btn btn-secondary">
                Create an account
              </Link>
            )}
          </div>
        </div>
        <div className="hero-visual" aria-hidden="true">
          <div className="visual-tile">
            <ShoppingBag size={110} strokeWidth={1} />
          </div>
          <div className="visual-tile">
            <Sprout size={90} strokeWidth={1} />
          </div>
        </div>
      </section>
      <section aria-label="Shop by category" className="section">
        <PageHeader
          level={2}
          title="Find your kind of good"
          description="Browse the active categories in our catalog."
        />
        {categories.isPending ? (
          <LoadingState label="Loading categories" />
        ) : categories.isError ? (
          <ErrorState
            error={categories.error}
            retry={() => void categories.refetch()}
          />
        ) : categories.data?.length ? (
          <div className="chips">
            <Link className="chip active" href="/products">
              All products
            </Link>
            {categories.data.map((category) => (
              <Link
                key={category.id}
                className="chip"
                href={"/products?category=" + encodeURIComponent(category.slug)}
              >
                {category.name}
              </Link>
            ))}
          </div>
        ) : (
          <p className="muted">
            Categories will appear here when vendors add their collections.
          </p>
        )}
      </section>
      <section aria-label="Recent products" className="section">
        <PageHeader
          level={2}
          title="Fresh to the collection"
          eyebrow="Recently added"
          description="The latest products from our catalog."
          actions={
            <Link href="/products" className="btn btn-secondary">
              View all products <ArrowRight size={16} />
            </Link>
          }
        />
        {products.isPending ? (
          <LoadingState label="Loading recent products" />
        ) : products.isError ? (
          <ErrorState
            error={products.error}
            retry={() => void products.refetch()}
          />
        ) : products.data?.items.length ? (
          <div className="product-grid">
            {products.data.items.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        ) : (
          <EmptyState
            title="New arrivals are on their way"
            description="Our catalog is empty right now. Check back as vendors publish their products."
          />
        )}
      </section>
      <section
        className="card row justify-between"
        style={{ background: "var(--sage)", padding: 32 }}
      >
        <div className="row">
          <Package size={38} strokeWidth={1} />
          <div>
            <h2 style={{ marginBottom: 4 }}>
              Your order, every step of the way.
            </h2>
            <p className="small muted" style={{ margin: 0 }}>
              Clear payment states and delivery milestones in one place.
            </p>
          </div>
        </div>
        <Link
          href={customer ? "/orders" : user ? roleHome(user.roles) : "/login"}
          className="btn btn-secondary"
        >
          {customer
            ? "View your orders"
            : user
              ? "Open your dashboard"
              : "Sign in to get started"}{" "}
          <ArrowRight size={16} />
        </Link>
      </section>
    </>
  );
}
