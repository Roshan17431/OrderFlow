"use client";
import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ShoppingBag } from "lucide-react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { money } from "@/lib/formatting";
import { useAuth } from "@/providers/auth-provider";
import {
  Alert,
  Button,
  EmptyState,
  ErrorState,
  Field,
  Input,
  LoadingState,
} from "@/components/ui";
import { ProductArt } from "./product-card";

export function ProductDetailPage({ productId }: { productId: string }) {
  const { user, status } = useAuth();
  const cache = useQueryClient();
  const [quantity, setQuantity] = useState("1");
  const [added, setAdded] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [checkedQuantity, setCheckedQuantity] = useState<number | null>(null);
  const product = useQuery({
    queryKey: ["product", productId],
    queryFn: () => api.products.get(productId),
    retry: 1,
  });
  const add = useMutation({
    retry: false,
    mutationFn: () => api.cart.add(productId, Number(quantity)),
    onSuccess: (cart) => {
      cache.setQueryData(["cart", user?.id], cart);
      void cache.invalidateQueries({
        queryKey: ["cart", user?.id],
        refetchType: "none",
      });
      setAdded(true);
      setUncertain(false);
      setCheckedQuantity(null);
      toast.success("Added to your cart");
    },
    onError: (error) => {
      setAdded(false);
      if (!(error instanceof ApiError) || error.status >= 500)
        setUncertain(true);
    },
  });
  const checkCart = useMutation({
    retry: false,
    mutationFn: api.cart.get,
    onSuccess: (cart) => {
      cache.setQueryData(["cart", user?.id], cart);
      setCheckedQuantity(
        cart.items.find((item) => item.productId === productId)?.quantity ?? 0,
      );
      setUncertain(false);
      add.reset();
    },
  });
  const valid =
    Number.isInteger(Number(quantity)) &&
    Number(quantity) >= 1 &&
    Number(quantity) <= 99;
  if (product.isPending) return <LoadingState label="Loading product" />;
  if (product.isError)
    return (
      <>
        <Link href="/products" className="btn btn-secondary">
          Back to products
        </Link>
        <ErrorState
          error={product.error}
          retry={() => void product.refetch()}
        />
      </>
    );
  if (!product.data) return <EmptyState title="Product not found" />;
  const data = product.data;
  const customer = user?.roles.includes("CUSTOMER");
  return (
    <>
      <Link className="small muted" href="/products">
        ← Back to products
      </Link>
      <div className="split" style={{ marginTop: 30, alignItems: "start" }}>
        <div>
          <ProductArt large />
          <p className="small muted" style={{ marginTop: 12 }}>
            Product imagery is not available for this catalog.
          </p>
        </div>
        <div>
          <Link
            className="eyebrow"
            href={
              "/products?category=" + encodeURIComponent(data.category.slug)
            }
          >
            {data.category.name}
          </Link>
          <h1
            style={{
              fontFamily: "Georgia, serif",
              fontWeight: 400,
              fontSize: 40,
              marginTop: 12,
            }}
          >
            {data.name}
          </h1>
          <p style={{ fontSize: 24 }}>
            {money(data.priceAmount, data.currency)}{" "}
            <span className="small muted">{data.currency}</span>
          </p>
          <p style={{ whiteSpace: "pre-line" }}>
            {data.description ||
              "No description has been provided for this product."}
          </p>
          <Alert tone="info">
            Available in the public catalog. Availability is checked when you
            add it and when your order reserves inventory.
          </Alert>
          {customer ? (
            <form
              onSubmit={(event) => {
                event.preventDefault();
                if (valid && !add.isPending && !uncertain) add.mutate();
              }}
            >
              <div className="row">
                <Field label="Quantity">
                  <Input
                    type="number"
                    min={1}
                    max={99}
                    step={1}
                    value={quantity}
                    onChange={(event) => {
                      setQuantity(event.target.value);
                      setAdded(false);
                    }}
                    style={{ width: 100 }}
                    disabled={add.isPending || uncertain}
                  />
                </Field>
                <Button
                  type="submit"
                  pending={add.isPending}
                  disabled={!valid || uncertain}
                  style={{ marginTop: 12 }}
                >
                  <ShoppingBag size={16} />
                  Add to cart
                </Button>
              </div>
              {!valid && (
                <p className="field-error" role="alert">
                  Enter a whole quantity from 1 to 99.
                </p>
              )}
            </form>
          ) : status === "loading" ? (
            <p role="status" className="muted">
              Checking your session…
            </p>
          ) : user ? (
            <Alert>A customer account is required to shop.</Alert>
          ) : (
            <Link
              href={
                "/login?returnTo=" + encodeURIComponent("/products/" + data.id)
              }
              className="btn btn-primary"
            >
              Sign in to add to cart
            </Link>
          )}
          {added && (
            <Alert tone="info">
              Added to your cart <Link href="/cart">View cart →</Link>
            </Alert>
          )}
          {add.error && <ErrorState error={add.error} />}
          {uncertain && (
            <Alert>
              The result is uncertain. Check the recorded cart quantity before
              adding more. <Link href="/cart">View cart</Link>
              <div>
                <Button
                  type="button"
                  variant="secondary"
                  pending={checkCart.isPending}
                  onClick={() => checkCart.mutate()}
                >
                  Check current cart
                </Button>
              </div>
            </Alert>
          )}
          {checkCart.error && <ErrorState error={checkCart.error} />}
          {checkedQuantity != null && (
            <Alert tone="info">Current cart quantity: {checkedQuantity}</Alert>
          )}
        </div>
      </div>
    </>
  );
}
