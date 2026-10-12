"use client";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useAuth } from "@/providers/auth-provider";
import {
  PageHeader,
  Card,
  Alert,
  Field,
  Input,
  Button,
  ErrorState,
  LoadingState,
} from "@/components/ui";
import { toast } from "sonner";
const schema = z
  .object({
    emailEnabled: z.boolean(),
    emailAddress: z.string().max(254),
    smsEnabled: z.boolean(),
    phoneNumber: z.string().max(32),
  })
  .superRefine((v, c) => {
    if (v.emailEnabled && !z.email().safeParse(v.emailAddress).success)
      c.addIssue({
        code: "custom",
        path: ["emailAddress"],
        message: "Enter a valid email destination.",
      });
    if (v.smsEnabled && !/^\+[1-9]\d{7,14}$/.test(v.phoneNumber))
      c.addIssue({
        code: "custom",
        path: ["phoneNumber"],
        message: "Use an international number, for example +14155550100.",
      });
  });
type Values = z.infer<typeof schema>;
export function Preferences() {
  const { user } = useAuth();
  const cache = useQueryClient();
  const query = useQuery({
    queryKey: ["preferences", user?.id],
    queryFn: api.notifications.preferences,
  });
  const [dirtyWarning, setDirtyWarning] = useState(false);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      emailEnabled: false,
      emailAddress: "",
      smsEnabled: false,
      phoneNumber: "",
    },
  });
  useEffect(() => {
    if (query.data && !isDirty)
      reset({
        ...query.data,
        emailAddress: query.data.emailAddress ?? "",
        phoneNumber: query.data.phoneNumber ?? "",
      });
  }, [query.data, isDirty, reset]);
  const save = useMutation({
    mutationFn: (body: Values) => api.notifications.savePreferences(body),
    onSuccess: (data) => {
      reset({
        ...data,
        emailAddress: data.emailAddress ?? "",
        phoneNumber: data.phoneNumber ?? "",
      });
      void cache.invalidateQueries({ queryKey: ["preferences", user?.id] });
      toast.success("Notification preferences saved.");
      setDirtyWarning(false);
    },
  });
  if (query.isLoading) return <LoadingState />;
  if (query.error)
    return (
      <ErrorState error={query.error} retry={() => void query.refetch()} />
    );
  return (
    <>
      <PageHeader
        title="Notification preferences"
        description="Choose where your updates appear."
      />
      <Alert tone="info">
        In-app notifications remain available in your account. Email and SMS
        delivery are simulated in this portfolio environment.
      </Alert>
      <Card className="max-w-2xl">
        {save.error && <ErrorState error={save.error} />}
        <form
          onSubmit={handleSubmit((v) => save.mutate(v))}
          onChange={() => setDirtyWarning(true)}
        >
          <label className="row mb-4">
            <input type="checkbox" {...register("emailEnabled")} /> Enable
            simulated email
          </label>
          <Field label="Email destination" error={errors.emailAddress?.message}>
            <Input type="email" {...register("emailAddress")} />
          </Field>
          <label className="row mb-4 mt-6">
            <input type="checkbox" {...register("smsEnabled")} /> Enable
            simulated SMS
          </label>
          <Field label="Phone destination" error={errors.phoneNumber?.message}>
            <Input
              type="tel"
              placeholder="+14155550100"
              {...register("phoneNumber")}
            />
          </Field>
          <Button type="submit" pending={save.isPending}>
            Save preferences
          </Button>
          {dirtyWarning && isDirty && (
            <span className="small muted ml-4">Unsaved changes</span>
          )}
        </form>
      </Card>
    </>
  );
}
