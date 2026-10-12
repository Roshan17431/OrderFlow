import { expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  Button,
  Field,
  Input,
  Select,
  StatusBadge,
  ErrorState,
} from "@/components/ui";
import { ApiError } from "@/lib/api/http";
it("keeps select label text stable and associates validation with its control", () => {
  render(
    <Field label="Category" error="Choose a category">
      <Select>
        <option>Choose a category</option>
        <option>Home</option>
      </Select>
    </Field>,
  );
  const select = screen.getByRole("combobox") as HTMLSelectElement;
  expect(select.labels?.[0]?.textContent).toBe("Category");
  expect(select).toHaveAccessibleDescription("Choose a category");
});
it("disabled submit prevents action and announces pending", async () => {
  const click = vi.fn();
  render(
    <Button pending onClick={click}>
      Save changes
    </Button>,
  );
  await userEvent.click(screen.getByRole("button"));
  expect(click).not.toHaveBeenCalled();
  expect(screen.getByRole("button")).toBeDisabled();
});
it("labels inputs and displays field validation", () => {
  render(
    <Field label="Recipient" error="Required">
      <Input name="recipient" />
    </Field>,
  );
  expect(screen.getByLabelText("Recipient")).toBeVisible();
  expect(screen.getByText("Required")).toHaveAttribute("role", "alert");
});
it("separates labelled financial and fulfillment badges", () => {
  render(
    <>
      <StatusBadge status="REFUNDED" />
      <StatusBadge status="DELIVERY_FAILED" />
    </>,
  );
  expect(screen.getByText("Refunded")).toBeVisible();
  expect(screen.getByText("Delivery failed")).toBeVisible();
});
it("shows retry-after and correlation safely", () => {
  render(
    <ErrorState
      error={
        new ApiError(
          429,
          "RATE_LIMITED",
          "Too many requests",
          "trace-1",
          [],
          12,
        )
      }
    />,
  );
  expect(screen.getByText(/12 seconds/)).toBeVisible();
  expect(screen.getByText(/trace-1/)).toBeVisible();
});
