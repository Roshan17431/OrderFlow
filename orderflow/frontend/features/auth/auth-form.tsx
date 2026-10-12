"use client";
import Link from "next/link";
import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Eye, EyeOff, ArrowRight } from "lucide-react";
import { useAuth } from "@/providers/auth-provider";
import { API_BASE } from "@/lib/api/http";
import { safeReturnUrl, roleHome } from "@/lib/auth/redirects";
import { Card, Field, Input, Button, ErrorState } from "@/components/ui";
const schema = z.object({
  email: z.email("Enter a valid email address").max(320),
  password: z.string().min(1, "Enter your password").max(128),
  displayName: z.string().max(100).optional(),
});
type Values = z.infer<typeof schema>;
export function AuthForm({ mode }: { mode: "login" | "register" }) {
  const auth = useAuth();
  const router = useRouter();
  const search = useSearchParams();
  const [show, setShow] = useState(false);
  const [error, setError] = useState<unknown>();
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError: fieldError,
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "", displayName: "" },
  });
  async function submit(values: Values) {
    setError(undefined);
    if (mode === "register") {
      if (values.password.length < 12) {
        fieldError("password", { message: "Use at least 12 characters." });
        return;
      }
      if (!values.displayName?.trim()) {
        fieldError("displayName", { message: "Enter your display name." });
        return;
      }
    }
    try {
      const response =
        mode === "register"
          ? await auth.register({
              ...values,
              displayName: values.displayName!.trim(),
            })
          : await auth.login({
              email: values.email,
              password: values.password,
            });
      router.replace(
        safeReturnUrl(search.get("returnTo"), roleHome(response.user.roles)),
      );
    } catch (e) {
      setError(e);
    }
  }
  const providers = (process.env.NEXT_PUBLIC_OAUTH_PROVIDERS ?? "")
    .split(",")
    .filter((x) => x === "google" || x === "github");
  return (
    <Card className="auth-card">
      <p className="eyebrow">Welcome to OrderFlow</p>
      <h1>
        {mode === "register"
          ? "Make yourself at home."
          : "Good to see you again."}
      </h1>
      <p className="muted">
        {mode === "register"
          ? "Create your account and discover something good."
          : "Sign in to continue your shopping journey."}
      </p>
      {error != null && <ErrorState error={error} />}
      <form onSubmit={handleSubmit(submit)} noValidate>
        {mode === "register" && (
          <Field label="Display name" error={errors.displayName?.message}>
            <Input autoComplete="name" {...register("displayName")} />
          </Field>
        )}
        <Field label="Email address" error={errors.email?.message}>
          <Input type="email" autoComplete="email" {...register("email")} />
        </Field>
        <Field label="Password" error={errors.password?.message}>
          <Input
            type={show ? "text" : "password"}
            autoComplete={
              mode === "register" ? "new-password" : "current-password"
            }
            {...register("password")}
          />
        </Field>
        <Button
          type="button"
          variant="ghost"
          aria-label={show ? "Hide password" : "Show password"}
          onClick={() => setShow(!show)}
        >
          {show ? <EyeOff size={16} /> : <Eye size={16} />}{" "}
          {show ? "Hide" : "Show"} password
        </Button>
        {mode === "register" && (
          <p className="small muted">
            Use 12–128 characters for your password.
          </p>
        )}
        <Button type="submit" pending={isSubmitting} className="w-full">
          {mode === "register" ? "Create account" : "Sign in"}
          <ArrowRight size={16} />
        </Button>
      </form>
      {mode === "login" && providers.length > 0 && (
        <div className="stack section">
          {providers.map((p) => (
            <a
              key={p}
              className="btn btn-secondary"
              href={`${API_BASE}/oauth2/authorization/${p}`}
            >
              Continue with {p === "google" ? "Google" : "GitHub"}
            </a>
          ))}
        </div>
      )}
      <p className="small muted mt-6">
        {mode === "register" ? "Already have an account?" : "New to OrderFlow?"}{" "}
        <Link href={mode === "register" ? "/login" : "/register"}>
          {mode === "register" ? "Sign in" : "Create an account"}
        </Link>
      </p>
    </Card>
  );
}
