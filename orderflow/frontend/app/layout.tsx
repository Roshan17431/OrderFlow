import type { Metadata } from "next";
import "./globals.css";
import { AppProvider } from "@/providers/app-provider";
import { Shell } from "@/components/layout/shell";
export const metadata: Metadata = {
  title: {
    default: "OrderFlow — Everyday essentials",
    template: "%s · OrderFlow",
  },
  description: "A connected shopping experience, from discovery to delivery.",
};
export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <a href="#main" className="skip-link">
          Skip to content
        </a>
        <AppProvider>
          <Shell>{children}</Shell>
        </AppProvider>
      </body>
    </html>
  );
}
