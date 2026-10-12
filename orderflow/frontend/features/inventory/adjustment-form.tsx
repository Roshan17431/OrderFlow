"use client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { useState } from "react";
import { api } from "@/lib/api";
import { ApiError } from "@/lib/api/http";
import { Alert, Button, ErrorState, Field, Input } from "@/components/ui";
import type { Stock } from "@/types/contracts";
export const adjustmentSchema = z.object({
  quantityDelta: z
    .string()
    .trim()
    .refine(
      (value) =>
        /^-?\d+$/.test(value) &&
        Number.isSafeInteger(Number(value)) &&
        Number(value) !== 0,
      "Enter a non-zero safe whole number.",
    ),
  reason: z.string().trim().min(1, "Enter an adjustment reason.").max(120),
  reference: z.string().trim().max(200),
});
type Values = z.infer<typeof adjustmentSchema>;
export function AdjustmentForm({
  stock,
  onSaved,
  onRefresh,
}: {
  stock: Stock;
  onSaved?: (stock: Stock) => void;
  onRefresh?: () => Promise<boolean>;
}) {
  const [refreshing, setRefreshing] = useState(false);
  const client = useQueryClient();
  const form = useForm<Values>({
    resolver: zodResolver(adjustmentSchema),
    defaultValues: { quantityDelta: "", reason: "", reference: "" },
  });
  const mutation = useMutation({
    retry: false,
    mutationFn: (values: Values) =>
      api.inventory.adjust(stock.productId, {
        quantityDelta: Number(values.quantityDelta),
        reason: values.reason,
        reference: values.reference || undefined,
      }),
    onSuccess: async (value) => {
      client.setQueryData(["inventory", "stock", stock.productId], value);
      await client.invalidateQueries({ queryKey: ["inventory"] });
      toast.success("Stock adjusted.");
      form.reset();
      onSaved?.(value);
    },
    onError: (error) => {
      if (error instanceof ApiError)
        for (const field of error.fieldErrors)
          if (field.field in form.getValues())
            form.setError(field.field as keyof Values, {
              message: field.message,
            });
    },
  });
  const delta = Number(
    useWatch({ control: form.control, name: "quantityDelta" }),
  );
  const belowReserved =
    Number.isSafeInteger(delta) &&
    delta !== 0 &&
    BigInt(stock.onHandQuantity) + BigInt(delta) <
      BigInt(stock.reservedQuantity);
  const uncertain =
    mutation.error instanceof ApiError &&
    ["REQUEST_TIMEOUT", "GATEWAY_UNAVAILABLE", "INVALID_RESPONSE"].includes(
      mutation.error.code,
    );
  const errors = form.formState.errors;
  return (
    <form
      noValidate
      onSubmit={form.handleSubmit((value) => mutation.mutate(value))}
    >
      {mutation.error && <ErrorState error={mutation.error} />}
      {uncertain && (
        <Alert>
          The result may be uncertain. Refresh stock and adjustment history
          before submitting another adjustment.
          <Button
            type="button"
            variant="secondary"
            pending={refreshing}
            disabled={!onRefresh}
            onClick={async () => {
              setRefreshing(true);
              try {
                if (await onRefresh?.()) mutation.reset();
              } finally {
                setRefreshing(false);
              }
            }}
          >
            Refresh stock and history
          </Button>
        </Alert>
      )}
      {mutation.isSuccess && (
        <Alert tone="info">
          Stock adjusted. Quantities are confirmed by the service.
        </Alert>
      )}
      <Field label="Quantity change" error={errors.quantityDelta?.message}>
        <Input
          {...form.register("quantityDelta")}
          inputMode="text"
          placeholder="For example, 10 or -3"
          disabled={mutation.isPending}
        />
      </Field>
      <Field label="Adjustment reason" error={errors.reason?.message}>
        <Input
          {...form.register("reason")}
          maxLength={120}
          disabled={mutation.isPending}
        />
      </Field>
      <Field label="Reference" error={errors.reference?.message}>
        <Input
          {...form.register("reference")}
          maxLength={200}
          placeholder="Optional receipt or count reference"
          disabled={mutation.isPending}
        />
      </Field>
      {belowReserved && (
        <Alert>
          This adjustment would reduce on-hand stock below the reserved
          quantity. The service will decide whether the adjustment can be
          accepted.
        </Alert>
      )}
      <p className="small muted">
        Positive changes add stock; negative changes remove it. Quantities
        update after server confirmation.
      </p>
      <Button type="submit" pending={mutation.isPending} disabled={uncertain}>
        Adjust stock
      </Button>
    </form>
  );
}
