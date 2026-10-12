"use client";
import { useState, type ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "sonner";
import { AuthProvider } from "./auth-provider";
export function AppProvider({ children }: { children: ReactNode }) {
  const [query] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30000,
            retry: false,
            refetchOnWindowFocus: false,
          },
          mutations: { retry: false },
        },
      }),
  );
  return (
    <QueryClientProvider client={query}>
      <AuthProvider>
        {children}
        <Toaster richColors position="bottom-right" />
      </AuthProvider>
    </QueryClientProvider>
  );
}
