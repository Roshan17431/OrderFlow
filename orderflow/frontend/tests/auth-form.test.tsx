import { it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/providers/auth-provider", () => ({
  useAuth: () => ({
    status: "anonymous",
    user: null,
    login: vi.fn(),
    register: vi.fn(),
  }),
}));
import { AuthForm } from "@/features/auth/auth-form";
it("requires actual registration fields and a twelve-character password", async () => {
  render(<AuthForm mode="register" />);
  await userEvent.type(screen.getByLabelText("Display name"), "Reader");
  await userEvent.type(
    screen.getByLabelText("Email address"),
    "reader@example.test",
  );
  await userEvent.type(screen.getByLabelText("Password"), "short");
  await userEvent.click(screen.getByRole("button", { name: "Create account" }));
  expect(await screen.findByText(/at least 12/)).toBeVisible();
});
it("does not show unconfigured OAuth buttons", () => {
  render(<AuthForm mode="login" />);
  expect(screen.queryByText("Continue with Google")).not.toBeInTheDocument();
  expect(screen.getByLabelText("Password")).toHaveAttribute("type", "password");
});
