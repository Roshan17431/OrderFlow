"use client";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { invalidateCatalog } from "@/lib/catalog-cache";
import { ApiError } from "@/lib/api/http";
import { date } from "@/lib/formatting";
import {
  Alert,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  Field,
  Input,
  LoadingState,
  PageHeader,
  Pagination,
  StatusBadge,
  Table,
  Textarea,
} from "@/components/ui";
import type { Category } from "@/types/contracts";
import { useAdmin } from "./common";
const schema = z.object({
  name: z.string().trim().min(1, "Enter a category name").max(120),
  slug: z
    .string()
    .trim()
    .min(1, "Enter a slug")
    .max(120)
    .regex(
      /^[a-z0-9]+(?:-[a-z0-9]+)*$/,
      "Use lowercase words separated by hyphens",
    ),
  description: z.string().max(1000),
});
type Values = z.infer<typeof schema>;
export function CategoryEditor({
  category,
  onSaved,
  onCancel,
  onReload,
}: {
  category?: Category;
  onSaved?: (category: Category) => void;
  onCancel?: () => void;
  onReload?: () => void;
}) {
  const { userId } = useAdmin();
  const cache = useQueryClient();
  const [version, setVersion] = useState(category?.version ?? 0);
  const [saved, setSaved] = useState(false);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: category?.name ?? "",
      slug: category?.slug ?? "",
      description: category?.description ?? "",
    },
  });
  const mutation = useMutation({
    retry: false,
    mutationFn: (body: Values) =>
      category
        ? api.admin.updateCategory(category.id, { ...body, version })
        : api.admin.createCategory(body),
    onSuccess: (value) => {
      setVersion(value.version);
      setSaved(true);
      void invalidateCatalog(cache, true);
      toast.success(category ? "Category saved" : "Category created");
      void cache.invalidateQueries({
        queryKey: ["admin", userId, "categories"],
      });
      onSaved?.(value);
    },
    onError: (error) => {
      if (error instanceof ApiError)
        for (const field of error.fieldErrors) {
          if (["name", "slug", "description"].includes(field.field))
            form.setError(field.field as keyof Values, {
              message: field.message,
            });
        }
      if (error instanceof ApiError && error.status === 409)
        void cache.invalidateQueries({
          queryKey: ["admin", userId, "categories"],
        });
    },
  });
  return (
    <Card title={category ? "Edit category" : "Create category"}>
      <form
        onSubmit={form.handleSubmit((value) => {
          setSaved(false);
          mutation.mutate(value);
        })}
        noValidate
      >
        <div className="form-grid">
          <Field
            label="Category name"
            error={form.formState.errors.name?.message}
          >
            <Input
              {...form.register("name")}
              maxLength={120}
              disabled={mutation.isPending}
            />
          </Field>
          <Field
            label="Category slug"
            error={form.formState.errors.slug?.message}
          >
            <Input
              {...form.register("slug")}
              maxLength={120}
              disabled={mutation.isPending}
            />
          </Field>
        </div>
        <Field
          label="Description"
          error={form.formState.errors.description?.message}
        >
          <Textarea
            {...form.register("description")}
            maxLength={1000}
            disabled={mutation.isPending}
          />
        </Field>
        {category && (
          <p className="small muted">
            Version {version}. Changes are checked against the current backend
            version.
          </p>
        )}
        {mutation.isError && <ErrorState error={mutation.error} />}{" "}
        {mutation.error instanceof ApiError &&
          mutation.error.status === 409 && (
            <Alert>
              Reload the category to review current data before trying again.
              {category && onReload && (
                <Button type="button" variant="secondary" onClick={onReload}>
                  Reload current category
                </Button>
              )}
            </Alert>
          )}
        {saved && <Alert tone="info">Category saved.</Alert>}
        <div className="row">
          <Button type="submit" pending={mutation.isPending}>
            {category ? "Save category" : "Create category"}
          </Button>
          {onCancel && (
            <Button
              type="button"
              variant="secondary"
              disabled={mutation.isPending}
              onClick={onCancel}
            >
              Close editor
            </Button>
          )}
        </div>
      </form>
    </Card>
  );
}
export function CategoriesPage() {
  const { userId, enabled } = useAdmin();
  const cache = useQueryClient();
  const [page, setPage] = useState(0);
  const [editor, setEditor] = useState<Category | "new" | null>(null);
  const [change, setChange] = useState<Category | null>(null);
  const query = useQuery({
    queryKey: ["admin", userId, "categories", page],
    queryFn: () => api.admin.categories({ page, size: 20 }),
    enabled,
  });
  const mutation = useMutation({
    retry: false,
    mutationFn: (category: Category) =>
      api.admin.categoryStatus(
        category.id,
        category.status === "ACTIVE" ? "deactivate" : "activate",
        category.version,
      ),
    onSuccess: () => {
      setChange(null);
      void invalidateCatalog(cache, true);
      toast.success("Category status updated");
      void cache.invalidateQueries({
        queryKey: ["admin", userId, "categories"],
      });
    },
    onError: () =>
      void cache.invalidateQueries({
        queryKey: ["admin", userId, "categories"],
      }),
  });
  return (
    <div className="stack">
      <PageHeader
        title="Categories"
        description="Organize the collection and manage category availability."
        actions={
          <Button onClick={() => setEditor("new")}>Create category</Button>
        }
      />
      {editor && (
        <CategoryEditor
          key={
            typeof editor === "string"
              ? "new"
              : `${editor.id}-${editor.version}`
          }
          category={typeof editor === "string" ? undefined : editor}
          onCancel={() => setEditor(null)}
          onSaved={() => setEditor(null)}
          onReload={() => {
            void query.refetch().then((result) => {
              if (typeof editor !== "string") {
                const current = result.data?.items.find(
                  (item) => item.id === editor.id,
                );
                setEditor(current ?? null);
                if (!current)
                  toast.info(
                    "Review the current category list before editing again",
                  );
              }
            });
          }}
        />
      )}{" "}
      {query.isPending ? (
        <LoadingState />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => void query.refetch()} />
      ) : query.data.items.length === 0 ? (
        <EmptyState
          title="No categories"
          description="Create a category to organize products."
        />
      ) : (
        <>
          <Table
            headers={[
              "Category",
              "Slug",
              "Status",
              "Version",
              "Updated",
              "Actions",
            ]}
          >
            {query.data.items.map((category) => (
              <tr key={category.id}>
                <td>
                  {category.name}
                  <p className="muted small">{category.description}</p>
                </td>
                <td>{category.slug}</td>
                <td>
                  <StatusBadge status={category.status} />
                </td>
                <td>{category.version}</td>
                <td>{date(category.updatedAt)}</td>
                <td>
                  <div className="row">
                    <Button
                      variant="secondary"
                      onClick={() => setEditor(category)}
                    >
                      Edit
                    </Button>
                    <Button
                      variant="ghost"
                      disabled={mutation.isPending}
                      onClick={() => {
                        mutation.reset();
                        setChange(category);
                      }}
                    >
                      {category.status === "ACTIVE" ? "Deactivate" : "Activate"}
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </Table>
          <Pagination
            page={page}
            totalPages={query.data.totalPages}
            totalElements={query.data.totalElements}
            onPageChange={setPage}
          />
        </>
      )}
      {mutation.isError && <ErrorState error={mutation.error} />}
      <ConfirmDialog
        open={!!change}
        onClose={() => setChange(null)}
        onConfirm={() => {
          if (change) mutation.mutate(change);
        }}
        pending={mutation.isPending}
        title={`${change?.status === "ACTIVE" ? "Deactivate" : "Activate"} category`}
        description={
          change?.status === "ACTIVE"
            ? `Deactivate ${change.name}? The backend checks whether this category is in use.`
            : `Make ${change?.name ?? "this category"} available?`
        }
        confirmLabel={change?.status === "ACTIVE" ? "Deactivate" : "Activate"}
      />
    </div>
  );
}
