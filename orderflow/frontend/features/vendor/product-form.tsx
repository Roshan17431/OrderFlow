"use client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { useRef } from "react";
import { api } from "@/lib/api";
import { invalidateCatalog } from "@/lib/catalog-cache";
import { ApiError } from "@/lib/api/http";
import {
  Alert,
  Button,
  ErrorState,
  Field,
  Input,
  Select,
  Textarea,
} from "@/components/ui";
import type { Category, VendorProduct } from "@/types/contracts";

const common = {
  name: z.string().trim().min(1, "Enter a product name.").max(200),
  description: z.string().max(4000),
  priceAmount: z
    .string()
    .trim()
    .regex(
      /^\d{1,17}(?:\.\d{1,2})?$/,
      "Enter a non-negative price with at most two decimal places.",
    ),
  currency: z
    .string()
    .trim()
    .regex(/^[A-Za-z]{3}$/, "Enter a three-letter currency code."),
  categoryId: z.string().uuid("Choose a category."),
};
export const productFormSchema = z.object({
  ...common,
  sku: z
    .string()
    .trim()
    .min(1, "Enter a SKU.")
    .max(64)
    .regex(
      /^[A-Za-z0-9._-]+$/,
      "Use letters, digits, periods, underscores or hyphens.",
    ),
  slug: z
    .string()
    .max(120)
    .refine(
      (value) => value === "" || /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value),
      "Use lowercase words separated by hyphens.",
    ),
});
type Values = z.infer<typeof productFormSchema>;
export function ProductForm({
  product,
  categories,
  onSaved,
  onReload,
}: {
  product?: VendorProduct;
  categories: Category[];
  onSaved?: (product: VendorProduct) => void;
  onReload?: () => void;
}) {
  const client = useQueryClient();
  const version = useRef(product?.version ?? 0);
  const form = useForm<Values>({
    resolver: zodResolver(productFormSchema),
    defaultValues: {
      name: product?.name ?? "",
      description: product?.description ?? "",
      sku: product?.sku ?? "",
      slug: product?.slug ?? "",
      priceAmount: product ? String(product.priceAmount) : "",
      currency: product?.currency ?? "USD",
      categoryId: product?.category.id ?? "",
    },
  });
  const mutation = useMutation({
    retry: false,
    mutationFn: (values: Values) => {
      const body = {
        name: values.name,
        description: values.description,
        priceAmount: values.priceAmount,
        currency: values.currency.toUpperCase(),
        categoryId: values.categoryId,
      };
      return product
        ? api.vendor.update(product.id, { ...body, version: version.current })
        : api.vendor.create({
            ...body,
            sku: values.sku,
            slug: values.slug || undefined,
          });
    },
    onSuccess: async (result) => {
      version.current = result.version;
      await Promise.all([
        client.invalidateQueries({ queryKey: ["vendor"] }),
        invalidateCatalog(client),
      ]);
      toast.success(product ? "Product saved." : "Product created as a draft.");
      onSaved?.(result);
    },
    onError: (error) => {
      if (error instanceof ApiError)
        for (const item of error.fieldErrors) {
          if (item.field in form.getValues())
            form.setError(item.field as keyof Values, {
              message: item.message,
            });
        }
    },
  });
  const errors = form.formState.errors;
  const conflict =
    !!product &&
    mutation.error instanceof ApiError &&
    mutation.error.code === "PRODUCT_VERSION_CONFLICT";
  const disabled = mutation.isPending || product?.status === "ARCHIVED";
  return (
    <form
      noValidate
      onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
      className="stack"
    >
      {product?.status === "ARCHIVED" && (
        <Alert>Archived products cannot be edited.</Alert>
      )}
      {mutation.error && <ErrorState error={mutation.error} />}
      {conflict && (
        <Alert>
          Review the latest product before saving again. Your changes are still
          in this form.
          <div className="row">
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                mutation.reset();
                onReload?.();
              }}
            >
              Reload latest product
            </Button>
          </div>
        </Alert>
      )}
      {mutation.isSuccess && (
        <Alert tone="info">
          {product ? "Product saved." : "Product created as a draft."}
        </Alert>
      )}
      <fieldset
        disabled={disabled}
        style={{ border: 0, padding: 0, margin: 0 }}
      >
        <Field label="Product name" error={errors.name?.message}>
          <Input
            {...form.register("name")}
            maxLength={200}
            autoComplete="off"
          />
        </Field>
        <Field label="Description" error={errors.description?.message}>
          <Textarea {...form.register("description")} maxLength={4000} />
        </Field>
        <div className="form-grid">
          <Field label="SKU" error={errors.sku?.message}>
            <Input
              {...form.register("sku")}
              disabled={!!product}
              maxLength={64}
            />
          </Field>
          <Field label="Slug" error={errors.slug?.message}>
            <Input
              {...form.register("slug")}
              disabled={!!product}
              placeholder="Generated from name if left blank"
              maxLength={120}
            />
          </Field>
          <Field label="Category" error={errors.categoryId?.message}>
            <Select {...form.register("categoryId")}>
              <option value="">Choose a category</option>
              {product &&
                !categories.some((c) => c.id === product.category.id) && (
                  <option value={product.category.id}>
                    {product.category.name} (currently unavailable)
                  </option>
                )}
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Currency" error={errors.currency?.message}>
            <Input {...form.register("currency")} maxLength={3} />
          </Field>
          <Field label="Price" error={errors.priceAmount?.message}>
            <Input
              {...form.register("priceAmount")}
              inputMode="decimal"
              placeholder="0.00"
            />
          </Field>
        </div>
      </fieldset>
      <p className="small muted">
        {product
          ? `SKU and slug stay fixed. Current product version: ${mutation.data?.version ?? product.version}.`
          : "New products start as drafts. Activate the product after reviewing its details."}{" "}
        Manage stock separately in Inventory.
      </p>
      <div>
        <Button
          type="submit"
          pending={mutation.isPending}
          disabled={product?.status === "ARCHIVED"}
        >
          {product ? "Save product" : "Create product"}
        </Button>
      </div>
    </form>
  );
}
