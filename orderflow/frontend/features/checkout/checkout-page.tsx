"use client";
import { useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { useAuth } from "@/providers/auth-provider";
import {
  Alert,
  Button,
  Card,
  EmptyState,
  ErrorState,
  Field,
  Input,
  LoadingState,
  PageHeader,
  Textarea,
} from "@/components/ui";
import type { CartValidation } from "@/types/contracts";
import { CartSummary } from "@/features/cart/cart-summary";
import { CartIssues } from "@/features/cart/cart-page";
import { CheckoutAttempt, shippingSchema, type ShippingFields } from "./model";

const addressFields = [
  ["recipientName", "Recipient name", 120, "name"],
  ["addressLine1", "Address line 1", 200, "address-line1"],
  ["addressLine2", "Address line 2", 200, "address-line2"],
  ["city", "City", 120, "address-level2"],
  ["stateOrRegion", "State or region", 120, "address-level1"],
  ["postalCode", "Postal code", 32, "postal-code"],
  ["countryCode", "Country code", 2, "country"],
] as const;
export function CheckoutPage() {
  const { user } = useAuth();
  const key = ["cart", user?.id];
  const cache = useQueryClient();
  const router = useRouter();
  const attempt = useRef(new CheckoutAttempt());
  const [uncertain, setUncertain] = useState(false);
  const [validation, setValidation] = useState<CartValidation | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const cart = useQuery({
    queryKey: key,
    queryFn: () => api.cart.get(),
    enabled: !!user,
    retry: 1,
  });
  const form = useForm<ShippingFields>({
    resolver: zodResolver(shippingSchema),
    defaultValues: {
      recipientName: "",
      addressLine1: "",
      addressLine2: "",
      city: "",
      stateOrRegion: "",
      postalCode: "",
      countryCode: "",
      deliveryInstructions: "",
    },
  });
  const submit = useMutation({
    mutationFn: async (values: ShippingFields | null) => {
      let current = attempt.current.get();
      if (values) {
        const checked = await api.cart.validate();
        setValidation(checked);
        cache.setQueryData(key, checked.cart);
        if (!checked.valid) {
          setLocalError(
            "Your cart needs attention. Resolve its validation issues before placing an order.",
          );
          return null;
        }
        if (!checked.cart.items.length) {
          setLocalError("Your cart is empty. Add an item before checkout.");
          return null;
        }
        current = attempt.current.prepare({
          expectedCartVersion: checked.cart.version,
          shippingAddress: values,
        });
      }
      if (!current) throw new Error("No checkout attempt is available.");
      return api.orders.create(current.body, current.key);
    },
    onSuccess: (result) => {
      if (!result) return;
      setUncertain(false);
      void Promise.all([
        cache.invalidateQueries({ queryKey: key }),
        cache.invalidateQueries({ queryKey: ["orders", user?.id] }),
      ]);
      cache.setQueryData(["order", user?.id, result.id], result);
      router.push("/orders/" + result.id);
    },
    onError: (error) => {
      if (
        attempt.current.get() &&
        (!(error instanceof ApiError) ||
          error.status >= 500 ||
          error.code === "ORDER_IDEMPOTENCY_CONFLICT")
      )
        setUncertain(true);
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          const name = fieldError.field.replace(/^shippingAddress\./, "");
          if (name in form.getValues())
            form.setError(name as keyof ShippingFields, {
              type: "server",
              message: fieldError.message,
            });
        }
        if (
          error.code === "CART_VERSION_CONFLICT" ||
          error.code === "ORDER_CART_VERSION_CONFLICT"
        ) {
          attempt.current.reset();
          setLocalError(
            "Your cart changed. Review the refreshed cart and submit again.",
          );
          void cart.refetch();
        }
      }
    },
  });
  const canSubmit =
    !!cart.data?.items.length && !submit.isPending && !uncertain;
  function restart() {
    attempt.current.reset();
    setUncertain(false);
    submit.reset();
    setLocalError(null);
    setValidation(null);
    void cart.refetch();
  }
  return (
    <>
      <PageHeader
        title="Checkout"
        eyebrow="The final details"
        description="A delivery address, a checked cart, and you’re ready."
        actions={
          <Link href="/cart" className="btn btn-secondary">
            Back to cart
          </Link>
        }
      />
      {uncertain && (
        <Alert>
          The last request may have created your order. Retry the same order to
          safely recover its result, or check{" "}
          <Link href="/orders">your orders</Link> before starting a new
          checkout.
        </Alert>
      )}
      {cart.isPending ? (
        <LoadingState label="Loading checkout" />
      ) : cart.isError && !uncertain ? (
        <ErrorState error={cart.error} retry={() => void cart.refetch()} />
      ) : !cart.data?.items.length && !uncertain ? (
        <EmptyState
          title="Your cart is empty"
          description="Add an item before placing an order."
          action={
            <Link href="/products" className="btn btn-primary">
              Browse products
            </Link>
          }
        />
      ) : (
        <div className="split">
          <Card title="Shipping address">
            <form
              onSubmit={form.handleSubmit((values) => {
                setLocalError(null);
                submit.mutate(values);
              })}
              noValidate
            >
              <fieldset
                disabled={submit.isPending || uncertain}
                style={{ border: 0, padding: 0, margin: 0 }}
              >
                <div className="form-grid">
                  {addressFields.map(([name, caption, max, autocomplete]) => (
                    <Field
                      key={name}
                      label={caption}
                      htmlFor={"shipping-" + name}
                      error={form.formState.errors[name]?.message}
                    >
                      <Input
                        id={"shipping-" + name}
                        {...form.register(name)}
                        autoComplete={autocomplete}
                        maxLength={max}
                        aria-invalid={!!form.formState.errors[name]}
                        placeholder={
                          name === "countryCode" ? "IN, US, GB…" : undefined
                        }
                      />
                    </Field>
                  ))}
                </div>
                <Field
                  label="Delivery instructions"
                  htmlFor="shipping-instructions"
                  error={form.formState.errors.deliveryInstructions?.message}
                >
                  <Textarea
                    id="shipping-instructions"
                    {...form.register("deliveryInstructions")}
                    maxLength={300}
                    placeholder="Optional instructions for your delivery"
                  />
                </Field>
              </fieldset>
              <p className="small muted">
                Country code uses two letters. Your address is used only for
                this order.
              </p>
              {!uncertain && (
                <Button
                  type="submit"
                  pending={submit.isPending}
                  disabled={!canSubmit}
                >
                  Place order
                </Button>
              )}
            </form>
            {uncertain && (
              <div className="row">
                <Button
                  pending={submit.isPending}
                  onClick={() => submit.mutate(null)}
                >
                  Retry same order
                </Button>
                <Button
                  variant="secondary"
                  disabled={submit.isPending}
                  onClick={restart}
                >
                  Start new checkout
                </Button>
              </div>
            )}
            {submit.error && <ErrorState error={submit.error} />}
            <div aria-live="polite">
              {localError && <Alert tone="error">{localError}</Alert>}
              {validation && <CartIssues validation={validation} />}
            </div>
          </Card>
          {cart.data && (
            <CartSummary cart={cart.data}>
              <Alert tone="info">
                After your order is accepted, inventory reservation and payment
                preparation happen in the background.
              </Alert>
            </CartSummary>
          )}
        </div>
      )}
    </>
  );
}
