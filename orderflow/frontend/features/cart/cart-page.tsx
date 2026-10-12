"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Trash2 } from "lucide-react";
import { api } from "@/lib/api";
import { money } from "@/lib/formatting";
import { useAuth } from "@/providers/auth-provider";
import {
  Alert,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Input,
  LoadingState,
  PageHeader,
} from "@/components/ui";
import type { CartItem, CartValidation } from "@/types/contracts";
import { CartSummary, lineAmount } from "./cart-summary";

function CartLine({
  item,
  pending,
  onUpdate,
  onRemove,
}: {
  item: CartItem;
  pending: boolean;
  onUpdate: (quantity: number) => void;
  onRemove: () => void;
}) {
  const [quantity, setQuantity] = useState(String(item.quantity));
  const valid =
    Number.isInteger(Number(quantity)) &&
    Number(quantity) >= 1 &&
    Number(quantity) <= 99;
  return (
    <Card>
      <div className="row justify-between">
        <div>
          <Link href={"/products/" + item.productId}>
            <h2>{item.productNameSnapshot}</h2>
          </Link>
          <p className="muted small">
            {money(item.unitPriceSnapshot, item.currency)} {item.currency} each
            · Saved cart price
          </p>
        </div>
        <strong>
          {money(
            lineAmount(item.unitPriceSnapshot, item.quantity),
            item.currency,
          )}
        </strong>
      </div>
      {item.priceChanged && (
        <Alert>
          The price has changed since this item was added. Review the displayed
          price before validating your cart again.
        </Alert>
      )}
      {!item.purchasable && (
        <Alert tone="error">
          This product is currently unavailable. Remove it before checkout.
        </Alert>
      )}
      <div className="row justify-between">
        <form
          className="quantity-control"
          onSubmit={(event) => {
            event.preventDefault();
            if (valid) onUpdate(Number(quantity));
          }}
        >
          <label htmlFor={"quantity-" + item.productId} className="small">
            Quantity
          </label>
          <Input
            id={"quantity-" + item.productId}
            aria-label={"Quantity for " + item.productNameSnapshot}
            type="number"
            min={1}
            max={99}
            step={1}
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            disabled={pending}
          />
          <Button
            variant="secondary"
            type="submit"
            disabled={!valid || Number(quantity) === item.quantity || pending}
            aria-label={"Update " + item.productNameSnapshot}
          >
            Update
          </Button>
        </form>
        <Button
          variant="ghost"
          aria-label={"Remove " + item.productNameSnapshot}
          disabled={pending}
          onClick={onRemove}
        >
          <Trash2 size={16} />
          Remove
        </Button>
      </div>
      {!valid && (
        <p className="field-error" role="alert">
          Enter a whole quantity from 1 to 99.
        </p>
      )}
    </Card>
  );
}
export function CartIssues({ validation }: { validation: CartValidation }) {
  return (
    <div aria-live="polite">
      {validation.issues.map((issue, index) => (
        <Alert
          tone={validation.valid ? "warning" : "error"}
          key={issue.productId + issue.code + index}
        >
          <strong>
            {validation.cart.items.find(
              (item) => item.productId === issue.productId,
            )?.productNameSnapshot ?? "Cart item"}
            :
          </strong>{" "}
          {issue.message}
        </Alert>
      ))}
    </div>
  );
}
export function CartPage() {
  const { user } = useAuth();
  const key = ["cart", user?.id];
  const cache = useQueryClient();
  const router = useRouter();
  const [clearOpen, setClearOpen] = useState(false);
  const [validation, setValidation] = useState<CartValidation | null>(null);
  const cart = useQuery({
    queryKey: key,
    queryFn: () => api.cart.get(),
    enabled: !!user,
    retry: 1,
  });
  const change = useMutation({
    mutationFn: (action: { productId: string; quantity?: number }) =>
      action.quantity === undefined
        ? api.cart.remove(action.productId)
        : api.cart.update(action.productId, action.quantity),
    onSuccess: (result) => {
      cache.setQueryData(key, result);
      void cache.invalidateQueries({ queryKey: key, refetchType: "none" });
      setValidation(null);
      toast.success("Cart updated");
    },
    onError: () => void cache.invalidateQueries({ queryKey: key }),
  });
  const clear = useMutation({
    mutationFn: () => api.cart.clear(),
    onSuccess: async () => {
      setClearOpen(false);
      setValidation(null);
      await cache.invalidateQueries({ queryKey: key });
      toast.success("Cart cleared");
    },
  });
  const validate = useMutation({
    mutationFn: () => api.cart.validate(),
    onSuccess: (result) => {
      cache.setQueryData(key, result.cart);
      setValidation(result);
      if (result.valid) router.push("/checkout");
    },
  });
  const pending = change.isPending || clear.isPending || validate.isPending;
  return (
    <>
      <PageHeader
        title="Your cart"
        eyebrow="Ready when you are"
        description="Review your items and validate current prices before checkout."
        actions={
          <Link className="btn btn-secondary" href="/products">
            Continue shopping
          </Link>
        }
      />
      {cart.isPending ? (
        <LoadingState label="Loading cart" />
      ) : cart.isError ? (
        <ErrorState error={cart.error} retry={() => void cart.refetch()} />
      ) : cart.data?.items.length ? (
        <>
          <div className="split">
            <div className="stack">
              {cart.data.items.map((item) => (
                <CartLine
                  key={item.productId + item.quantity + item.updatedAt}
                  item={item}
                  pending={pending}
                  onUpdate={(quantity) =>
                    change.mutate({ productId: item.productId, quantity })
                  }
                  onRemove={() => change.mutate({ productId: item.productId })}
                />
              ))}
              <div>
                <Button
                  variant="ghost"
                  disabled={pending}
                  onClick={() => setClearOpen(true)}
                >
                  Clear cart
                </Button>
              </div>
              {validation && <CartIssues validation={validation} />}
              <ErrorStateIf
                error={change.error ?? clear.error ?? validate.error}
              />
            </div>
            <CartSummary cart={cart.data}>
              <Button
                pending={validate.isPending}
                disabled={pending}
                onClick={() => validate.mutate()}
              >
                Validate and checkout
              </Button>
              <p className="small muted">
                Availability is checked again when inventory is reserved for
                your order.
              </p>
            </CartSummary>
          </div>
        </>
      ) : (
        <EmptyState
          title="Your cart is waiting"
          description="Find something you like and add it to your cart."
          action={
            <Link href="/products" className="btn btn-primary">
              Explore products
            </Link>
          }
        />
      )}
      <ConfirmDialog
        open={clearOpen}
        onClose={() => setClearOpen(false)}
        onConfirm={() => clear.mutate()}
        pending={clear.isPending}
        title="Clear your cart?"
        description="All items will be removed from your cart."
        confirmLabel="Clear cart"
      />
    </>
  );
}
function ErrorStateIf({ error }: { error: unknown }) {
  return error ? <ErrorState error={error} /> : null;
}
